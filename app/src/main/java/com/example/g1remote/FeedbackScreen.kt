package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val FGreen = Color(0xFF009B77)
private val FText = Color(0xFF292F33)
private val FMuted = Color(0xFF657078)
private val FStar = Color(0xFFD79616)

object G1FeedbackApi {
    /** Returns null on success, otherwise an error message. */
    suspend fun submit(body: JSONObject): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("${G1Host.url(8000)}/api/feedback").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) null else "HTTP $code"
        }.getOrElse { it.message ?: "not reachable" }
    }
}

// System Usability Scale (Brooke 1996), "system" replaced by "G1"; German after Rummel.
private val SUS_EN = listOf(
    "I think that I would like to use G1 frequently.",
    "I found G1 unnecessarily complex.",
    "I thought G1 was easy to use.",
    "I think that I would need the support of a technical person to be able to use G1.",
    "I found the various functions of G1 were well integrated.",
    "I thought there was too much inconsistency in G1.",
    "I would imagine that most people would learn to use G1 very quickly.",
    "I found G1 very cumbersome to use.",
    "I felt very confident using G1.",
    "I needed to learn a lot of things before I could get going with G1."
)
private val SUS_DE = listOf(
    "Ich denke, dass ich G1 gerne häufig benutzen würde.",
    "Ich fand G1 unnötig komplex.",
    "Ich fand G1 einfach zu benutzen.",
    "Ich glaube, ich würde die Hilfe einer technisch versierten Person benötigen, um G1 benutzen zu können.",
    "Ich fand, die verschiedenen Funktionen von G1 waren gut integriert.",
    "Ich denke, G1 enthielt zu viele Inkonsistenzen.",
    "Ich kann mir vorstellen, dass die meisten Menschen den Umgang mit G1 sehr schnell lernen.",
    "Ich fand G1 sehr umständlich zu nutzen.",
    "Ich fühlte mich bei der Benutzung von G1 sehr sicher.",
    "Ich musste eine Menge lernen, bevor ich anfangen konnte, G1 zu verwenden."
)

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (selected) FGreen else Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) FGreen else Color(0xFFD8DDE1), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else FText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/**
 * Visitor feedback: stars + recommendation + comment, then optionally the 10 SUS
 * questions. Anonymous. Sent to G1 Studio (Evaluation page).
 */
@Composable
fun FeedbackScreen(german: Boolean, context: String, onDone: () -> Unit) {
    fun t(en: String, de: String) = if (german) de else en
    var step by remember { mutableStateOf(0) }            // 0 quick, 1 SUS, 2 thanks
    var rating by remember { mutableStateOf(0) }
    var recommend by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    val sus = remember { mutableStateListOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    // Nobody answers -> back home after 60 s (thank-you screen after 6 s).
    LaunchedEffect(step) {
        while (true) {
            delay(1000)
            val idle = System.currentTimeMillis() - lastTouch
            if ((step == 2 && idle > 6_000) || idle > 60_000) {
                onDone()
                return@LaunchedEffect
            }
        }
    }

    fun send(withSus: Boolean) {
        sending = true
        error = null
        scope.launch {
            val body = JSONObject()
                .put("lang", if (german) "de" else "en")
                .put("context", context)
                .put("comment", comment.trim())
            if (rating > 0) body.put("rating", rating)
            recommend?.let { body.put("recommend", it) }
            if (withSus) body.put("sus", JSONArray(sus.toList()))
            val err = G1FeedbackApi.submit(body)
            sending = false
            if (err == null) {
                step = 2
                lastTouch = System.currentTimeMillis()
            } else {
                error = t("Could not send: $err", "Senden fehlgeschlagen: $err")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8F7))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (step) {
            0 -> {
                Box(Modifier.width(48.dp).height(3.dp).background(FGreen))
                Spacer(Modifier.height(16.dp))
                Text(t("How did you like G1?", "Wie hat Ihnen G1 gefallen?"),
                    color = FText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(t("Anonymous – helps a master's thesis at HFU.", "Anonym – hilft einer Masterarbeit an der HFU."),
                    color = FMuted, fontSize = 15.sp)
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in 1..5) {
                        Icon(
                            if (k <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$k",
                            tint = FStar,
                            modifier = Modifier.size(58.dp).clickable {
                                rating = k
                                lastTouch = System.currentTimeMillis()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
                Text(t("Would you recommend G1 to a friend?", "Würden Sie G1 weiterempfehlen?"),
                    color = FText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("yes" to t("Yes", "Ja"), "maybe" to t("Maybe", "Vielleicht"), "no" to t("No", "Nein")).forEach { (v, l) ->
                        Choice(l, recommend == v, {
                            recommend = v
                            lastTouch = System.currentTimeMillis()
                        }, Modifier.width(140.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        comment = it.take(500)
                        lastTouch = System.currentTimeMillis()
                    },
                    label = { Text(t("Comment (optional)", "Kommentar (optional)")) },
                    modifier = Modifier.fillMaxWidth(0.7f),
                    maxLines = 3
                )
                error?.let { Text(it, color = Color(0xFFB42020), modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDone) { Text(t("Skip", "Überspringen"), color = FMuted, fontSize = 16.sp) }
                    OutlinedButton(
                        onClick = { send(false) },
                        enabled = !sending && (rating > 0 || recommend != null || comment.isNotBlank()),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
                    ) { Text(t("Send", "Senden"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FText) }
                    Button(
                        onClick = {
                            step = 1
                            lastTouch = System.currentTimeMillis()
                        },
                        enabled = !sending,
                        colors = ButtonDefaults.buttonColors(containerColor = FGreen),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
                    ) {
                        Text(t("Continue: 10 short questions (2 min)", "Weiter: 10 kurze Fragen (2 Min.)"),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            1 -> {
                Text(t("10 short questions", "10 kurze Fragen"), color = FText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(t("1 = strongly disagree · 5 = strongly agree", "1 = stimme überhaupt nicht zu · 5 = stimme voll zu"),
                    color = FMuted, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                val items = if (german) SUS_DE else SUS_EN
                items.forEachIndexed { i, q ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text("${i + 1}. $q", color = FText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (k in 1..5) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .background(if (sus[i] == k) FGreen else Color(0xFFF0F2F3), CircleShape)
                                        .clickable {
                                            sus[i] = k
                                            lastTouch = System.currentTimeMillis()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$k", color = if (sus[i] == k) Color.White else FText,
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFB42020), modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(16.dp))
                val done = sus.all { it > 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { step = 0 }) { Text(t("Back", "Zurück"), color = FMuted, fontSize = 16.sp) }
                    Button(
                        onClick = { send(true) },
                        enabled = done && !sending,
                        colors = ButtonDefaults.buttonColors(containerColor = FGreen),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp)
                    ) {
                        Text(
                            if (done) t("Send", "Senden")
                            else t("${sus.count { it > 0 }}/10 answered", "${sus.count { it > 0 }}/10 beantwortet"),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            else -> {
                Spacer(Modifier.height(80.dp))
                Icon(Icons.Default.CheckCircle, null, tint = FGreen, modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(18.dp))
                Text(t("Thank you!", "Vielen Dank!"), color = FText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(t("Your feedback helps us improve G1.", "Ihr Feedback hilft uns, G1 zu verbessern."),
                    color = FMuted, fontSize = 17.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
