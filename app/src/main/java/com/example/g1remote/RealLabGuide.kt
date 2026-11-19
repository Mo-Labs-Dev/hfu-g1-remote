package com.example.g1remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tour
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val LGreen = Color(0xFF009B77)
private val LText = Color(0xFF292F33)
private val LMuted = Color(0xFF657078)

/**
 * Lab Guide connected to the REAL robot: every route / tour taught on G1
 * (and edited in G1 Studio) appears here. Tap -> details -> Start -> G1 walks.
 */
@Composable
fun RealLabGuideScreen(german: Boolean, onBack: () -> Unit, onStarted: (title: String) -> Unit) {
    fun t(en: String, de: String) = if (german) de else en
    var routes by remember { mutableStateOf<List<GuideRoute>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<GuideRoute?>(null) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val routesVersion by G1StudioSync.routesVersion.collectAsState()

    LaunchedEffect(reload, routesVersion) {
        loading = true
        runCatching { G1GuideApi.routes() }
            .onSuccess {
                routes = it.sortedWith(compareBy<GuideRoute> { r -> if (r.isTour) 0 else 1 }.thenBy { r -> r.title(german) })
                offline = false
                selected = selected?.let { s -> it.find { r -> r.name == s.name } }
            }
            .onFailure { offline = true }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected != null) selected = null else onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = t("Back", "Zurück"), tint = LText)
            }
            Column(Modifier.weight(1f)) {
                Text(t("Lab Guide", "Laborführung"), color = LText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(t("Where would you like G1 to take you?", "Wohin soll G1 Sie bringen?"), color = LMuted, fontSize = 14.sp)
            }
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, null, tint = LMuted) }
        }

        val sel = selected
        when {
            loading && routes.isEmpty() -> Text(t("Loading destinations…", "Ziele werden geladen…"), color = LMuted)

            offline -> Surface(color = Color(0xFFFFF6E5), shape = RoundedCornerShape(12.dp)) {
                Text(
                    t("The guide is not active right now. Please ask the lab staff – or talk to G1.",
                      "Die Führung ist gerade nicht aktiv. Bitte fragen Sie das Laborteam – oder sprechen Sie mit G1."),
                    color = Color(0xFF7A520E), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            routes.isEmpty() -> Text(t("No destinations taught yet.", "Noch keine Ziele eingelernt."), color = LMuted)

            sel == null -> routes.forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE4E8EB), RoundedCornerShape(14.dp))
                        .clickable { selected = r; error = null },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(if (r.isTour) R.drawable.lab else R.drawable.lab_guide),
                        contentDescription = null,
                        modifier = Modifier.width(170.dp).height(110.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(Modifier.weight(1f).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (r.isTour) Icons.Default.Tour else Icons.Default.Place, null, tint = LGreen,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (r.isTour) t("Guided tour", "Führung") else t("Destination", "Ziel"),
                                color = LGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Text(r.title(german), color = LText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            (if (r.isTour) t("${r.stopCount} stops · ", "${r.stopCount} Stationen · ") else "") +
                                t("about ${r.estimatedMinutes()} min", "ca. ${r.estimatedMinutes()} Min."),
                            color = LMuted, fontSize = 13.sp
                        )
                    }
                    Text("›", color = LMuted, fontSize = 34.sp, modifier = Modifier.padding(end = 18.dp))
                }
            }

            else -> {
                // ---------------- details ----------------
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Image(
                        painter = painterResource(if (sel.isTour) R.drawable.lab else R.drawable.lab_guide),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).height(240.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(Modifier.weight(1f)) {
                        Text(sel.title(german), color = LText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (sel.isTour) t("G1 walks with you through the lab, stops at each station and explains it.",
                                "G1 läuft mit Ihnen durch das Labor, hält an jeder Station und erklärt sie.")
                            else t("G1 walks you there and then returns to its start position.",
                                "G1 bringt Sie dorthin und kehrt danach zum Start zurück."),
                            color = LMuted, fontSize = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(t("≈ ${sel.estimatedMinutes()} min", "≈ ${sel.estimatedMinutes()} Min."),
                                color = LText, fontWeight = FontWeight.Bold)
                            Text("${sel.lengthM.toInt()} m", color = LText, fontWeight = FontWeight.Bold)
                        }
                        if (sel.isTour && sel.stopTitles(german).isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            sel.stopTitles(german).forEachIndexed { i, st ->
                                Text("${i + 1}. $st", color = LText, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
                Surface(color = Color(0xFFEAF7F2), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsWalk, null, tint = LGreen)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t("Please walk behind G1 and keep a little distance. Staff can stop G1 at any time.",
                              "Bitte gehen Sie hinter G1 und halten Sie etwas Abstand. Das Team kann G1 jederzeit stoppen."),
                            color = Color(0xFF1F4D40), fontSize = 14.sp
                        )
                    }
                }
                error?.let {
                    Text(t("G1 can't start right now: $it", "G1 kann gerade nicht starten: $it"),
                        color = Color(0xFFB42020), fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { selected = null }, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)) {
                        Text(t("Back", "Zurück"), fontSize = 16.sp, color = LText)
                    }
                    Button(
                        onClick = {
                            starting = true
                            error = null
                            scope.launch {
                                val err = G1GuideApi.start(sel.name, german)
                                starting = false
                                if (err == null) onStarted(sel.title(german)) else error = err
                            }
                        },
                        enabled = !starting,
                        colors = ButtonDefaults.buttonColors(containerColor = LGreen),
                        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 16.dp)
                    ) {
                        Text(
                            if (starting) t("Starting…", "Startet…")
                            else if (sel.isTour) t("Start the tour", "Führung starten") else t("Take me there", "Bring mich hin"),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
