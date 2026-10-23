
package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object G1Backend {

    private const val BASE_URL =
        "http://10.150.68.160:8000"

    data class ConnectionStatus(
        val connected: Boolean,
        val model: String = "",
        val cameraConnected: Boolean = false,
        val error: String = ""
    )

    data class GuidanceStep(
        val id: String,
        val name: String,
        val action: String,
        val status: String
    )

    data class GuidanceStatus(
        val sessionId: String?,
        val destinationName: String?,
        val state: String,
        val progress: Int,
        val message: String,
        val active: Boolean,
        val mode: String,
        val physicalMotionRequested: Boolean,
        val steps: List<GuidanceStep>
    )

    private fun getJson(path: String): JSONObject {
        val connection = (
                URL("$BASE_URL$path")
                    .openConnection() as HttpURLConnection
                )

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val code = connection.responseCode

            if (code !in 200..299) {
                val errorBody = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                throw IllegalStateException(
                    "HTTP $code: ${errorBody.take(200)}"
                )
            }

            val body = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            return JSONObject(body)

        } finally {
            connection.disconnect()
        }
    }

    suspend fun checkConnection(): ConnectionStatus =
        withContext(Dispatchers.IO) {
            try {
                val json = getJson("/api/vision/status")

                ConnectionStatus(
                    connected =
                        json.optString("status") == "ready",
                    model = json.optString("model"),
                    cameraConnected =
                        json.optBoolean(
                            "camera_connected",
                            false
                        )
                )

            } catch (e: Exception) {
                ConnectionStatus(
                    connected = false,
                    error = e.message ?: "Connection failed"
                )
            }
        }

    suspend fun getGuidanceStatus(): GuidanceStatus =
        withContext(Dispatchers.IO) {
            val json = getJson(
                "/api/visitor-guidance/status"
            )

            val array = json.optJSONArray("steps")
                ?: JSONArray()

            val steps = (0 until array.length()).map {
                    index ->
                val item = array.getJSONObject(index)

                GuidanceStep(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    action = item.optString("action"),
                    status = item.optString("status")
                )
            }

            GuidanceStatus(
                sessionId = json.optString(
                    "session_id"
                ).takeIf {
                    it.isNotBlank() && it != "null"
                },
                destinationName = json.optString(
                    "destination_name"
                ).takeIf {
                    it.isNotBlank() && it != "null"
                },
                state = json.optString(
                    "state",
                    "idle"
                ),
                progress = json.optInt(
                    "progress",
                    0
                ).coerceIn(0, 100),
                message = json.optString(
                    "message",
                    "Waiting for guidance."
                ),
                active = json.optBoolean(
                    "active",
                    false
                ),
                mode = json.optString(
                    "mode",
                    "simulation"
                ),
                physicalMotionRequested =
                    json.optBoolean(
                        "physical_motion_requested",
                        false
                    ),
                steps = steps
            )
        }
}
