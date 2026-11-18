package com.example.g1remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Client for the robot-side guide service (hfu_nav/teach_repeat/guide_service.py).
 * The robot server checks all safety rules; the app only asks.
 */
object G1GuideConfig {
    val BASE_URL: String get() = G1Host.url(8002)
}

data class GuideRoute(
    val name: String,
    val titleEn: String,
    val titleDe: String,
    val lengthM: Double,
    /** "route" = one destination, "tour" = lab tour with several stops */
    val kind: String = "route",
    val stopCount: Int = 0
) {
    val isTour: Boolean get() = kind == "tour"

    fun title(german: Boolean): String =
        (if (german) titleDe else titleEn).ifBlank { name.replace('_', ' ') }
}

data class GuideStatus(
    val state: String,
    val route: String,
    val progress: Double,
    val message: String,
    val leg: String,
    val kind: String = "route",
    val stopIndex: Int = 0,
    val stopCount: Int = 0,
    val stopTitle: String = ""
) {
    val isTour: Boolean get() = kind == "tour"
}

object G1GuideApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun routes(): List<GuideRoute> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${G1GuideConfig.BASE_URL}/api/guide/routes").get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val arr = JSONArray(resp.body?.string().orEmpty())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GuideRoute(
                    name = o.optString("name"),
                    titleEn = o.optString("title_en"),
                    titleDe = o.optString("title_de"),
                    lengthM = o.optDouble("length_m", 0.0),
                    kind = o.optString("kind", "route"),
                    stopCount = o.optJSONArray("stops")?.length() ?: 0
                )
            }
        }
    }

    suspend fun status(): GuideStatus = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${G1GuideConfig.BASE_URL}/api/guide/status").get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            parseStatus(JSONObject(resp.body?.string().orEmpty()))
        }
    }

    /** Returns null when the guide started, otherwise the reason (from the robot). */
    suspend fun start(route: String, german: Boolean, returnHome: Boolean = true,
                      waitSeconds: Int = 20): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("route", route)
                .put("lang", if (german) "de" else "en")
                .put("return_home", returnHome)
                .put("wait_s", waitSeconds)
                .toString().toRequestBody(jsonType)
            val req = Request.Builder().url("${G1GuideConfig.BASE_URL}/api/guide/start")
                .post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) null
                else runCatching { JSONObject(text).optString("detail") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"
            }
        } catch (e: Exception) {
            "Guide service not reachable (${e.message})"
        }
    }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${G1GuideConfig.BASE_URL}/api/guide/cancel")
                .post("{}".toRequestBody(jsonType)).build()
            http.newCall(req).execute().close()
        }
    }

    private fun parseStatus(o: JSONObject) = GuideStatus(
        state = o.optString("state", "idle"),
        route = o.optString("route"),
        progress = o.optDouble("progress", 0.0),
        message = o.optString("message"),
        leg = o.optString("leg"),
        kind = o.optString("kind", "route"),
        stopIndex = o.optInt("stop_index", 0),
        stopCount = o.optInt("stop_count", 0),
        stopTitle = o.optString("stop_title")
    )
}

/**
 * "Guide me to the coffee machine" / "Bring mich zur Kaffeemaschine".
 * Returns the matching taught route, or null if the sentence is not a
 * guiding request for a known destination.
 */
fun detectGuideRequest(text: String, routes: List<GuideRoute>): GuideRoute? {
    if (routes.isEmpty()) return null
    val t = text.lowercase(Locale.ROOT)
    // "Give me a lab tour" / "Kannst du mir das Labor zeigen?" -> the lab tour
    val tour = routes.firstOrNull { it.isTour }
    if (tour != null && isTourRequest(t)) return tour
    val wantsGuide = listOf(
        "guide", "take me", "bring me", "show me the way", "show me where", "lead me",
        "walk me", "way to", "directions to", "where is", "where's", "how do i get to",
        "führ", "fuehr", "bring mich", "zeig mir den weg", "zeig mir wo", "begleit",
        "wo ist", "wo finde ich", "weg zur", "weg zum", "wie komme ich"
    ).any { t.contains(it) }
    if (!wantsGuide) return null

    fun keys(r: GuideRoute): List<String> {
        val titles = listOf(r.titleEn, r.titleDe, r.name.replace('_', ' '))
            .map { it.lowercase(Locale.ROOT).trim() }.filter { it.length >= 3 }
        val firstWords = titles.mapNotNull { it.split(" ").firstOrNull()?.takeIf { w -> w.length >= 5 } }
        return (titles + titles.map { it.replace(" ", "") } + titles.map { it.replace("-", " ") } + firstWords)
            .distinct()
    }
    return routes.firstOrNull { r -> keys(r).any { k -> t.contains(k) } }
}

/** Visitor wants the whole lab tour (not one destination). */
fun isTourRequest(text: String): Boolean {
    val t = text.lowercase(Locale.ROOT)
    return listOf(
        "tour", "show me around", "show me the lab", "show me your lab", "guide me through",
        "walk me through", "explain the lab", "what is in this lab", "what's in this lab",
        "rundgang", "laborführung", "laborfuehrung", "führung durch", "fuehrung durch",
        "zeig mir das labor", "zeigst du mir das labor", "labor zeigen", "herumführen",
        "herum führen", "durch das labor", "durchs labor"
    ).any { t.contains(it) }
}
