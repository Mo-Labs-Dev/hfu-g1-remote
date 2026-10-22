
package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object G1Backend {

    private const val BASE_URL = "http://10.150.68.160:8000"

    data class ConnectionStatus(
        val connected: Boolean,
        val model: String = "",
        val cameraConnected: Boolean = false,
        val error: String = ""
    )

    suspend fun checkConnection(): ConnectionStatus =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                connection = (
                        URL("$BASE_URL/api/vision/status")
                            .openConnection() as HttpURLConnection
                        ).apply {
                        requestMethod = "GET"
                        connectTimeout = 3000
                        readTimeout = 3000
                        setRequestProperty(
                            "Accept",
                            "application/json"
                        )
                    }

                if (connection.responseCode != 200) {
                    return@withContext ConnectionStatus(
                        connected = false,
                        error = "HTTP ${connection.responseCode}"
                    )
                }

                val body = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(body)

                ConnectionStatus(
                    connected = json.optString("status") == "ready",
                    model = json.optString("model"),
                    cameraConnected =
                        json.optBoolean("camera_connected", false)
                )

            } catch (e: Exception) {
                ConnectionStatus(
                    connected = false,
                    error = e.message ?: "Connection failed"
                )
            } finally {
                connection?.disconnect()
            }
        }
}
