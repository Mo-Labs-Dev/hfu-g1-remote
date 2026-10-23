
package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Visitor-facing connection to the G1 Studio Welcome Manager.
 *
 * This client can:
 * - Read welcome state and active session
 * - Read welcome events
 * - Request visitor workflow transitions
 *
 * It cannot:
 * - Enable robot movement
 * - Control robot posture
 * - Operate the robot joystick
 * - Enable Welcome Mode
 */
object G1VisitorWelcomeApi {

    private const val BASE_URL =
        "http://10.150.68.160:8000/api/visitor-welcome"

    data class WelcomeStatus(
        val enabled: Boolean,
        val state: String,
        val sessionId: String?,
        val eventId: Int,
        val safetyAcknowledged: Boolean,
        val welcomeText: String,
        val updatedAt: String
    )

    data class WelcomeEvent(
        val id: Int,
        val type: String,
        val state: String,
        val sessionId: String?,
        val timestamp: String
    )

    private suspend fun request(
        path: String,
        method: String = "GET",
        payload: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {

        val connection = (
                URL("$BASE_URL$path").openConnection()
                        as HttpURLConnection
                )

        try {
            connection.requestMethod = method
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.outputStream.use {
                    it.write(
                        payload.toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                }
            }

            val statusCode = connection.responseCode

            val response = (
                    if (statusCode in 200..299)
                        connection.inputStream
                    else
                        connection.errorStream
                    )?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException(
                    "Welcome API HTTP $statusCode: $response"
                )
            }

            JSONObject(response)

        } finally {
            connection.disconnect()
        }
    }

    private fun parseStatus(
        json: JSONObject
    ): WelcomeStatus {

        val rawSessionId =
            json.optString("session_id", "")

        return WelcomeStatus(
            enabled = json.optBoolean("enabled", false),
            state = json.optString("state", "idle"),
            sessionId = rawSessionId
                .takeIf { it.isNotBlank() && it != "null" },
            eventId = json.optInt("event_id", 0),
            safetyAcknowledged =
                json.optBoolean("safety_acknowledged", false),
            welcomeText = json.optString("welcome_text"),
            updatedAt = json.optString("updated_at")
        )
    }

    suspend fun getStatus(): WelcomeStatus {
        return parseStatus(
            request("/status")
        )
    }

    suspend fun getEvents(
        afterId: Int = 0
    ): List<WelcomeEvent> {

        val json = request(
            "/events?after_id=$afterId"
        )

        val events = json.getJSONArray("events")

        return (0 until events.length()).map { index ->
            val item = events.getJSONObject(index)

            WelcomeEvent(
                id = item.getInt("id"),
                type = item.getString("type"),
                state = item.optString("state"),
                sessionId = item.optString("session_id")
                    .takeIf {
                        it.isNotBlank() && it != "null"
                    },
                timestamp = item.optString("timestamp")
            )
        }
    }

    suspend fun advance(
        sessionId: String,
        action: String
    ): WelcomeStatus {

        // Restrict this visitor client to
        // non-robot workflow transitions.
        require(
            action in setOf(
                "welcome_completed",
                "briefing_completed",
                "acknowledge_safety"
            )
        ) {
            "Unsupported visitor action."
        }

        val payload = JSONObject().apply {
            put("session_id", sessionId)
            put("action", action)
        }

        return parseStatus(
            request(
                path = "/advance",
                method = "POST",
                payload = payload
            )
        )
    }
}
