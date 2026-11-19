package com.example.g1remote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// G1 Studio colours (same as the web platform)
private val StudioGreen = Color(0xFF009B77)
private val StudioGreenDark = Color(0xFF00866A)
private val StudioHeader = Color(0xFFD7D8D6)
private val StudioPage = Color(0xFFF7F8F7)
private val StudioText = Color(0xFF292F33)
private val StudioMuted = Color(0xFF657078)
private val StudioBorder = Color(0xFFD8DDE1)

/**
 * Splash: HFU logo fades in, then "G1 Remote · Unitree G1". Calls onDone after ~2.6 s.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.92f) }
    val textAlpha = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(500)
        textAlpha.animateTo(1f, tween(600))
    }
    LaunchedEffect(Unit) {
        bar.animateTo(1f, tween(2300, easing = FastOutSlowInEasing))
        delay(300)
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.hfu_logo),
                contentDescription = "Hochschule Furtwangen University",
                modifier = Modifier
                    .width(360.dp)
                    .height(134.dp)
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.width(56.dp).height(3.dp).background(StudioGreen).alpha(textAlpha.value))
            Spacer(Modifier.height(22.dp))
            Text(
                "G1 Remote",
                color = StudioText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Unitree G1  ·  Humanoid Robot",
                color = StudioMuted,
                fontSize = 16.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier
                    .width(220.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE9EDEF))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bar.value)
                        .background(StudioGreen)
                )
            }
        }
        Text(
            "IPSE · Institut für Produkt und Service Engineering",
            color = StudioMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).alpha(textAlpha.value)
        )
    }
}

@Composable
private fun StatusPill(text: String, good: Boolean?) {
    val (bg, fg) = when (good) {
        true -> Color(0xFFEAF7F2) to StudioGreenDark
        false -> Color(0xFFFDECEC) to Color(0xFFB42020)
        null -> Color(0xFFF0F2F3) to StudioMuted
    }
    Text(
        text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Icon(icon, null, tint = StudioGreen, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = StudioText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(text, color = StudioMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/**
 * Welcome screen in the G1 Studio look: grey header with the HFU logo, green accent,
 * big title, robot + battery status, Studio announcement, Start / Talk to G1.
 */
@Composable
fun WelcomeScreen(
    german: Boolean,
    backendOnline: Boolean?,
    battery: G1StudioSync.Battery,
    studioConfig: G1StudioSync.AppConfig,
    onLanguage: (german: Boolean) -> Unit,
    onStart: () -> Unit,
    onTalk: () -> Unit,
    onOperator: () -> Unit,
    onFeedback: () -> Unit = {}
) {
    fun t(en: String, de: String) = if (german) de else en

    Column(Modifier.fillMaxSize().background(StudioPage)) {
        // ---------------- Studio-style header ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .background(StudioHeader)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.hfu_logo),
                contentDescription = "HFU",
                modifier = Modifier
                    .width(150.dp)
                    .height(52.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onOperator() }) },
                contentScale = ContentScale.Fit
            )
            Box(Modifier.padding(horizontal = 18.dp).width(1.dp).height(44.dp).background(Color(0xFFB6BAB8)))
            Column(Modifier.weight(1f)) {
                Text("G1 Remote", color = StudioText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(t("Visitor app", "Besucher-App"), color = StudioMuted, fontSize = 11.sp)
            }
            BatteryChip(battery)
            Spacer(Modifier.width(14.dp))
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White)
            ) {
                listOf(false to "EN", true to "DE").forEach { (de, label) ->
                    Text(
                        label,
                        color = if (german == de) Color.White else StudioMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(if (german == de) StudioGreen else Color.White)
                            .clickable { onLanguage(de) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ---------------- hero ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Box(Modifier.width(48.dp).height(3.dp).background(StudioGreen))
                Spacer(Modifier.height(18.dp))
                Text(
                    t("Meet G1", "Lernen Sie G1 kennen"),
                    color = StudioText,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 50.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    t(
                        "Your humanoid robot guide in the IPSE lab. Ask me questions, take a lab tour or watch the robot show.",
                        "Ihr humanoider Roboter-Guide im IPSE-Labor. Stellen Sie Fragen, machen Sie eine Laborführung oder sehen Sie die Roboter-Show."
                    ),
                    color = StudioMuted,
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        when (backendOnline) {
                            true -> t("G1 online", "G1 online")
                            false -> t("G1 offline", "G1 offline")
                            null -> t("Connecting…", "Verbinde…")
                        },
                        backendOnline
                    )
                    if (battery.soc != null) {
                        StatusPill(
                            t("Battery", "Akku") + " ${battery.soc.toInt()} %",
                            battery.level == "ok" || battery.charging
                        )
                    }
                }

                // Announcement from G1 Studio
                val ann = studioConfig.announcement
                val annText = if (german) ann.textDe.ifBlank { ann.textEn } else ann.textEn.ifBlank { ann.textDe }
                if (ann.active && annText.isNotBlank()) {
                    val warning = ann.level == "warning"
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (warning) Color(0xFFFFF6E5) else Color(0xFFEAF7F2), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (warning) Icons.Default.Warning else Icons.Default.Info, null,
                            tint = if (warning) Color(0xFFD79616) else StudioGreen
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(annText, color = StudioText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 18.dp)
                    ) {
                        Text(t("Start", "Los geht's"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.ArrowForward, null)
                    }
                    OutlinedButton(
                        onClick = onTalk,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        Icon(Icons.Default.Mic, null, tint = StudioGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Talk to G1", "Mit G1 sprechen"), color = StudioText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Image(
                painter = painterResource(R.drawable.unitree),
                contentDescription = "Unitree G1",
                modifier = Modifier
                    .weight(1.1f)
                    .aspectRatio(1672f / 941f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }

        // ---------------- features ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Feature(Icons.Default.Map, t("Lab tour", "Laborführung"),
                t("G1 walks with you and explains the lab.", "G1 läuft mit Ihnen und erklärt das Labor."),
                Modifier.weight(1f))
            Feature(Icons.Default.Mic, t("Ask anything", "Fragen Sie G1"),
                t("In English or German – G1 answers out loud.", "Auf Deutsch oder Englisch – G1 antwortet laut."),
                Modifier.weight(1f))
            Feature(Icons.Default.Celebration, t("Robot show", "Roboter-Show"),
                t("Waves, claps, hearts and a handshake.", "Winken, Klatschen, Herzen und Händeschütteln."),
                Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hochschule Furtwangen University · IPSE", color = StudioMuted, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onFeedback) {
                Text("★ " + t("Give feedback", "Feedback geben"), color = StudioGreenDark,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
