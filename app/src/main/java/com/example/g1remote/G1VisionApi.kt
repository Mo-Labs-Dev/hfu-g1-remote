package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * G1 Vision service on the robot PC (port 8003):
 *  describe  - camera picture -> local vision AI -> G1 says what it sees
 *  find      - G1 turns on the spot, YOLO looks for the object, faces it, waves
 */
object G1VisionApi {
    private val base: String get() = G1Host.url(8003)

    data class Status(val state: String, val task: String, val target: String, val answer: String, val message: String)

    private fun call(method: String, path: String, body: JSONObject?, timeoutMs: Int): Pair<Int, JSONObject> {
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 3000
        conn.readTimeout = timeoutMs
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            code to (runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        } finally {
            conn.disconnect()
        }
    }

    /** Returns G1's spoken answer, or throws with a readable message. */
    suspend fun describe(question: String, german: Boolean): String = withContext(Dispatchers.IO) {
        val (code, json) = call("POST", "/api/vision/describe",
            JSONObject().put("question", question).put("lang", if (german) "de" else "en"), 150_000)
        if (code >= 400) error(json.optString("detail", "Vision not available"))
        json.optString("answer")
    }

    /** COCO label for the object the visitor named, or null. */
    suspend fun match(text: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val (_, json) = call("POST", "/api/vision/match", JSONObject().put("text", text), 4000)
            json.optString("label").takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
    }

    /** Starts the search. Returns null if started, otherwise the reason. */
    suspend fun find(label: String, german: Boolean): String? = withContext(Dispatchers.IO) {
        runCatching {
            val (code, json) = call("POST", "/api/vision/find",
                JSONObject().put("object", label).put("lang", if (german) "de" else "en"), 5000)
            if (code >= 400) json.optString("detail", "HTTP $code") else null
        }.getOrElse { "Vision service not reachable" }
    }

    suspend fun status(): Status? = withContext(Dispatchers.IO) {
        runCatching {
            val (_, j) = call("GET", "/api/vision/status", null, 3000)
            Status(j.optString("state"), j.optString("task"), j.optString("target"),
                j.optString("answer"), j.optString("message"))
        }.getOrNull()
    }
}

private fun clean(text: String) =
    text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

/** "What do you see?", "What is this?", "Was siehst du?", "Was ist das?" */
fun isDescribeRequest(text: String): Boolean {
    val t = clean(text).removePrefix("g1 ").removePrefix("hey g1 ").trim()
    if (t in setOf("what is this", "what s this", "whats this", "was ist das", "was ist das hier", "what is that"))
        return true
    return listOf(
        "what do you see", "what can you see", "what are you seeing", "what are you looking at",
        "describe what", "look at this", "tell me what you see",
        "was siehst du", "was kannst du sehen", "was siehst du gerade", "beschreib", "schau dir das an",
        "was ist das für"
    ).any { t.contains(it) }
}

/** "Find the bottle", "Where is a chair?", "Finde die Tasse", "Wo ist ein Stuhl?" */
fun isFindRequest(text: String): Boolean {
    val t = " " + clean(text) + " "
    return listOf(
        " find ", " look for ", " search for ", " search the ", " where is ", " where s ", " can you see a ",
        " finde ", " finden ", " such ", " suche ", " wo ist ", " siehst du ein"
    ).any { t.contains(it) }
}
