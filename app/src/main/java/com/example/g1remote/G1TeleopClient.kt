package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the robot-side teleop server (robot/teleop/teleop_api.py).
 *
 * The robot server owns all safety: speed limits, dead-man timeout,
 * FSM check and E-stop. This client only sends normalized stick values
 * (-1..1) about every 100 ms.
 */
object G1TeleopConfig {
    // Standalone teleop server (scripts/run_teleop_server.py).
    // Change to port 8000 once the router is mounted in the main backend.
    const val BASE_URL = "http://10.150.68.160:8001"
}

class TeleopException(val code: Int, message: String) : Exception(message)

data class TeleopStatus(
    val state: String,
    val teleopActive: Boolean,
    val youHaveControl: Boolean,
    val operator: String?,
    val estop: Boolean,
    val estopReason: String,
    val fsmId: Int?,
    val fsmMode: Int?,
    val locomotionReady: Boolean,
    val vx: Double,
    val vy: Double,
    val yaw: Double,
    val lastError: String,
    val gesture: String?,
    val gestureResult: String,
    val demoEnabled: Boolean,
    val maxForward: Double,
    val maxYaw: Double
) {
    companion object {
        fun fromJson(json: JSONObject): TeleopStatus {
            val output = json.optJSONObject("output") ?: JSONObject()
            val limits = json.optJSONObject("limits") ?: JSONObject()
            return TeleopStatus(
                state = json.optString("state", "unknown"),
                teleopActive = json.optBoolean("teleop_active", false),
                youHaveControl = json.optBoolean("you_have_control", false),
                operator = json.optString("operator")
                    .takeIf { it.isNotBlank() && it != "null" },
                estop = json.optBoolean("estop", false),
                estopReason = json.optString("estop_reason", ""),
                fsmId = if (json.isNull("fsm_id")) null
                else json.optInt("fsm_id"),
                fsmMode = if (!json.has("fsm_mode") || json.isNull("fsm_mode")) null
                else json.optInt("fsm_mode"),
                locomotionReady = json.optBoolean("locomotion_ready", false),
                vx = output.optDouble("vx", 0.0),
                vy = output.optDouble("vy", 0.0),
                yaw = output.optDouble("yaw", 0.0),
                lastError = json.optString("last_error", ""),
                gesture = if (!json.has("gesture") || json.isNull("gesture")) null
                else json.optString("gesture"),
                gestureResult = json.optString("gesture_result", ""),
                demoEnabled = json.optBoolean("demo_enabled", false),
                maxForward = limits.optDouble("max_vx_forward", 0.0),
                maxYaw = limits.optDouble("max_yaw", 0.0)
            )
        }
    }
}

enum class TeleopConnection { DISCONNECTED, CONNECTING, CONNECTED }

class G1TeleopClient(
    private val baseUrl: String = G1TeleopConfig.BASE_URL
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .pingInterval(2, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val _status = MutableStateFlow<TeleopStatus?>(null)
    val status: StateFlow<TeleopStatus?> = _status

    private val _connection = MutableStateFlow(TeleopConnection.DISCONNECTED)
    val connection: StateFlow<TeleopConnection> = _connection

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token

    @Volatile
    private var socket: WebSocket? = null

    // ---------------------------------------------------------------
    // REST
    // ---------------------------------------------------------------

    private suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .post(body.toString().toRequestBody(jsonType))
                .build()

            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(text).optString("detail")
                    }.getOrNull()
                    throw TeleopException(
                        response.code,
                        detail?.takeIf { it.isNotBlank() }
                            ?: "HTTP ${response.code}"
                    )
                }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }

    suspend fun refreshStatus() {
        try {
            val json = withContext(Dispatchers.IO) {
                val builder = Request.Builder().url("$baseUrl/api/teleop/status")
                _token.value?.let { builder.header("X-Teleop-Token", it) }
                http.newCall(builder.get().build()).execute().use {
                    if (!it.isSuccessful) throw TeleopException(it.code, "HTTP ${it.code}")
                    JSONObject(it.body?.string().orEmpty())
                }
            }
            _status.value = TeleopStatus.fromJson(json)
        } catch (e: Exception) {
            _message.value = "Teleop server unreachable: ${e.message}"
        }
    }

    /** Returns null on success, or an error message for the operator. */
    suspend fun unlock(pin: String, operator: String): String? =
        try {
            val json = post(
                "/api/teleop/unlock",
                JSONObject().put("pin", pin).put("operator", operator)
            )
            _token.value = json.getString("token")
            json.optJSONObject("status")?.let {
                _status.value = TeleopStatus.fromJson(it)
            }
            _message.value = null
            connect()
            null
        } catch (e: TeleopException) {
            e.message
        } catch (e: Exception) {
            "Cannot reach robot: ${e.message}"
        }

    suspend fun lock() {
        val current = _token.value
        sendStop()
        disconnect()
        _token.value = null
        if (current != null) {
            runCatching { post("/api/teleop/lock", JSONObject().put("token", current)) }
        }
    }

    /**
     * Change robot mode: "damp" (FSM 1), "stand_up" (FSM 4), "walk" (FSM 500).
     * The server checks the allowed order. Returns null on success or an error.
     */
    suspend fun setMode(mode: String): String? {
        val current = _token.value ?: return "Unlock first."
        return try {
            val json = post(
                "/api/teleop/mode",
                JSONObject().put("token", current).put("mode", mode)
            )
            _status.value = TeleopStatus.fromJson(json)
            null
        } catch (e: TeleopException) {
            e.message
        } catch (e: Exception) {
            "Cannot reach robot: ${e.message}"
        }
    }

    /** Start an arm gesture (e.g. "high_wave"). Returns null or an error. */
    suspend fun performGesture(gesture: String): String? {
        val current = _token.value ?: return "Unlock first."
        return try {
            val json = post(
                "/api/teleop/gesture",
                JSONObject().put("token", current).put("gesture", gesture)
            )
            _status.value = TeleopStatus.fromJson(json)
            null
        } catch (e: TeleopException) {
            e.message
        } catch (e: Exception) {
            "Cannot reach robot: ${e.message}"
        }
    }

    /** Operator switches the visitor Robot Show on/off. */
    suspend fun setDemoMode(enabled: Boolean): String? {
        val current = _token.value ?: return "Unlock first."
        return try {
            val json = post(
                "/api/teleop/demo/mode",
                JSONObject().put("token", current).put("enabled", enabled)
            )
            _status.value = TeleopStatus.fromJson(json)
            null
        } catch (e: Exception) {
            e.message
        }
    }

    /** Normal stop. Works even without a token. */
    suspend fun stop() {
        sendStop()
        runCatching { post("/api/teleop/stop") }
            .onFailure { _message.value = "STOP request failed: ${it.message}" }
    }

    /** Latched software E-stop. Works even without a token. */
    suspend fun estop() {
        sendStop()
        runCatching { post("/api/teleop/estop") }
            .onSuccess { _status.value = TeleopStatus.fromJson(it) }
            .onFailure { _message.value = "E-STOP request failed: ${it.message}" }
    }

    suspend fun resetEstop(): String? {
        val current = _token.value ?: return "Unlock first."
        return try {
            val json = post("/api/teleop/estop/reset", JSONObject().put("token", current))
            _status.value = TeleopStatus.fromJson(json)
            null
        } catch (e: Exception) {
            e.message
        }
    }

    // ---------------------------------------------------------------
    // WebSocket
    // ---------------------------------------------------------------

    fun connect() {
        val current = _token.value ?: return
        if (_connection.value != TeleopConnection.DISCONNECTED) return

        _connection.value = TeleopConnection.CONNECTING
        val wsUrl = baseUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") +
                "/api/teleop/ws?token=$current"

        socket = http.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connection.value = TeleopConnection.CONNECTED
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (json.optString("type")) {
                        "status" -> _status.value = TeleopStatus.fromJson(json)
                        "error" -> _message.value = json.optString("message")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    onClosedInternal(code)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onClosedInternal(code)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _message.value = "Connection lost: ${t.message}"
                    onClosedInternal(0)
                }
            }
        )
    }

    private fun onClosedInternal(code: Int) {
        socket = null
        _connection.value = TeleopConnection.DISCONNECTED
        if (code == 4401) {
            // Server says our session is no longer valid.
            _token.value = null
            _message.value = "Session expired. Please unlock again."
        }
    }

    fun disconnect() {
        socket?.close(1000, "operator left")
        socket = null
        _connection.value = TeleopConnection.DISCONNECTED
    }

    /** Normalized: x forward+, y left+, yaw turn-left+. Each in -1..1. */
    fun sendCommand(x: Float, y: Float, yaw: Float, scale: Float): Boolean {
        val ws = socket ?: return false
        if (_connection.value != TeleopConnection.CONNECTED) return false
        val msg = JSONObject()
            .put("type", "cmd")
            .put("x", x.coerceIn(-1f, 1f).toDouble())
            .put("y", y.coerceIn(-1f, 1f).toDouble())
            .put("yaw", yaw.coerceIn(-1f, 1f).toDouble())
            .put("scale", scale.coerceIn(0.1f, 1f).toDouble())
        return ws.send(msg.toString())
    }

    private fun sendStop() {
        socket?.send("""{"type":"stop"}""")
    }

    fun clearMessage() {
        _message.value = null
    }
}
