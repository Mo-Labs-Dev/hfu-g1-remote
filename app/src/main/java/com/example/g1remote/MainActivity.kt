package com.example.g1remote

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.g1remote.ui.theme.G1RemoteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

private val Green = Color(0xFF087B39)
private val Mint = Color(0xFFE7F5EC)
private val Background = Color(0xFFF7F9F8)
private val Dark = Color(0xFF172820)
private val Muted = Color(0xFF64756B)
private val BorderColor = Color(0xFFDCE7DF)

private const val INSTITUTE =
    "Institute for Product and Service Engineering"

private const val IPSE_URL =
    "https://www.hs-furtwangen.de/en/research/" +
            "research-institutes/" +
            "institute-for-product-and-service-engineering"

// ======================================================
// SCREENS
// ======================================================

private enum class Screen {
    HOME,
    LAB_GUIDE,
    DESTINATION,
    DEMONSTRATIONS,
    DEMO_DETAIL,
    LAB_MAP,
    LAB_INFO,
    WEBSITE,
    NAVIGATION,
    SAFETY_PRESENTATION,
    VISITOR_WELCOME,
    OPERATOR,
    VOICE
}

// NEW: Language selection
private enum class AppLanguage {
    EN,
    DE
}

private fun tr(
    language: AppLanguage,
    english: String,
    german: String
): String {
    return if (language == AppLanguage.EN) english else german
}

// ======================================================
// DATA MODELS
// ======================================================

private data class VisitorItem(
    val title: String,
    val subtitle: String,
    val description: String,
    val image: Int
)

private data class SafetySlide(
    val title: String,
    val subtitle: String,
    val narration: String,
    val image: Int
)

// Example content only.
// Replace with approved HFU laboratory safety
// instructions before using this for real visitors.

private val safetySlides = listOf(
    SafetySlide(
        title = "Welcome to HFU IPSE",
        subtitle = "Laboratory Safety Introduction",
        narration = "Welcome to the HFU laboratory. " +
                "Before exploring the laboratory, " +
                "please listen to these example safety instructions.",
        image = R.drawable.robot
    ),
    SafetySlide(
        title = "Follow Staff Instructions",
        subtitle = "Your safety comes first",
        narration = "Please follow the instructions " +
                "given by authorized laboratory staff. " +
                "If you are unsure about anything, " +
                "ask your host before continuing.",
        image = R.drawable.lab_info
    ),
    SafetySlide(
        title = "Keep Clear of Moving Robots",
        subtitle = "Respect the demonstration area",
        narration = "Keep a safe distance from robots " +
                "and other moving laboratory equipment. " +
                "Do not enter a demonstration area " +
                "unless authorized staff permit it.",
        image = R.drawable.demonstration
    ),
    SafetySlide(
        title = "Do Not Touch Equipment",
        subtitle = "Only authorized operation",
        narration = "Please do not touch, operate, " +
                "or adjust laboratory equipment " +
                "without permission from an authorized " +
                "staff member.",
        image = R.drawable.lab
    ),
    SafetySlide(
        title = "Ask for Assistance",
        subtitle = "Report concerns to laboratory staff",
        narration = "If you notice an unsafe situation " +
                "or need assistance, please inform " +
                "the laboratory staff immediately.",
        image = R.drawable.lab_guide
    )
)

// ======================================================
// LAB DESTINATIONS
// ======================================================

private val destinations = listOf(
    VisitorItem(
        "Coffee Machine",
        "Visitor guidance",
        "G1's planned guidance demonstration uses " +
                "an approved route to the coffee machine. " +
                "Physical movement is controlled by G1 Studio.",
        R.drawable.lab_guide
    ),
    VisitorItem(
        "3D Printing",
        "Additive manufacturing",
        "Explore the laboratory's 3D printing area.",
        R.drawable.lab
    ),
    VisitorItem(
        "Computer Area",
        "Engineering workstations",
        "Discover the engineering computer workstations.",
        R.drawable.lab_info
    )
)

// ======================================================
// ROBOT DEMONSTRATIONS
// ======================================================

private val demonstrations = listOf(
    VisitorItem(
        "Robot Introduction",
        "Meet Unitree G1",
        "Learn about the humanoid robot and " +
                "the G1 Studio visitor experience.",
        R.drawable.robot
    ),
    VisitorItem(
        "Visitor Guidance",
        "Coffee machine demonstration",
        "A planned supervised demonstration in which " +
                "G1 guides a visitor along a predefined route.",
        R.drawable.lab_guide
    ),
    VisitorItem(
        "Walking Demonstration",
        "Humanoid locomotion",
        "Learn about controlled humanoid locomotion.",
        R.drawable.demonstration
    ),
    VisitorItem(
        "Voice Interaction",
        "Talk to G1",
        "Discover the planned voice interaction system.",
        R.drawable.robot2
    )
)

// ======================================================
// MAIN ACTIVITY
// ======================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()

        setContent {
            G1RemoteTheme {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Green,
                        background = Background,
                        surface = Color.White
                    )
                ) {
                    G1RemoteApp()
                }
            }
        }
    }
}

// ======================================================
// APP NAVIGATION
// ======================================================

@Composable
private fun G1RemoteApp() {

    var backendStatus by remember {
        mutableStateOf<G1Backend.ConnectionStatus?>(null)
    }

    LaunchedEffect(Unit) {
        while (true) {
            backendStatus = G1Backend.checkConnection()
            delay(5000L)
        }
    }

    var screen by remember { mutableStateOf(Screen.HOME) }

    // Read-only workflow observation. On launch, ignore the previous execution
    // so an old completed workflow cannot hijack the visitor display.
    var observedGuidanceSessionId by remember { mutableStateOf<String?>(null) }
    var guidanceBaselineLoaded by remember { mutableStateOf(false) }
    var autoGuidanceSessionId by remember { mutableStateOf<String?>(null) }
    var guidanceFinishedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val guidance = G1Backend.getGuidanceStatus()
                val id = guidance.sessionId
                if (!guidanceBaselineLoaded) {
                    observedGuidanceSessionId = id
                    guidanceBaselineLoaded = true
                } else if (id != null && id != observedGuidanceSessionId) {
                    observedGuidanceSessionId = id
                    // Welcome and safety must take priority over auto-navigation.
                    if (screen != Screen.SAFETY_PRESENTATION &&
                        screen != Screen.VISITOR_WELCOME) {
                        autoGuidanceSessionId = id
                        guidanceFinishedAt = null
                        screen = Screen.NAVIGATION
                    }
                }

                if (autoGuidanceSessionId != null &&
                    id == autoGuidanceSessionId &&
                    screen == Screen.NAVIGATION) {
                    if (guidance.state in setOf("completed", "failed", "cancelled", "canceled")) {
                        if (guidanceFinishedAt == null) {
                            guidanceFinishedAt = android.os.SystemClock.elapsedRealtime()
                        }
                        if (android.os.SystemClock.elapsedRealtime() - guidanceFinishedAt!! >= 12000L) {
                            autoGuidanceSessionId = null
                            guidanceFinishedAt = null
                            screen = Screen.HOME
                        }
                    } else {
                        guidanceFinishedAt = null
                    }
                }
            } catch (_: Exception) {
                // Keep the visitor's current screen during a network outage.
            }
            delay(1000L)
        }
    }
    var activeWelcomeSessionId by remember { mutableStateOf<String?>(null) }
    var handledWelcomeSessionId by remember { mutableStateOf<String?>(null) }
    var welcomeText by remember { mutableStateOf("") }
    var welcomeHomeVisible by remember { mutableStateOf(false) }
    var welcomeScreenSessionId by remember { mutableStateOf<String?>(null) }
    var welcomeBusy by remember { mutableStateOf(false) }
    var welcomeError by remember { mutableStateOf<String?>(null) }
    val welcomeScope = rememberCoroutineScope()

    // Observe new physical-camera visitor sessions, without controlling the robot.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val status = G1VisitorWelcomeApi.getStatus()
                val id = status.sessionId
                if (status.enabled && id != null &&
                    id != handledWelcomeSessionId &&
                    status.state in setOf("welcoming", "safety_briefing", "awaiting_acknowledgement")) {
                    activeWelcomeSessionId = id
                    handledWelcomeSessionId = id
                    welcomeText = status.welcomeText
                    welcomeHomeVisible = true
                    welcomeError = null
                    // Show the welcome screen for this newly detected visitor.
                    welcomeScreenSessionId = id
                    screen = Screen.VISITOR_WELCOME
                }
                // Allow a future session after the operator resets the backend.
                if (id == null && status.state in setOf("idle", "waiting_for_visitor")) {
                    activeWelcomeSessionId = null
                    welcomeHomeVisible = false
                }
            } catch (_: Exception) {
                // Preserve the current visitor screen during a temporary network outage.
            }
            delay(1000L)
        }
    }

    // Unitree speech currently reports request acceptance, not playback completion.
    // This is an estimated display duration, not actual audio synchronization.
    LaunchedEffect(welcomeScreenSessionId) {
        val session = welcomeScreenSessionId ?: return@LaunchedEffect
        delay(12_000L)
        if (welcomeScreenSessionId == session && screen == Screen.VISITOR_WELCOME) {
            screen = Screen.HOME
        }
    }

    // Shared across visits to the voice screen; lost when the app process ends.
    var visitorSessionId by remember { mutableStateOf<String?>(null) }

    // NEW: English / German
    var language by remember {
        mutableStateOf(AppLanguage.EN)
    }

    var selectedDestination by remember {
        mutableStateOf(destinations.first())
    }

    var selectedDemo by remember {
        mutableStateOf(demonstrations.first())
    }

    val home: () -> Unit = {
        autoGuidanceSessionId = null
        guidanceFinishedAt = null
        screen = Screen.HOME
    }

    val back: () -> Unit = {
        if (screen == Screen.NAVIGATION) {
            autoGuidanceSessionId = null
            guidanceFinishedAt = null
        }
        screen = when (screen) {
            Screen.DESTINATION -> Screen.LAB_GUIDE
            Screen.DEMO_DETAIL -> Screen.DEMONSTRATIONS
            Screen.NAVIGATION -> Screen.DESTINATION
            Screen.WEBSITE -> Screen.LAB_INFO
            else -> Screen.HOME
        }
    }

    BackHandler(
        enabled = screen != Screen.HOME
    ) {
        back()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

        if (
            screen != Screen.WEBSITE &&
            screen != Screen.SAFETY_PRESENTATION
        ) {
            RemoteHeader(
                isHome = screen == Screen.HOME,
                language = language,
                backendStatus = backendStatus,
                onLanguageChange = {
                    language = it
                },
                onHome = home,
                onOperator = home // Visitor tablet must never open operator controls.
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (screen) {

                Screen.HOME -> HomeScreen(
                    language = language,
                    visitorWelcomed = welcomeHomeVisible,
                    onNavigate = {
                        screen = it
                    }
                )

                Screen.VISITOR_WELCOME -> G1VisitorWelcomeScreen(
                    welcomeText = welcomeText,
                    isGerman = language == AppLanguage.DE,
                    isLoading = welcomeBusy,
                    error = welcomeError,
                    onHome = home,
                    onStartSafety = {
                        val id = activeWelcomeSessionId
                        if (id == null) {
                            welcomeError = "No active visitor session. Ask the operator to start Welcome Mode."
                        } else if (!welcomeBusy) {
                            welcomeBusy = true
                            welcomeError = null
                            welcomeScope.launch {
                                try {
                                    val status = G1VisitorWelcomeApi.getStatus()
                                    require(status.sessionId == id) { "Visitor session changed. Please wait for G1." }
                                    if (status.state == "welcoming") {
                                        G1VisitorWelcomeApi.advance(id, "welcome_completed")
                                    } else {
                                        require(status.state in setOf("safety_briefing", "awaiting_acknowledgement")) {
                                            "Visitor session is not ready for safety briefing."
                                        }
                                    }
                                    screen = Screen.SAFETY_PRESENTATION
                                } catch (e: Exception) {
                                    welcomeError = e.message ?: "Unable to start safety briefing"
                                } finally {
                                    welcomeBusy = false
                                }
                            }
                        }
                    }
                )

                Screen.SAFETY_PRESENTATION -> SafetyPresentationScreen(
                    onHome = home,
                    autoNarrateFirstSlide = true,
                    existingSessionId = activeWelcomeSessionId,
                    onAcknowledged = { completedId ->
                        if (completedId == activeWelcomeSessionId) {
                            screen = Screen.HOME
                        }
                    }
                )

                Screen.LAB_GUIDE -> CatalogScreen(
                    title = "Lab Guide",
                    subtitle = "Where would you like to go?",
                    items = destinations,
                    onBack = back,
                    onSelect = {
                        selectedDestination = it
                        screen = Screen.DESTINATION
                    }
                )

                Screen.DESTINATION -> DetailScreen(
                    item = selectedDestination,
                    onBack = back,
                    actionLabel =
                        if (
                            selectedDestination.title ==
                            "Coffee Machine"
                        ) {
                            "Preview Navigation"
                        } else {
                            null
                        },
                    onAction = {
                        screen = Screen.NAVIGATION
                    }
                )

                Screen.DEMONSTRATIONS -> CatalogScreen(
                    title = "Robot Demonstrations",
                    subtitle = "Discover the G1 visitor experience",
                    items = demonstrations,
                    onBack = back,
                    onSelect = {
                        selectedDemo = it
                        screen = Screen.DEMO_DETAIL
                    }
                )

                Screen.DEMO_DETAIL -> DetailScreen(
                    item = selectedDemo,
                    onBack = back,
                    actionLabel = null,
                    onAction = {}
                )

                Screen.LAB_MAP -> LabMapScreen(
                    onBack = back
                )

                Screen.LAB_INFO -> LabInfoScreen(
                    onBack = back,
                    onWebsite = {
                        screen = Screen.WEBSITE
                    }
                )

                Screen.WEBSITE -> FullScreenWebsite(
                    onHome = home,
                    onBack = back
                )

                Screen.NAVIGATION -> GuidanceStatusScreen(
                    onBack = back
                )

                // NEW: Talk to G1
                Screen.VOICE -> TalkToG1Screen(
                    language = language,
                    onBack = back,
                    onStartSafety = { screen = Screen.SAFETY_PRESENTATION },
                    onGuidanceStarted = { guidanceSessionId ->
                        observedGuidanceSessionId = guidanceSessionId
                        autoGuidanceSessionId = guidanceSessionId
                        guidanceFinishedAt = null
                        screen = Screen.NAVIGATION
                    },
                    sessionId = visitorSessionId,
                    onSessionIdChange = { visitorSessionId = it }
                )

                // No operator controls are reachable from the visitor tablet.
                Screen.OPERATOR -> HomeScreen(language = language, onNavigate = { screen = it })
            }
        }
    }
}

// ======================================================
// VISITOR WELCOME — READ-ONLY DISPLAY
// ======================================================

@Composable
private fun G1VisitorWelcomeScreen(
    welcomeText: String,
    isGerman: Boolean,
    isLoading: Boolean,
    error: String?,
    onStartSafety: () -> Unit,
    onHome: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Background).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Mint)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.robot),
                    contentDescription = "Unitree G1 humanoid robot",
                    modifier = Modifier.width(180.dp).height(230.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (isGerman) "Willkommen bei IPSE!" else "Welcome to IPSE!",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Green
                    )
                    Text(
                        if (isGerman) "G1 begrüßt Sie gerade" else "G1 is welcoming you",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Dark
                    )
                    Text(
                        welcomeText.ifBlank {
                            "Hello! Welcome to the Institute for Product and Service Engineering."
                        },
                        fontSize = 15.sp,
                        color = Dark
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Green
                    )
                    Text(
                        if (isGerman)
                            "Die Startseite erscheint gleich automatisch."
                        else "Home will open automatically in a moment.",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onHome) {
                            Text(if (isGerman) "Zur Startseite" else "Go to Home")
                        }
                        OutlinedButton(onClick = onStartSafety, enabled = !isLoading) {
                            Text(if (isGerman) "Sicherheit" else "Safety briefing")
                        }
                    }
                }
            }
        }
    }
}

// ======================================================
// ORIGINAL HEADER + LANGUAGE SELECTOR
// ======================================================

@Composable
private fun RemoteHeader(
    isHome: Boolean,
    language: AppLanguage,
    backendStatus: G1Backend.ConnectionStatus?,
    onLanguageChange: (AppLanguage) -> Unit,
    onHome: () -> Unit,
    onOperator: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(Color(0xFFF0F3F1))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Image(
            painter = painterResource(R.drawable.hfu_logo),
            contentDescription = "HFU",
            modifier = Modifier
                .width(135.dp)
                .height(48.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.width(15.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                "G1 Remote",
                color = Green,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )

            Text(
                INSTITUTE,
                color = Dark,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = when {
                    backendStatus == null -> "Checking..."
                    backendStatus.connected -> "Backend Connected"
                    else -> "Backend Offline"
                },
                color = if (backendStatus?.connected == true) Green
                else Color(0xFFB3261E),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Text(
                text = when {
                    backendStatus == null -> "Connecting to Ubuntu"
                    backendStatus.connected -> "G1 Studio API · Online"
                    else -> backendStatus.error.ifBlank { "Connection unavailable" }
                },
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(10.dp))

        // NEW: Language selector
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextButton(
                onClick = {
                    onLanguageChange(AppLanguage.EN)
                }
            ) {
                Text(
                    "EN",
                    color = if (
                        language == AppLanguage.EN
                    ) Green else Muted,
                    fontWeight = if (
                        language == AppLanguage.EN
                    ) FontWeight.Bold
                    else FontWeight.Normal
                )
            }

            TextButton(
                onClick = {
                    onLanguageChange(AppLanguage.DE)
                }
            ) {
                Text(
                    "DE",
                    color = if (
                        language == AppLanguage.DE
                    ) Green else Muted,
                    fontWeight = if (
                        language == AppLanguage.DE
                    ) FontWeight.Bold
                    else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        IconButton(
            onClick = {
                onHome()
            }
        ) {

            Icon(
                Icons.Default.Home,
                contentDescription = "Navigation",
                tint = Green
            )
        }
    }
}

// ======================================================
// ORIGINAL HOME SCREEN
// SIXTH CARD ADDED: TALK TO G1
// ======================================================

@Composable
private fun HomeScreen(
    language: AppLanguage,
    onNavigate: (Screen) -> Unit,
    visitorWelcomed: Boolean = false
) {

    data class HomeTile(
        val title: String,
        val image: Int,
        val screen: Screen
    )

    val cards = listOf(
        HomeTile(
            tr(
                language,
                "Lab Guide",
                "Laborführung"
            ),
            R.drawable.lab_guide,
            Screen.LAB_GUIDE
        ),

        HomeTile(
            tr(
                language,
                "Demonstrations",
                "Demonstrationen"
            ),
            R.drawable.demonstration,
            Screen.DEMONSTRATIONS
        ),

        HomeTile(
            tr(
                language,
                "Lab Map",
                "Laborplan"
            ),
            R.drawable.lab_map,
            Screen.LAB_MAP
        ),

        HomeTile(
            tr(
                language,
                "Lab Info",
                "Laborinfo"
            ),
            R.drawable.lab_info,
            Screen.LAB_INFO
        ),

        HomeTile(
            tr(
                language,
                "Safety Presentation",
                "Sicherheit"
            ),
            R.drawable.robot2,
            Screen.SAFETY_PRESENTATION
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        if (visitorWelcomed) {
            Surface(color = Mint, shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr(language,
                            "Welcome to IPSE! G1 is ready to assist you.",
                            "Willkommen bei IPSE! G1 hilft Ihnen gerne."),
                        color = Dark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ORIGINAL G1 BANNER
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Mint
            ),
            shape = RoundedCornerShape(18.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Image(
                    painter = painterResource(R.drawable.robot),
                    contentDescription = "G1",
                    modifier = Modifier
                        .width(185.dp)
                        .fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp)
                ) {

                    Text(
                        "Unitree G1",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = Dark
                    )

                    Text(
                        tr(
                            language,
                            "Humanoid Robot",
                            "Humanoider Roboter"
                        ),
                        fontSize = 13.sp,
                        color = Muted
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        tr(
                            language,
                            "Robot status: Not connected",
                            "Roboterstatus: Nicht verbunden"
                        ),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .width(226.dp)
                        .padding(end = 16.dp, top = 6.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        tr(language, "Connecting Humans and Robots", "Menschen und Roboter verbinden"),
                        color = Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2
                    )

                    Text(
                        tr(language, "Ask G1 about the laboratory", "Frag G1 über das Labor"),
                        color = Muted,
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )

                    Button(
                        onClick = { onNavigate(Screen.VOICE) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr(language, "Talk to G1", "Mit G1 sprechen"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Visitor-facing camera preview. No camera is connected yet.
        HomeCameraPreview(language = language)

        // ORIGINAL CARD ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            cards.forEach { card ->

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .clickable {
                            onNavigate(card.screen)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {

                    Column {

                        Image(
                            painter = painterResource(card.image),
                            contentDescription = card.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentScale = ContentScale.Crop
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                card.title,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Dark,
                                maxLines = 2,
                                lineHeight = 14.sp
                            )

                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Notice(
            "Visitor interface preview. Robot actions " +
                    "will be controlled by G1 Studio."
        )
    }
}


// ======================================================
// HOME CAMERA PREVIEW — READ-ONLY G1 SDK2 SNAPSHOTS
// ======================================================

private const val G1_CAMERA_SNAPSHOT_URL = "http://10.150.68.160:8770/snapshot"

@Composable
private fun HomeCameraPreview(language: AppLanguage) {
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Fetch snapshots only while Home is displayed; no robot control commands.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val connection = (URL(G1_CAMERA_SNAPSHOT_URL).openConnection()
                            as HttpURLConnection)
                    try {
                        connection.connectTimeout = 2500
                        connection.readTimeout = 2500
                        connection.setRequestProperty("Cache-Control", "no-cache")
                        if (connection.responseCode != 200) {
                            throw IllegalStateException("Camera HTTP ${connection.responseCode}")
                        }
                        connection.inputStream.use { stream ->
                            BitmapFactory.decodeStream(stream)
                                ?: throw IllegalStateException("Invalid camera image")
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                frame = bitmap.asImageBitmap()
                cameraError = null
            } catch (e: Exception) {
                frame = null
                cameraError = e.message ?: "Camera unavailable"
            }
            delay(1000L)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.width(220.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B2A25)),
                contentAlignment = Alignment.Center
            ) {
                if (frame != null) {
                    Image(
                        bitmap = frame!!,
                        contentDescription = "Physical G1 camera preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideocamOff, null, tint = Color.White)
                        Text(
                            tr(language, "Camera unavailable", "Kamera nicht verfügbar"),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, null, tint = Green)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        tr(language, "G1 Live Camera", "G1 Live-Kamera"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Dark
                    )
                }
                Text(
                    if (frame != null) tr(language, "Connected · Refreshes every second", "Verbunden · Aktualisierung jede Sekunde")
                    else tr(language, "Not connected", "Nicht verbunden"),
                    color = if (frame != null) Green else Muted,
                    fontSize = 12.sp
                )
                Text(
                    cameraError ?: tr(language,
                        "Read-only view from G1's physical camera.",
                        "Nur-Lese-Ansicht der G1-Kamera."),
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ======================================================
// SAFETY PRESENTATION
// ======================================================

private const val SAFETY_API = "http://10.150.68.160:8000/api/visitor-safety"

private data class ApiSafetySlide(
    val id: String,
    val title: String,
    val subtitle: String,
    val points: List<String>
)

private data class ApiSafetyBriefing(
    val slides: List<ApiSafetySlide>,
    val approved: Boolean,
    val version: String
)

private fun safetyHttp(path: String, method: String = "GET", payload: JSONObject? = null): JSONObject {
    val connection = (URL("$SAFETY_API$path").openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(250)}")
        return JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

private fun fetchSafetyBriefing(): ApiSafetyBriefing {
    val result = safetyHttp("/briefing")
    val array = result.getJSONArray("slides")
    val slides = (0 until array.length()).map { index ->
        val slide = array.getJSONObject(index)
        val points = slide.getJSONArray("points")
        ApiSafetySlide(
            id = slide.getString("id"),
            title = slide.getString("title"),
            subtitle = slide.optString("subtitle"),
            points = (0 until points.length()).map { points.getString(it) }
        )
    }
    return ApiSafetyBriefing(
        slides = slides,
        approved = result.optString("approval_status") == "approved",
        version = result.getString("version")
    )
}

// Timed progression is an estimate, NOT Unitree playback-completion feedback.
// Increase this buffer if G1 is still talking when the next slide appears.
private const val SAFETY_SPEECH_BUFFER_MS = 6000L
private const val SAFETY_SPEECH_WORDS_PER_MINUTE = 105L

private val originalSafetyImages = mapOf(
    "welcome" to R.drawable.hfu_safety_01,
    "overview" to R.drawable.hfu_safety_02,
    "fire_response" to R.drawable.hfu_safety_03,
    "evacuation" to R.drawable.hfu_safety_04,
    "small_fires" to R.drawable.hfu_safety_05,
    "extinguishers" to R.drawable.hfu_safety_06,
    "assembly" to R.drawable.hfu_safety_07,
    "accident_response" to R.drawable.hfu_safety_08,
    "first_aid" to R.drawable.hfu_safety_09,
    "defibrillator" to R.drawable.hfu_safety_10,
    "lab_access" to R.drawable.hfu_safety_11,
    "lab_housekeeping" to R.drawable.hfu_safety_12,
    "electrical" to R.drawable.hfu_safety_13,
    "waste" to R.drawable.hfu_safety_14,
    "summary" to R.drawable.hfu_safety_15,
)
private fun estimatedSafetyNarrationMs(text: String): Long {
    val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.toLong()
    return ((words * 60_000L) / SAFETY_SPEECH_WORDS_PER_MINUTE +
            SAFETY_SPEECH_BUFFER_MS).coerceIn(10_000L, 120_000L)
}

@Composable
private fun SafetyPresentationScreen(
    onHome: () -> Unit,
    autoNarrateFirstSlide: Boolean = false,
    existingSessionId: String? = null,
    onAcknowledged: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var briefing by remember { mutableStateOf<ApiSafetyBriefing?>(null) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var currentSlide by remember { mutableIntStateOf(0) }
    var visitedSlideIds by remember { mutableStateOf(setOf<String>()) }
    var acknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var narrationStatus by remember { mutableStateOf("Preparing safety presentation…") }
    var understood by remember { mutableStateOf(false) }
    var autoPlaying by remember { mutableStateOf(true) }
    var replayNonce by remember { mutableIntStateOf(0) }
    var narrationRunning by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(existingSessionId) {
        busy = true
        try {
            // The visitor may open Safety later from Home or by voice.
            // Only then transition the welcome workflow to safety_briefing.
            if (existingSessionId != null) {
                val welcomeStatus = G1VisitorWelcomeApi.getStatus()
                require(welcomeStatus.sessionId == existingSessionId) {
                    "Visitor session changed. Please ask the operator to reset Welcome Mode."
                }
                if (welcomeStatus.state == "welcoming") {
                    G1VisitorWelcomeApi.advance(existingSessionId, "welcome_completed")
                }
            }
            val data = withContext(Dispatchers.IO) { fetchSafetyBriefing() }
            val resolvedSessionId = if (existingSessionId != null) {
                // Validate the exact Welcome Manager session; never create a second one.
                withContext(Dispatchers.IO) {
                    safetyHttp("/sessions/$existingSessionId")
                }
                existingSessionId
            } else {
                // Standalone safety preview from Home, separate from an active visitor welcome.
                val session = withContext(Dispatchers.IO) { safetyHttp("/sessions", "POST") }
                session.getString("session_id")
            }
            briefing = data
            sessionId = resolvedSessionId
            if (data.slides.isNotEmpty()) visitedSlideIds = setOf(data.slides.first().id)
            error = null
        } catch (exception: Exception) {
            autoPlaying = false
            error = exception.message ?: "Cannot load safety briefing"
        } finally {
            busy = false
        }
    }

    val slides = briefing?.slides.orEmpty()
    val slide = slides.getOrNull(currentSlide)

    // A new slide starts narration automatically. Pausing only freezes the
    // countdown; it cannot stop audio already playing on the physical robot.
    LaunchedEffect(sessionId, currentSlide, replayNonce) {
        val id = sessionId ?: return@LaunchedEffect
        val item = slides.getOrNull(currentSlide) ?: return@LaunchedEffect
        if (!autoPlaying) return@LaunchedEffect
        narrationRunning = true
        remainingMs = 0L
        narrationStatus = "Sending slide ${currentSlide + 1} narration to G1…"
        try {
            val response = withContext(Dispatchers.IO) {
                safetyHttp("/sessions/$id/slides/${item.id}/speak", "POST")
            }
            if (!response.optBoolean("speech_accepted")) {
                throw IllegalStateException("G1 did not accept the narration")
            }
            val narration = response.optString("narration", "")
            if (narration.isBlank()) {
                throw IllegalStateException("Narration text missing; cannot estimate duration")
            }
            remainingMs = estimatedSafetyNarrationMs(narration)
            while (remainingMs > 0L) {
                if (autoPlaying) {
                    narrationStatus = "G1 narration — estimated ${((remainingMs + 999L) / 1000L)}s remaining"
                    val tick = minOf(250L, remainingMs)
                    delay(tick)
                    remainingMs -= tick
                } else {
                    narrationStatus = "Paused (G1 audio may still be playing)"
                    delay(250L)
                }
            }
            narrationRunning = false
            if (autoPlaying && currentSlide < slides.lastIndex) {
                currentSlide += 1
                visitedSlideIds = visitedSlideIds + slides[currentSlide].id
            } else {
                narrationStatus = if (currentSlide == slides.lastIndex)
                    "Presentation finished. Please review and acknowledge below."
                else "Paused. Tap Resume to continue."
                autoPlaying = false
            }
        } catch (exception: Exception) {
            narrationRunning = false
            autoPlaying = false
            error = exception.message ?: "Narration unavailable"
            narrationStatus = "Narration stopped. Tap Replay to retry."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HealthAndSafety, null, tint = Green)
            Spacer(Modifier.width(10.dp))
            Text("HFU IPSE · Visitor Safety", modifier = Modifier.weight(1f),
                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Dark)
            Text(if (slides.isEmpty()) "Loading" else "${currentSlide + 1} / ${slides.size}",
                color = Green, fontWeight = FontWeight.Bold)
            IconButton(onClick = onHome) { Icon(Icons.Default.Close, "Close") }
        }

        if (briefing != null && briefing?.approved != true) {
            Surface(color = Color(0xFFFFF2DD), shape = RoundedCornerShape(10.dp)) {
                Text("DRAFT — Pending laboratory supervisor approval. Not valid as formal safety instruction.",
                    modifier = Modifier.padding(9.dp), fontSize = 12.sp, color = Color(0xFF825200))
            }
        }
        if (busy && slide == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Loading visitor safety briefing from G1 Studio…")
        }
        if (error != null) {
            Text("Connection error: $error", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        if (slide != null) {
            Text(slide.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Dark)
            Text(slide.subtitle, fontSize = 13.sp, color = Muted)
            Card(modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Mint),
                shape = RoundedCornerShape(14.dp)) {
                val originalImage = originalSafetyImages[slide.id]
                if (originalImage != null) {
                    Image(
                        painter = painterResource(originalImage),
                        contentDescription = "Original HFU safety slide: ${slide.title}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        slide.points.forEach { point ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircle, null, tint = Green,
                                    modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(point, fontSize = 15.sp, color = Dark)
                            }
                        }
                    }
                }
            }
            Text(narrationStatus, color = Muted, fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { (currentSlide + 1).toFloat() / slides.size },
                modifier = Modifier.fillMaxWidth(), color = Green)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    autoPlaying = false
                    currentSlide = (currentSlide - 1).coerceAtLeast(0)
                    visitedSlideIds = visitedSlideIds + slides[currentSlide].id
                    narrationStatus = "Previous slide. Tap Replay to narrate."
                    error = null
                }, enabled = currentSlide > 0 && !narrationRunning && !busy) {
                    Icon(Icons.Default.ChevronLeft, null)
                    Text("Previous")
                }
                Button(onClick = {
                    if (autoPlaying) {
                        autoPlaying = false
                    } else {
                        autoPlaying = true
                        if (!narrationRunning) replayNonce += 1
                    }
                }, enabled = !busy && sessionId != null &&
                        (narrationRunning || currentSlide < slides.lastIndex || remainingMs == 0L)) {
                    Icon(if (autoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (autoPlaying) "Pause" else "Resume")
                }
                OutlinedButton(onClick = {
                    error = null
                    autoPlaying = true
                    replayNonce += 1
                }, enabled = !busy && !narrationRunning && sessionId != null) {
                    Icon(Icons.Default.RecordVoiceOver, null)
                    Text("Replay")
                }
                OutlinedButton(onClick = {
                    autoPlaying = false
                    currentSlide = (currentSlide + 1).coerceAtMost(slides.lastIndex)
                    visitedSlideIds = visitedSlideIds + slides[currentSlide].id
                    narrationStatus = "Next slide. Tap Resume to narrate."
                    error = null
                }, enabled = currentSlide < slides.lastIndex && !narrationRunning && !busy) {
                    Text("Next")
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            if (currentSlide == slides.lastIndex && !acknowledged) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text("I have reviewed the safety information and understand the instructions.",
                        fontSize = 12.sp, color = Dark)
                }
                Button(onClick = {
                    val id = sessionId ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val payload = JSONObject()
                                .put("understood", true)
                                .put("presented_slide_ids", JSONArray(slides.map { it.id }))
                            withContext(Dispatchers.IO) {
                                safetyHttp("/sessions/$id/acknowledge", "POST", payload)
                            }
                            if (existingSessionId != null) {
                                // Safety SQLite acknowledgement must succeed first.
                                val current = G1VisitorWelcomeApi.getStatus()
                                require(current.sessionId == id) { "Welcome session changed during acknowledgement" }
                                if (current.state == "safety_briefing") {
                                    G1VisitorWelcomeApi.advance(id, "briefing_completed")
                                }
                                G1VisitorWelcomeApi.advance(id, "acknowledge_safety")
                            }
                            acknowledged = true
                            onAcknowledged(id)
                        } catch (exception: Exception) {
                            error = exception.message ?: "Acknowledgement failed"
                        } finally { busy = false }
                    }
                }, enabled = understood && !busy && !narrationRunning &&
                        visitedSlideIds.containsAll(slides.map { it.id })) {
                    Text("I Understand · Record Acknowledgement")
                }
            }
            if (acknowledged) {
                Text("Acknowledgement recorded in G1 Studio. Tour and physical motion remain unauthorized until separately approved.",
                    color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ======================================================
// SHARED COMPONENTS
// ======================================================

@Composable
private fun ScreenTitle(
    title: String,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null)
            Spacer(Modifier.width(6.dp))
            Text("Back")
        }

        Spacer(Modifier.width(16.dp))

        Text(
            title,
            color = Dark,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = Mint,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                null,
                tint = Green
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text,
                color = Dark,
                fontSize = 12.sp
            )
        }
    }
}

// ======================================================
// LAB GUIDE AND DEMONSTRATIONS
// ======================================================

@Composable
private fun CatalogScreen(
    title: String,
    subtitle: String,
    items: List<VisitorItem>,
    onBack: () -> Unit,
    onSelect: (VisitorItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenTitle(title, onBack)

        Text(
            subtitle,
            color = Muted,
            fontSize = 13.sp
        )

        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(155.dp)
                            .clickable { onSelect(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(item.image),
                                contentDescription = item.title,
                                modifier = Modifier
                                    .width(155.dp)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    item.title,
                                    color = Dark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )

                                Text(
                                    item.subtitle,
                                    color = Muted,
                                    fontSize = 12.sp
                                )

                                Spacer(Modifier.height(5.dp))

                                Text(
                                    "View Details →",
                                    color = Green,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    item: VisitorItem,
    onBack: () -> Unit,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenTitle(item.title, onBack)

        Image(
            painter = painterResource(item.image),
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Text(
            item.subtitle,
            color = Green,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            item.description,
            color = Dark,
            fontSize = 14.sp
        )

        if (actionLabel != null) {
            Button(onClick = onAction) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }

        Notice(
            "This is a visitor interface preview. " +
                    "No physical robot commands are sent."
        )
    }
}

// ======================================================
// LAB MAP
// ======================================================

@Composable
private fun LabMapScreen(
    onBack: () -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenTitle("Building H – First Floor", onBack)
        Text(
            "HFU IPSE laboratory floor plan · Pinch to zoom and drag to explore",
            color = Muted,
            fontSize = 13.sp
        )

        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()

                Image(
                    painter = painterResource(R.drawable.hfu_building_h_floor1),
                    contentDescription = "Original HFU Building H first-floor evacuation and rescue plan",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                val maxX = (widthPx * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                val maxY = (heightPx * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                panX = (panX + pan.x).coerceIn(-maxX, maxX)
                                panY = (panY + pan.y).coerceIn(-maxY, maxY)
                                zoom = newZoom
                                if (zoom == 1f) {
                                    panX = 0f
                                    panY = 0f
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                        }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = {
                zoom = 1f
                panX = 0f
                panY = 0f
            }) { Text("Reset zoom") }
            Text("${(zoom * 100).toInt()}%", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("Floor plan · Not live navigation", color = Muted, fontSize = 12.sp)
        }
    }
}

// ======================================================
// LAB INFORMATION
// ======================================================

@Composable
private fun LabInfoScreen(
    onBack: () -> Unit,
    onWebsite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenTitle("Lab Information", onBack)

        Image(
            painter = painterResource(R.drawable.lab_info),
            contentDescription = "HFU Laboratory",
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )

        Text(
            INSTITUTE,
            color = Green,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Explore research, product development, " +
                    "robotics, smart systems and engineering " +
                    "activities at HFU IPSE.",
            color = Dark,
            fontSize = 14.sp
        )

        ExpandableSection(
            title = "Research and Development",
            description =
                "Learn about engineering projects, " +
                        "research activities and laboratory systems."
        )

        ExpandableSection(
            title = "Humanoid Robotics",
            description =
                "The Unitree G1 is being explored for " +
                        "visitor assistance, interaction and " +
                        "demonstration tasks."
        )

        ExpandableSection(
            title = "G1 Studio",
            description =
                "G1 Studio is the software platform " +
                        "being developed to support visitor " +
                        "interaction and supervised robot functions."
        )

        Button(onClick = onWebsite) {
            Icon(Icons.Default.Language, null)
            Spacer(Modifier.width(8.dp))
            Text("Open Official HFU Website")
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    description: String
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = Dark,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    null,
                    tint = Green
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                Text(
                    description,
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ======================================================
// HFU WEBSITE
// ======================================================

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FullScreenWebsite(
    onHome: () -> Unit,
    onBack: () -> Unit
) {
    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    webView = this
                    loadUrl(IPSE_URL)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp
        ) {
            Row {
                IconButton(
                    onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onBack()
                        }
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }

                IconButton(
                    onClick = { webView?.reload() }
                ) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }

                IconButton(onClick = onHome) {
                    Icon(Icons.Default.Home, "Home")
                }
            }
        }
    }
}

// ======================================================
// NAVIGATION PREVIEW
// ======================================================

@Composable
private fun NavigationPreview(
    onBack: () -> Unit
) {
    var previewStarted by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }

    val steps = listOf(
        "Ready",
        "Preparing",
        "Guiding",
        "Arrived",
        "Completed"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenTitle("Coffee Machine Navigation", onBack)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Visitor Guidance Preview",
                    color = Dark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Destination: Coffee Machine",
                    color = Green,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, title ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (index <= step)
                                Mint else Background,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    if (index < step)
                                        Icons.Default.CheckCircle
                                    else
                                        Icons.Default.RadioButtonUnchecked,
                                    null,
                                    tint = if (index <= step)
                                        Green else Muted
                                )

                                Text(
                                    title,
                                    fontSize = 11.sp,
                                    color = Dark
                                )
                            }
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { step / 4f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Green
                )

                Text(
                    "Current Stage: ${steps[step]}",
                    color = Green,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            previewStarted = true
                            step = (step + 1).coerceAtMost(4)
                        },
                        enabled = step < 4
                    ) {
                        Text(
                            if (!previewStarted)
                                "Start Preview"
                            else
                                "Next Preview Step"
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            previewStarted = false
                            step = 0
                        }
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        Notice(
            "Navigation simulation only. No walking, " +
                    "localization or robot commands are active."
        )
    }
}

// ======================================================
// OPERATOR CONTROL — UI SIMULATION
// ======================================================

private enum class JoystickMotion(
    val title: String,
    val vx: Float,
    val vy: Float,
    val yaw: Float
) {
    STOP("STOPPED", 0f, 0f, 0f),
    FORWARD("FORWARD", 0.15f, 0f, 0f),
    BACKWARD("BACKWARD", -0.15f, 0f, 0f),
    LEFT("LEFT", 0f, 0.10f, 0f),
    RIGHT("RIGHT", 0f, -0.10f, 0f),
    TURN_LEFT("ROTATE LEFT", 0f, 0f, 0.20f),
    TURN_RIGHT("ROTATE RIGHT", 0f, 0f, -0.20f)
}

@Composable
private fun G1RemoteOperatorPreviewScreen(
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var invalidPin by remember { mutableStateOf(false) }
    var motion by remember {
        mutableStateOf(JoystickMotion.STOP)
    }

    var previewFsm by remember { mutableIntStateOf(0) }

    fun stopMotion() {
        motion = JoystickMotion.STOP
    }

    DisposableEffect(Unit) {
        onDispose {
            motion = JoystickMotion.STOP
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    stopMotion()
                    onBack()
                }
            ) {
                Icon(Icons.Default.ArrowBack, null)
                Text("Back")
            }

            Spacer(Modifier.width(12.dp))

            Text(
                "G1 Operator Control",
                modifier = Modifier.weight(1f),
                color = Dark,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "OFFLINE · UI PREVIEW",
                color = Green,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            IconButton(
                onClick = {
                    stopMotion()
                    onHome()
                }
            ) {
                Icon(Icons.Default.Home, "Home", tint = Green)
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (unlocked) Icons.Default.LockOpen
                    else Icons.Default.Lock,
                    null,
                    tint = Green
                )

                Text(
                    if (unlocked)
                        "Operator Preview Unlocked"
                    else
                        "Operator Authentication",
                    modifier = Modifier.weight(1f),
                    color = Dark,
                    fontWeight = FontWeight.Bold
                )

                if (!unlocked) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it.filter(Char::isDigit).take(4)
                            invalidPin = false
                        },
                        modifier = Modifier.width(135.dp),
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = invalidPin,
                        visualTransformation =
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )

                    Button(
                        onClick = {
                            if (pin == "1234") {
                                unlocked = true
                                invalidPin = false
                            } else {
                                invalidPin = true
                                pin = ""
                            }
                        },
                        enabled = pin.length == 4
                    ) {
                        Text("Unlock")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            stopMotion()
                            unlocked = false
                            previewFsm = 0
                            pin = ""
                        }
                    ) {
                        Icon(Icons.Default.Lock, null)
                        Text("Lock")
                    }
                }
            }
        }

        if (invalidPin) {
            Text(
                "Incorrect PIN",
                color = Color.Red,
                fontSize = 12.sp
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Robot FSM Modes — Preview",
                    color = Dark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Zero Torque",
                        1 to "Damp",
                        4 to "Stand Up",
                        500 to "Locomotion"
                    ).forEach { (fsm, label) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (previewFsm == fsm)
                                Mint else Background,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (previewFsm == fsm)
                                    Green else BorderColor
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "FSM $fsm",
                                    color = Green,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )

                                Text(
                                    label,
                                    color = Dark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Text(
                    "Preview sequence: FSM 0 → 1 → 4 → 500",
                    color = Muted,
                    fontSize = 11.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            stopMotion()
                            previewFsm = 1
                        },
                        enabled = unlocked && previewFsm == 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FSM 1 · Damp")
                    }

                    Button(
                        onClick = {
                            stopMotion()
                            previewFsm = 4
                        },
                        enabled = unlocked && previewFsm == 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FSM 4 · Stand")
                    }

                    Button(
                        onClick = {
                            stopMotion()
                            previewFsm = 500
                        },
                        enabled = unlocked && previewFsm == 4,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FSM 500 · Locomotion")
                    }

                    OutlinedButton(
                        onClick = {
                            stopMotion()
                            previewFsm = 0
                        },
                        enabled = unlocked
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        val joystickEnabled =
            unlocked && previewFsm == 500

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1.3f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Walking Joystick",
                        color = Dark,
                        fontWeight = FontWeight.Bold
                    )

                    HoldJoystickButton(
                        "Forward",
                        Icons.Default.ArrowUpward,
                        joystickEnabled,
                        motion == JoystickMotion.FORWARD,
                        { motion = JoystickMotion.FORWARD },
                        { stopMotion() }
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HoldJoystickButton(
                            "Left",
                            Icons.Default.ArrowBack,
                            joystickEnabled,
                            motion == JoystickMotion.LEFT,
                            { motion = JoystickMotion.LEFT },
                            { stopMotion() }
                        )

                        Button(
                            onClick = { stopMotion() },
                            modifier = Modifier
                                .width(100.dp)
                                .height(64.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB3261E)
                            )
                        ) {
                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Stop, null)
                                Text("STOP", fontSize = 11.sp)
                            }
                        }

                        HoldJoystickButton(
                            "Right",
                            Icons.Default.ArrowForward,
                            joystickEnabled,
                            motion == JoystickMotion.RIGHT,
                            { motion = JoystickMotion.RIGHT },
                            { stopMotion() }
                        )
                    }

                    HoldJoystickButton(
                        "Backward",
                        Icons.Default.ArrowDownward,
                        joystickEnabled,
                        motion == JoystickMotion.BACKWARD,
                        { motion = JoystickMotion.BACKWARD },
                        { stopMotion() }
                    )
                }
            }

            Card(
                modifier = Modifier.weight(0.8f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Rotation",
                        color = Dark,
                        fontWeight = FontWeight.Bold
                    )

                    HoldJoystickButton(
                        "Rotate Left",
                        Icons.Default.RotateLeft,
                        joystickEnabled,
                        motion == JoystickMotion.TURN_LEFT,
                        { motion = JoystickMotion.TURN_LEFT },
                        { stopMotion() },
                        wide = true
                    )

                    HoldJoystickButton(
                        "Rotate Right",
                        Icons.Default.RotateRight,
                        joystickEnabled,
                        motion == JoystickMotion.TURN_RIGHT,
                        { motion = JoystickMotion.TURN_RIGHT },
                        { stopMotion() },
                        wide = true
                    )

                    Text(
                        "Press and hold",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Card(
                modifier = Modifier.weight(0.85f),
                colors = CardDefaults.cardColors(
                    containerColor = Mint
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Motion Preview",
                        color = Dark,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        motion.title,
                        color = Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Text("vx: %.2f m/s".format(motion.vx))
                    Text("vy: %.2f m/s".format(motion.vy))
                    Text("yaw: %.2f rad/s".format(motion.yaw))

                    HorizontalDivider()

                    Text(
                        "Actual robot FSM: Unknown",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    Text(
                        "Robot: Not connected",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    Text(
                        "Physical motion: DISABLED",
                        color = Color(0xFF9D4B00),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Notice(
            "Demo PIN 1234 unlocks the UI only. " +
                    "No commands are sent to G1. " +
                    "On-screen STOP is not an emergency stop."
        )
    }
}

// ======================================================
// PRESS-AND-HOLD JOYSTICK BUTTON
// ======================================================

@Composable
private fun HoldJoystickButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    active: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    wide: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)

    val backgroundColor = when {
        !enabled -> Color(0xFFE6E9E7)
        active -> Green
        else -> Mint
    }

    val foregroundColor = when {
        !enabled -> Color.Gray
        active -> Color.White
        else -> Green
    }

    val latestPress by rememberUpdatedState(onPress)
    val latestRelease by rememberUpdatedState(onRelease)

    Column(
        modifier = Modifier
            .width(if (wide) 155.dp else 96.dp)
            .height(64.dp)
            .background(backgroundColor, shape)
            .border(1.dp, BorderColor, shape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            latestPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                latestRelease()
                            }
                        }
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = foregroundColor
        )

        Text(
            title,
            color = foregroundColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ======================================================
// NEW: TALK TO G1
// ======================================================

@Composable
private fun TalkToG1Screen(
    language: AppLanguage,
    onBack: () -> Unit,
    onStartSafety: () -> Unit,
    onGuidanceStarted: (String) -> Unit,
    sessionId: String?,
    onSessionIdChange: (String?) -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to listen") }
    var messages by remember {
        mutableStateOf(listOf("G1" to "Hello! I'm G1. How can I help you?"))
    }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var requesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun showVisitorText(text: String) {
        if (text.isBlank()) return
        val spoken = text.trim()
        if (requesting) return
        messages = messages + ("Visitor" to spoken)
        message = ""
        val normalized = spoken.lowercase(Locale.ROOT)
        if (listOf(
                "safety", "safety instructions", "safety briefing", "safety rules",
                "lab rules", "laboratory rules", "safe in the lab",
                "sicherheit", "sicherheitsunterweisung", "sicherheitsregeln",
                "laborregeln", "sicherheitsanweisungen"
            ).any { normalized.contains(it) }) {
            status = "Opening G1 safety presentation"
            onStartSafety()
            return
        }
        if (isCoffeeGuidanceRequest(normalized)) {
            requesting = true
            status = "Starting coffee-machine guidance..."
            scope.launch {
                try {
                    val guidanceSessionId = withContext(Dispatchers.IO) {
                        startCoffeeGuidance()
                    }
                    messages = messages + ("G1" to
                            "Coffee-machine guidance started. Opening navigation display.")
                    status = "Guidance started"
                    onGuidanceStarted(guidanceSessionId)
                } catch (error: Exception) {
                    status = "Could not start guidance"
                    messages = messages + ("System" to
                            "Guidance unavailable: ${error.message}")
                } finally {
                    requesting = false
                }
            }
            return
        }
        requesting = true
        status = "G1 is thinking..."
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    askG1Backend(spoken, sessionId)
                }
                onSessionIdChange(result.sessionId)
                messages = messages + ("G1" to result.answer)
                status = if (result.speechAccepted) "G1 speech sent to robot" else
                    "Answer received; robot speech unavailable"
            } catch (error: Exception) {
                status = "AI request failed"
                messages = messages + ("System" to
                        "Could not contact G1 Studio: ${error.message}")
            } finally {
                requesting = false
            }
        }
    }

    fun startNewConversation() {
        if (requesting || listening) return
        requesting = true
        status = "Starting a new conversation..."
        scope.launch {
            try {
                val previousSession = sessionId
                if (previousSession != null) {
                    withContext(Dispatchers.IO) { resetG1Backend(previousSession) }
                }
                onSessionIdChange(null)
                messages = listOf("G1" to "Hello! I'm G1. How can I help you?")
                message = ""
                status = "New conversation ready"
            } catch (error: Exception) {
                status = "Could not reset conversation"
                messages = messages + ("System" to
                        "Session reset failed: ${error.message}")
            } finally {
                requesting = false
            }
        }
    }

    val latestShowVisitorText by rememberUpdatedState<(String) -> Unit>(::showVisitorText)

    val speechIntent = remember(language) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                if (language == AppLanguage.DE) "de-DE" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    DisposableEffect(context) {
        var speechRecognizerForCleanup: SpeechRecognizer? = null
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    status = "Listening... speak now"
                }
                override fun onBeginningOfSpeech() { status = "Hearing your voice..." }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status = "Processing speech..." }
                override fun onError(error: Int) {
                    listening = false
                    status = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        else -> "Speech recognition error: $error"
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val recognized = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!recognized.isNullOrBlank()) {
                        status = "Speech recognized"
                        latestShowVisitorText(recognized)
                    } else status = "No words recognized"
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer = speechRecognizer
            speechRecognizerForCleanup = speechRecognizer
        } else {
            status = "No Android speech recognition service available"
        }
        onDispose {
            speechRecognizerCleanup(speechRecognizerForCleanup)
            recognizer = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                recognizer?.startListening(speechIntent)
                status = "Starting microphone..."
            } catch (e: Exception) {
                status = "Could not start recognition: ${e.message}"
            }
        } else status = "Microphone permission denied"
    }

    fun startListening() {
        if (recognizer == null) {
            status = "Speech recognition is unavailable on this device"
            return
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED) {
            try {
                recognizer?.startListening(speechIntent)
                status = "Starting microphone..."
            } catch (e: Exception) {
                status = "Could not start recognition: ${e.message}"
            }
        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenTitle(tr(language, "Talk to G1", "Mit G1 sprechen"), onBack)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.weight(0.9f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Mic, null, tint = Green, modifier = Modifier.size(55.dp))
                    Text(tr(language, "G1 Voice Assistant", "G1 Sprachassistent"),
                        color = Dark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = Green, fontSize = 12.sp)
                    Button(
                        onClick = {
                            if (listening) {
                                recognizer?.stopListening()
                                listening = false
                                status = "Processing speech..."
                            } else startListening()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !requesting
                    ) {
                        Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (listening) "Stop Listening" else "Start Listening")
                    }
                    Text("Speak to your tablet. Recognized speech appears in the conversation.",
                        color = Muted, fontSize = 12.sp)
                }
            }
            Card(
                modifier = Modifier.weight(1.4f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Conversation", "Unterhaltung"),
                            modifier = Modifier.weight(1f),
                            color = Dark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { startNewConversation() },
                            enabled = !requesting && !listening
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(tr(language, "New Conversation", "Neues Gespräch"),
                                fontSize = 11.sp)
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        messages.forEach { (speaker, content) ->
                            Surface(
                                color = if (speaker == "G1") Mint else Background,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(speaker, color = Green,
                                        fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(content, color = Dark, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text(tr(language, "Type a message", "Nachricht eingeben")) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showVisitorText(message) }, enabled = message.isNotBlank() && !requesting) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
        Notice("G1 Studio AI connected through /api/interaction/ask. Replies are sent to the physical G1 speaker when available. Safety presentation remains a separate preview.")
    }
}

// Recognize explicit coffee-machine guidance requests only.
// General questions about coffee still go to the AI conversation endpoint.
private fun isCoffeeGuidanceRequest(text: String): Boolean {
    val normalized = text.lowercase(Locale.ROOT)
    val coffee = listOf("coffee machine", "coffee maker", "coffeemachine",
        "kaffeemaschine", "kaffeeautomat", "kaffee maschine")
        .any { normalized.contains(it) }
    val guidance = listOf("guide", "take me", "show me", "lead me", "bring me",
        "navigate", "walk me", "way to", "directions to", "where is",
        "führ", "fuehr", "zeig", "bring mich", "begleit", "weg zur",
        "wo ist").any { normalized.contains(it) }
    return coffee && guidance
}

// Visitor intent starts the existing simulation-only Task Engine workflow.
// The backend validates destination, robot mode, and permitted actions.
private fun startCoffeeGuidance(): String {
    val connection = (URL("http://10.150.68.160:8000/api/visitor-guidance/start")
        .openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val payload = JSONObject().put("destination", "coffee_machine")
        connection.outputStream.use {
            it.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${body.take(250)}")
        }
        val session = JSONObject(body).optString("session_id")
        require(session.isNotBlank()) { "Guidance started without a session ID" }
        return session
    } finally {
        connection.disconnect()
    }
}

private fun speechRecognizerCleanup(recognizer: SpeechRecognizer?) {
    recognizer?.cancel()
    recognizer?.destroy()
}

// Uses the existing FastAPI interaction endpoint. No robot motion commands.
private data class G1AskResult(
    val answer: String,
    val speechAccepted: Boolean,
    val sessionId: String
)

private fun askG1Backend(question: String, sessionId: String?): G1AskResult {
    val connection = (URL("http://10.150.68.160:8000/api/interaction/ask")
        .openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 140000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val payload = JSONObject().put("question", question).put("speak", true)
        if (!sessionId.isNullOrBlank()) payload.put("session_id", sessionId)
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(250)}")
        val result = JSONObject(body)
        return G1AskResult(
            answer = result.getString("answer"),
            speechAccepted = result.optBoolean("speech_accepted", false),
            sessionId = result.getString("session_id")
        )
    } finally {
        connection.disconnect()
    }
}

// Resets only the visitor's conversational context, never robot motion.
private fun resetG1Backend(sessionId: String) {
    val connection = (URL("http://10.150.68.160:8000/api/interaction/reset")
        .openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val payload = JSONObject().put("session_id", sessionId)
        connection.outputStream.use {
            it.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: ${body.take(250)}")
        }
        if (!JSONObject(body).optBoolean("reset", false)) {
            throw IllegalStateException("The session was not reset by the backend")
        }
    } finally {
        connection.disconnect()
    }
}
