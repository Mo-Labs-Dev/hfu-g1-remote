
package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object G1SafetyApi {

    private const val BASE_URL =
        "http://10.150.68.160:8000/api/visitor-safety"

    data class SafetySlide(
        val id: String,
        val number: Int,
        val title: String,
        val subtitle: String,
        val points: List<String>
    )

    data class Briefing(
        val version: String,
        val title: String,
        val approvalStatus: String,
        val slides: List<SafetySlide>
    )

    data class VisitorSession(
        val sessionId: String,
        val safetyAcknowledged: Boolean,
        val nextStep: String
    )

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {

        val connection = (
                URL("$BASE_URL$path").openConnection()
                        as HttpURLConnection
                )

        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode

            val response = (
                    if (status in 200..299)
                        connection.inputStream
                    else
                        connection.errorStream
                    )?.bufferedReader()?.use {
                    it.readText()
                } ?: ""

            if (status !in 200..299) {
                throw IllegalStateException(
                    "Safety API HTTP $status: $response"
                )
            }

            JSONObject(response)

        } finally {
            connection.disconnect()
        }
    }

    suspend fun getBriefing(): Briefing {
        val json = request("/briefing")
        val slidesJson = json.getJSONArray("slides")

        val slides = mutableListOf<SafetySlide>()

        for (index in 0 until slidesJson.length()) {
            val slide = slidesJson.getJSONObject(index)
            val pointsJson = slide.getJSONArray("points")

            val points = mutableListOf<String>()

            for (pointIndex in 0 until pointsJson.length()) {
                points.add(pointsJson.getString(pointIndex))
            }

            slides.add(
                SafetySlide(
                    id = slide.getString("id"),
                    number = slide.getInt("number"),
                    title = slide.getString("title"),
                    subtitle = slide.optString("subtitle"),
                    points = points
                )
            )
        }

        return Briefing(
            version = json.getString("version"),
            title = json.getString("title"),
            approvalStatus = json.getString("approval_status"),
            slides = slides
        )
    }

    suspend fun createSession(): VisitorSession {
        val json = request(
            path = "/sessions",
            method = "POST"
        )

        return VisitorSession(
            sessionId = json.getString("session_id"),
            safetyAcknowledged =
                json.optBoolean("safety_acknowledged", false),
            nextStep = json.optString("next_step")
        )
    }

    suspend fun acknowledge(
        sessionId: String,
        presentedSlideIds: List<String>
    ): VisitorSession {

        val body = JSONObject().apply {
            put("understood", true)

            put(
                "presented_slide_ids",
                JSONArray(presentedSlideIds)
            )
        }

        val json = request(
            path = "/sessions/$sessionId/acknowledge",
            method = "POST",
            body = body
        )

        return VisitorSession(
            sessionId = json.getString("session_id"),
            safetyAcknowledged =
                json.optBoolean("safety_acknowledged", false),
            nextStep = json.optString("next_step")
        )
    }

    suspend fun getSession(
        sessionId: String
    ): VisitorSession {

        val json = request("/sessions/$sessionId")

        return VisitorSession(
            sessionId = json.getString("session_id"),
            safetyAcknowledged =
                json.optBoolean("safety_acknowledged", false),
            nextStep = json.optString("next_step")
        )
    }
}
