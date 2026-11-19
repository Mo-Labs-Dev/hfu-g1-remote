package com.example.g1remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Live link to G1 Studio (backend :8000).
 *  - GET /api/app/config  -> announcement banner + which home tiles are shown
 *  - WebSocket /ws        -> "app_config_changed" / "routes_changed" pushed by Studio
 * Reconnects by itself and follows a changed robot address (G1Host).
 */
object G1StudioSync {

    data class Announcement(
        val active: Boolean = false,
        val level: String = "info",
        val textEn: String = "",
        val textDe: String = ""
    )

    data class AppConfig(
        val version: Int = 0,
        val announcement: Announcement = Announcement(),
        val tiles: Map<String, Boolean> = emptyMap()
    ) {
        fun tileVisible(key: String): Boolean = tiles[key] ?: true
    }

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config

    /** Increases whenever Studio changed a route / tour -> reload routes. */
    private val _routesVersion = MutableStateFlow(0)
    val routesVersion: StateFlow<Int> = _routesVersion

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .build()

    @Volatile private var started = false
    @Volatile private var socket: WebSocket? = null
    @Volatile private var socketHost: String = ""

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                val host = G1Host.ip
                if (socket == null || host != socketHost) {
                    socket?.cancel()
                    socket = null
                    connect(host)
                    refreshConfig()
                }
                delay(3000L)
            }
        }
    }

    private fun connect(host: String) {
        socketHost = host
        val request = Request.Builder().url("ws://$host:8000/ws").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                scope.launch { refreshConfig() }
                _routesVersion.value = _routesVersion.value + 1     // catch up after a reconnect
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = runCatching { JSONObject(text).optString("type") }.getOrDefault("")
                when (type) {
                    "app_config_changed" -> scope.launch { refreshConfig() }
                    "routes_changed" -> _routesVersion.value = _routesVersion.value + 1
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(webSocket)
        })
    }

    private fun dropped(webSocket: WebSocket) {
        if (socket === webSocket) {
            socket = null
            _connected.value = false
        }
    }

    suspend fun refreshConfig() {
        runCatching {
            val conn = URL("${G1Host.url(8000)}/api/app/config").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            try {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val a = json.optJSONObject("announcement") ?: JSONObject()
                val t = json.optJSONObject("tiles") ?: JSONObject()
                val tiles = mutableMapOf<String, Boolean>()
                t.keys().forEach { k -> tiles[k] = t.optBoolean(k, true) }
                _config.value = AppConfig(
                    version = json.optInt("version", 0),
                    announcement = Announcement(
                        active = a.optBoolean("active", false),
                        level = a.optString("level", "info"),
                        textEn = a.optString("text_en", ""),
                        textDe = a.optString("text_de", "")
                    ),
                    tiles = tiles
                )
            } finally {
                conn.disconnect()
            }
        }
    }
}
