package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val GGreen = Color(0xFF087B39)
private val GMint = Color(0xFFE7F5EC)
private val GDark = Color(0xFF172820)
private val GMuted = Color(0xFF64756B)
private val GRed = Color(0xFFB3261E)
private val GAmber = Color(0xFF9D4B00)

/** Live view of a real guided walk, with a big STOP button. */
@Composable
fun GuideScreen(
    isGerman: Boolean,
    routeTitle: String,
    onDone: () -> Unit
) {
    fun t(en: String, de: String) = if (isGerman) de else en

    var status by remember { mutableStateOf<GuideStatus?>(null) }
    var offline by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        var finishedAt: Long? = null
        while (isActive) {
            try {
                status = G1GuideApi.status()
                offline = false
            } catch (_: Exception) {
                offline = true
            }
            val s = status?.state
            if (s in setOf("done", "cancelled", "aborted")) {
                if (finishedAt == null) finishedAt = System.currentTimeMillis()
                if (System.currentTimeMillis() - finishedAt!! > 15_000) {
                    onDone()
                    return@LaunchedEffect
                }
            } else finishedAt = null
            delay(700)
        }
    }

    val s = status
    val (headline, color) = when (s?.state) {
        null, "idle", "starting" -> t("Getting ready…", "Einen Moment…") to GMuted
        "guiding" -> t("Please follow me to the $routeTitle", "Bitte folgen Sie mir: $routeTitle") to GGreen
        "blocked" -> t("Please wait – something is in my way", "Bitte warten – etwas ist im Weg") to GAmber
        "arrived" -> t("Here is the $routeTitle!", "Wir sind da: $routeTitle!") to GGreen
        "returning" -> t("I'm going back to my place", "Ich gehe zurück an meinen Platz") to GGreen
        "done" -> t("Thank you for visiting!", "Danke für Ihren Besuch!") to GGreen
        "cancelled" -> t("Guide stopped", "Führung gestoppt") to GAmber
        "aborted" -> t("I had to stop", "Ich musste anhalten") to GRed
        else -> (s?.state ?: "") to GMuted
    }
    val active = s?.state in setOf("starting", "guiding", "blocked", "arrived", "returning")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8F6))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDone, shape = RoundedCornerShape(50)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Home", "Start"))
            }
            Spacer(Modifier.weight(1f))
            Text(t("G1 Guide", "G1 Führung"), color = GMuted, fontSize = 14.sp)
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(GMint),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (s?.state) {
                    "blocked" -> "✋"
                    "arrived", "done" -> "📍"
                    "aborted", "cancelled" -> "⏹"
                    else -> "🚶"
                },
                fontSize = 52.sp
            )
        }
        Text(headline, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)

        val progress = (s?.progress ?: 0.0).toFloat().coerceIn(0f, 1f)
        val legText = if (s?.leg == "back") t("Way back", "Rückweg") else t("Way there", "Hinweg")
        if (active) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(50)),
                color = GGreen,
                trackColor = GMint
            )
            Text("$legText · ${(progress * 100).toInt()} %", color = GMuted, fontSize = 15.sp)
        }

        val detail = when {
            offline -> t("Guide service not reachable", "Führungsdienst nicht erreichbar")
            s?.state == "aborted" -> s?.message ?: ""
            s?.state == "blocked" -> s?.message ?: ""
            else -> ""
        }
        if (detail.isNotBlank()) {
            Text(detail, color = GMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.weight(1f))
        if (active || offline) {
            Button(
                onClick = {
                    scope.launch {
                        G1GuideApi.cancel()
                        // Also stop motion directly on the robot (no PIN needed).
                        runCatching { G1TeleopClient().stop() }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(84.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GRed)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text("STOP", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(64.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GGreen)
            ) { Text(t("Back to start", "Zurück zum Start"), fontSize = 18.sp) }
        }
        Text(
            t("Please walk behind G1 and keep a little distance.",
              "Bitte gehen Sie hinter G1 und halten Sie etwas Abstand."),
            color = GMuted, fontSize = 13.sp
        )
    }
}
