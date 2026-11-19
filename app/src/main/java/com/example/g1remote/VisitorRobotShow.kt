package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Visitor "Robot Show": visitors trigger safe arm gestures without a PIN.
 * Works only after the operator switched "Visitor demo" ON in the joystick
 * screen. The robot server enforces all rules (safe gestures only,
 * robot standing in walking mode, cooldown).
 */
data class DemoStatus(
    val demoEnabled: Boolean,
    val available: Boolean,
    val reason: String,
    val gesture: String?
)

object G1DemoApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl: String get() = G1TeleopConfig.BASE_URL

    private fun parse(json: JSONObject) = DemoStatus(
        demoEnabled = json.optBoolean("demo_enabled", false),
        available = json.optBoolean("available", false),
        reason = json.optString("reason", "off"),
        gesture = if (json.isNull("gesture")) null else json.optString("gesture")
    )

    suspend fun status(): DemoStatus = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/api/teleop/demo").get().build()
        http.newCall(req).execute().use {
            if (!it.isSuccessful) throw Exception("HTTP ${it.code}")
            parse(JSONObject(it.body?.string().orEmpty()))
        }
    }

    /** Touch-screen gesture. Returns null on success, or the server's message. */
    suspend fun gesture(key: String): String? = postGesture("/api/teleop/demo/gesture", key)

    /** Voice gesture (also allows handshake and high five). */
    suspend fun voiceGesture(key: String): String? = postGesture("/api/teleop/voice/gesture", key)

    private suspend fun postGesture(path: String, key: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("gesture", key).toString().toRequestBody(jsonType)
            val req = Request.Builder().url(baseUrl + path).post(body).build()
            http.newCall(req).execute().use {
                val text = it.body?.string().orEmpty()
                if (it.isSuccessful) null
                else runCatching { JSONObject(text).optString("detail") }.getOrNull()
                    ?.takeIf { d -> d.isNotBlank() } ?: "HTTP ${it.code}"
            }
        } catch (e: Exception) {
            e.message ?: "Network error"
        }
    }
}

private data class ShowGesture(
    val key: String,
    val emoji: String,
    val en: String,
    val de: String
)

/** Keys must match VISITOR_GESTURES in robot/teleop/teleop_controller.py. */
private val ShowGestures = listOf(
    ShowGesture("high_wave", "👋", "Wave hello", "Winken"),
    ShowGesture("face_wave", "🙋", "Friendly wave", "Freundlich winken"),
    ShowGesture("clap", "👏", "Clap", "Klatschen"),
    ShowGesture("heart", "❤️", "Heart", "Herz"),
    ShowGesture("two_hand_kiss", "😘", "Flying kiss", "Luftkuss")
)

private val ShowGreen = Color(0xFF087B39)
private val ShowMint = Color(0xFFE7F5EC)
private val ShowDark = Color(0xFF172820)
private val ShowMuted = Color(0xFF64756B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotShowScreen(
    isGerman: Boolean,
    onBack: () -> Unit
) {
    fun t(en: String, de: String) = if (isGerman) de else en

    var status by remember { mutableStateOf<DemoStatus?>(null) }
    var offline by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                status = G1DemoApi.status()
                offline = false
            } catch (_: Exception) {
                offline = true
            }
            delay(1000L)
        }
    }

    val s = status
    val (banner, bannerColor) = when {
        offline || s == null -> t(
            "The robot is not connected.",
            "Der Roboter ist nicht verbunden."
        ) to Color(0xFF9D4B00)
        !s.demoEnabled -> t(
            "The robot show will start soon. Please ask our team!",
            "Die Roboter-Show beginnt gleich. Fragen Sie gerne unser Team!"
        ) to ShowMuted
        s.reason == "busy" -> t("G1 is performing for you…", "G1 zeigt es Ihnen gerade…") to ShowGreen
        s.reason == "resting" -> t("One moment…", "Einen Moment…") to ShowMuted
        s.reason == "moving" -> t("G1 is walking right now.", "G1 läuft gerade.") to ShowMuted
        s.reason == "stopped" || s.reason == "not_ready" -> t(
            "G1 is taking a break.",
            "G1 macht gerade eine Pause."
        ) to ShowMuted
        else -> t("Tap a button and G1 will show you!", "Tippen Sie – G1 zeigt es Ihnen!") to ShowGreen
    }
    val canPress = s?.available == true && !offline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9F8))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(t("Back", "Zurück")) }
            Spacer(Modifier.width(14.dp))
            Text(
                t("Robot Show", "Roboter-Show"),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = ShowDark
            )
        }

        Surface(color = ShowMint, shape = RoundedCornerShape(14.dp)) {
            Text(
                banner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = bannerColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        ShowGestures.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { g ->
                    val running = s?.gesture == g.key
                    Card(
                        onClick = {
                            scope.launch {
                                message = G1DemoApi.gesture(g.key)
                                runCatching { status = G1DemoApi.status() }
                            }
                        },
                        enabled = canPress,
                        modifier = Modifier
                            .weight(1f)
                            .height(150.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (running) ShowGreen else Color.White,
                            disabledContainerColor = if (running) ShowGreen else Color(0xFFF0F2F1)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(g.emoji, fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                t(g.en, g.de),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    running -> Color.White
                                    canPress -> ShowDark
                                    else -> ShowMuted
                                }
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        message?.let {
            Text(it, color = ShowMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }

        Text(
            t(
                "Please stay behind the marked line while G1 moves its arms.",
                "Bitte bleiben Sie hinter der Markierung, während G1 die Arme bewegt."
            ),
            color = ShowMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * Voice commands for gestures (English + German).
 * Returns a gesture key, or null if the sentence is not a gesture command
 * (then it goes to the G1 AI as before).
 * Only short sentences count, so a long question that happens to contain
 * "heart" is not treated as a command.
 */
private val VoiceGesturePatterns: List<Pair<String, Regex>> = listOf(
    "shake_hand" to Regex(
        "shake (my |your |the )?hands?|hand ?shake|give me (your|a) hand|" +
                "hand geben|gib mir (die|deine) hand|h(ä|ae)nde sch(ü|ue)tteln|handschlag|" +
                "schüttel|schuettel"
    ),
    "high_five" to Regex("high[ -]?five|abklatschen|gib mir f(ü|ue)nf|einschlagen"),
    "clap" to Regex("\\bclap|applause|klatsch|applaus"),
    "heart" to Regex("\\bheart\\b|\\bherz\\b|\\bherzchen"),
    "two_hand_kiss" to Regex("\\bkiss(es)?\\b|\\bkuss\\b|k(ü|ue)sschen"),
    "high_wave" to Regex("\\bwave\\b|\\bwaving|wink(e|en)?\\b|say hi\\b|say hello")
)

fun detectVoiceGesture(text: String): String? {
    val t = text.lowercase().trim()
    if (t.split(Regex("\\s+")).size > 9) return null
    return VoiceGesturePatterns.firstOrNull { (_, rx) -> rx.containsMatchIn(t) }?.first
}

fun voiceGestureReply(key: String, german: Boolean): String = when (key) {
    "shake_hand" -> if (german) "Gerne! Hier ist meine Hand. 🤝" else "Sure! Here is my hand. 🤝"
    "high_five" -> if (german) "Gib mir fünf! ✋" else "High five! ✋"
    "clap" -> if (german) "Applaus! 👏" else "Applause! 👏"
    "heart" -> if (german) "Ein Herz für dich! ❤️" else "A heart for you! ❤️"
    "two_hand_kiss" -> if (german) "Küsschen! 😘" else "Sending a kiss! 😘"
    else -> if (german) "Hallo! 👋" else "Hello there! 👋"
}
