package com.example.g1remote

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// App colours (unchanged)
private val M3Green = Color(0xFF087B39)
private val M3Mint = Color(0xFFE7F5EC)
private val M3Dark = Color(0xFF172820)
private val M3Muted = Color(0xFF64756B)
private val M3Gold = Color(0xFFD79616)
private val M3Line = Color(0xFFE1E8E4)

// Height bands (floor -> ceiling), shades of the app green.
private val BAND_TOPS = floatArrayOf(0.15f, 0.6f, 1.2f, 1.8f, 99f)
private val BAND_COLORS = listOf(
    Color(0xFFC9D8CF), // floor
    Color(0xFF9CC8AE),
    Color(0xFF5FA87C),
    Color(0xFF087B39), // app green
    Color(0xFF2E4A3B)
)
private val BAND_NAMES_EN = listOf("Floor", "0.2–0.6 m", "0.6–1.2 m", "1.2–1.8 m", "above 1.8 m")
private val BAND_NAMES_DE = listOf("Boden", "0,2–0,6 m", "0,6–1,2 m", "1,2–1,8 m", "über 1,8 m")

// ======================================================================
// Data from G1 Studio (port 8000)
// ======================================================================
object LabMap3DApi {
    class Cloud(
        val bands: List<FloatArray>,      // xyz triples per height band
        val source: String,               // "lidar" or "2d_map"
        val count: Int,
        val minX: Float, val maxX: Float, val minY: Float, val maxY: Float
    ) {
        val screen: List<FloatArray> = bands.map { FloatArray(it.size / 3 * 2) }
    }

    data class Pose(val x: Float, val y: Float, val yaw: Float, val ageS: Double?, val route: String?, val state: String?)
    data class Stop(val title: String, val x: Float, val y: Float)
    data class Route(val name: String, val title: String, val isTour: Boolean, val path: FloatArray, val stops: List<Stop>)

    private fun get(path: String, timeout: Int = 5000): HttpURLConnection {
        val conn = URL("${G1Host.url(8000)}$path").openConnection() as HttpURLConnection
        conn.connectTimeout = timeout
        conn.readTimeout = 20000
        return conn
    }

    suspend fun loadCloud(): Cloud = withContext(Dispatchers.IO) {
        val conn = get("/api/map/cloud3d.bin")
        try {
            if (conn.responseCode != 200) error("Studio answered ${conn.responseCode}")
            val source = conn.getHeaderField("X-Source") ?: "lidar"
            val bytes = conn.inputStream.use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            if (magic != "G1C3") error("Unknown map format")
            buf.position(4)
            val n = buf.int
            val xs = FloatArray(n); val ys = FloatArray(n); val zs = FloatArray(n)
            val bandOf = IntArray(n)
            val counts = IntArray(BAND_TOPS.size)
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until n) {
                val x = buf.short / 100f; val y = buf.short / 100f; val z = buf.short / 100f
                xs[i] = x; ys[i] = y; zs[i] = z
                var b = 0
                while (z >= BAND_TOPS[b]) b++
                bandOf[i] = b; counts[b]++
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            }
            val bands = counts.map { FloatArray(it * 3) }
            val fill = IntArray(BAND_TOPS.size)
            for (i in 0 until n) {
                val b = bandOf[i]; val k = fill[b]
                bands[b][k] = xs[i]; bands[b][k + 1] = ys[i]; bands[b][k + 2] = zs[i]
                fill[b] = k + 3
            }
            Cloud(bands, source, n, minX, maxX, minY, maxY)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun live(): Pose? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = get("/api/map/live", 2500)
            try {
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                val p = o.optJSONObject("pose") ?: return@runCatching null
                Pose(
                    p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("yaw").toFloat(),
                    if (p.isNull("age_s")) null else p.optDouble("age_s"),
                    p.optString("route").ifBlank { null }, p.optString("state").ifBlank { null }
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun routes(german: Boolean): List<Route> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = get("/api/map/routes")
            try {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                (0 until arr.length()).mapNotNull { i ->
                    val r = arr.getJSONObject(i)
                    val path = r.optJSONArray("path") ?: return@mapNotNull null
                    val pts = FloatArray(path.length() * 2)
                    for (k in 0 until path.length()) {
                        val p = path.getJSONArray(k)
                        pts[2 * k] = p.getDouble(0).toFloat(); pts[2 * k + 1] = p.getDouble(1).toFloat()
                    }
                    val stopsJ = r.optJSONArray("stops") ?: JSONArray()
                    val stops = (0 until stopsJ.length()).mapNotNull { k ->
                        val s = stopsJ.getJSONObject(k)
                        val m = s.optJSONArray("map") ?: return@mapNotNull null
                        val t = (if (german) s.optString("title_de") else "").ifBlank { s.optString("title_en") }
                            .ifBlank { s.optString("name") }
                        Stop(t, m.getDouble(0).toFloat(), m.getDouble(1).toFloat())
                    }
                    val title = (if (german) r.optString("title_de") else "").ifBlank { r.optString("title_en") }
                        .ifBlank { r.optString("name").replace('_', ' ') }
                    Route(r.optString("name"), title, r.optString("kind") == "tour", pts, stops)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(emptyList())
    }
}

// ======================================================================
// Orbit camera: yaw around the vertical axis, pitch 0 = side view, 90° = top view
// ======================================================================
private class OrbitCamera(
    val tx: Float, val ty: Float, val tz: Float,
    yaw: Float, pitch: Float,
    val zoomPx: Float, val w2: Float, val h2: Float
) {
    private val cy = cos(yaw); private val sy = sin(yaw)
    private val cp = cos(pitch); private val sp = sin(pitch)
    private val dist = 45f

    /** Writes screen x,y into out[i], out[i+1]; false if behind the camera. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray, i: Int): Boolean {
        val dx = x - tx; val dy = y - ty; val dz = z - tz
        val xr = dx * cy + dy * sy
        val yr = -dx * sy + dy * cy
        val up = yr * sp + dz * cp
        val depth = yr * cp - dz * sp
        val d = dist + depth
        if (d < 1f) return false
        val s = zoomPx * dist / d
        out[i] = w2 + xr * s
        out[i + 1] = h2 - up * s
        return true
    }
}

// ======================================================================
// Screen
// ======================================================================
@Composable
fun LabMap3DScreen(
    german: Boolean,
    floorPlan: @Composable () -> Unit
) {
    var show3d by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (german) "Laborkarte" else "Lab Map", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = M3Dark)
                Text(
                    if (german) "3D-LiDAR-Scan des Labors · G1-Position live"
                    else "3D LiDAR scan of the lab · live G1 position",
                    fontSize = 13.sp, color = M3Muted
                )
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(M3Mint)) {
                listOf(true to (if (german) "3D LiDAR" else "3D LiDAR"), false to (if (german) "Grundriss" else "Floor plan"))
                    .forEach { (is3d, label) ->
                        Box(
                            Modifier
                                .background(if (show3d == is3d) M3Green else Color.Transparent)
                                .clickable { show3d = is3d }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(label, color = if (show3d == is3d) Color.White else M3Dark, fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (show3d) LidarMapView(german) else floorPlan()
        }
    }
}

@Composable
private fun LidarMapView(german: Boolean) {
    var cloud by remember { mutableStateOf<LabMap3DApi.Cloud?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var pose by remember { mutableStateOf<LabMap3DApi.Pose?>(null) }
    var routes by remember { mutableStateOf<List<LabMap3DApi.Route>>(emptyList()) }
    var selectedRoute by remember { mutableStateOf<String?>(null) }
    val routesVersion by G1StudioSync.routesVersion.collectAsState()

    // camera state
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0.95f) }
    var zoomPx by remember { mutableFloatStateOf(0f) }    // 0 = fit on first draw
    var follow by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        loadError = null
        try {
            cloud = LabMap3DApi.loadCloud()
            cloud?.let { tx = (it.minX + it.maxX) / 2; ty = (it.minY + it.maxY) / 2 }
            zoomPx = 0f
        } catch (e: Exception) {
            loadError = e.message ?: "Studio not reachable"
        }
    }
    LaunchedEffect(routesVersion, german) {
        routes = LabMap3DApi.routes(german)
        if (selectedRoute == null || routes.none { it.name == selectedRoute }) {
            selectedRoute = (routes.firstOrNull { it.isTour } ?: routes.firstOrNull())?.name
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            pose = LabMap3DApi.live()
            delay(1000)
        }
    }
    LaunchedEffect(pose, follow) {
        val p = pose
        if (follow && p != null) { tx = p.x; ty = p.y }
    }
    // show the G1's own route when it is guiding
    LaunchedEffect(pose?.route) {
        pose?.route?.let { r -> if (routes.any { it.name == r }) selectedRoute = r }
    }

    val pulse by rememberInfiniteTransition(label = "g1").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "pulse"
    )

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // ------------------------------------------------ 3D view
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                val c = cloud
                if (c == null) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loadError == null) {
                            CircularProgressIndicator(color = M3Green)
                            Spacer(Modifier.height(10.dp))
                            Text(if (german) "3D-Karte wird geladen…" else "Loading 3D map…", color = M3Muted)
                        } else {
                            Text(if (german) "3D-Karte nicht verfügbar" else "3D map not available",
                                color = M3Dark, fontWeight = FontWeight.Bold)
                            Text(loadError ?: "", color = M3Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { reloadKey++ }, colors = ButtonDefaults.buttonColors(containerColor = M3Green)) {
                                Text(if (german) "Erneut versuchen" else "Try again")
                            }
                        }
                    }
                } else {
                    val paints = remember {
                        BAND_COLORS.map { col ->
                            android.graphics.Paint().apply {
                                color = col.toArgb(); isAntiAlias = false
                                strokeCap = android.graphics.Paint.Cap.SQUARE
                            }
                        }
                    }
                    val label = remember {
                        android.graphics.Paint().apply {
                            isAntiAlias = true; textSize = 30f; color = M3Dark.toArgb()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    }
                    val halo = remember {
                        android.graphics.Paint().apply {
                            isAntiAlias = true; textSize = 30f; color = android.graphics.Color.WHITE
                            style = android.graphics.Paint.Style.STROKE; strokeWidth = 8f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    }
                    val tmp = remember { FloatArray(6) }

                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val fingers = event.changes.count { it.pressed }
                                        if (fingers >= 2) {
                                            // two fingers: move + pinch zoom + twist to rotate
                                            val z = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            val rot = event.calculateRotation()
                                            zoomPx = (zoomPx * z).coerceIn(3f, 600f)
                                            yaw -= rot * (PI.toFloat() / 180f)
                                            if (pan.x != 0f || pan.y != 0f) {
                                                follow = false
                                                val s = zoomPx.coerceAtLeast(1f)
                                                val k = 1f / max(sin(pitch), 0.35f)
                                                tx += (-pan.x * cos(yaw) - pan.y * k * sin(yaw)) / s
                                                ty += (-pan.x * sin(yaw) + pan.y * k * cos(yaw)) / s
                                            }
                                        } else if (fingers == 1) {
                                            // one finger: orbit around the lab
                                            val pan = event.calculatePan()
                                            yaw -= pan.x * 0.006f
                                            pitch = (pitch + pan.y * 0.005f).coerceIn(0.12f, (PI / 2).toFloat())
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    ) {
                        if (zoomPx <= 0f) {
                            val sx = size.width / (c.maxX - c.minX + 2f)
                            val sy = size.height / (c.maxY - c.minY + 2f)
                            zoomPx = min(sx, sy) * 1.25f
                        }
                        val cam = OrbitCamera(tx, ty, 0f, yaw, pitch, zoomPx, size.width / 2, size.height / 2)
                        val pointSize = (zoomPx * 0.09f).coerceIn(1.5f, 7f)

                        drawIntoCanvas { canvas ->
                            val nc = canvas.nativeCanvas
                            // point cloud, floor first
                            c.bands.forEachIndexed { b, xyz ->
                                val out = c.screen[b]
                                var n = 0
                                var i = 0
                                val w = size.width; val h = size.height
                                while (i < xyz.size) {
                                    if (cam.project(xyz[i], xyz[i + 1], xyz[i + 2], out, n)) {
                                        val sx = out[n]; val sy = out[n + 1]
                                        if (sx > -5 && sx < w + 5 && sy > -5 && sy < h + 5) n += 2
                                    }
                                    i += 3
                                }
                                paints[b].strokeWidth = pointSize
                                if (n > 0) nc.drawPoints(out, 0, n, paints[b])
                            }
                        }

                        // selected route / tour path
                        val route = routes.firstOrNull { it.name == selectedRoute }
                        if (route != null && route.path.size >= 4) {
                            val path = Path()
                            var started = false
                            var k = 0
                            while (k < route.path.size) {
                                if (cam.project(route.path[k], route.path[k + 1], 0.05f, tmp, 0)) {
                                    if (!started) { path.moveTo(tmp[0], tmp[1]); started = true }
                                    else path.lineTo(tmp[0], tmp[1])
                                }
                                k += 2
                            }
                            drawPath(path, Color.White, style = Stroke(width = 10f, cap = StrokeCap.Round))
                            drawPath(path, M3Green, style = Stroke(width = 5f, cap = StrokeCap.Round))
                            route.stops.forEachIndexed { idx, st ->
                                if (cam.project(st.x, st.y, 0.05f, tmp, 0)) {
                                    val o = Offset(tmp[0], tmp[1])
                                    drawCircle(Color.White, 17f, o)
                                    drawCircle(M3Gold, 13f, o)
                                    drawIntoCanvas {
                                        val t = "${idx + 1}. ${st.title}"
                                        it.nativeCanvas.drawText(t, o.x + 20f, o.y - 12f, halo)
                                        it.nativeCanvas.drawText(t, o.x + 20f, o.y - 12f, label)
                                    }
                                }
                            }
                        }

                        // live G1
                        val p = pose
                        if (p != null && cam.project(p.x, p.y, 0f, tmp, 0) && cam.project(p.x, p.y, 1.32f, tmp, 2)) {
                            val base = Offset(tmp[0], tmp[1]); val head = Offset(tmp[2], tmp[3])
                            drawCircle(M3Green.copy(alpha = 0.35f * (1f - pulse)), 18f + 46f * pulse, base)
                            if (cam.project(p.x + 0.9f * cos(p.yaw), p.y + 0.9f * sin(p.yaw), 0f, tmp, 4)) {
                                drawLine(M3Gold, base, Offset(tmp[4], tmp[5]), strokeWidth = 7f, cap = StrokeCap.Round)
                            }
                            drawLine(Color.White, base, head, strokeWidth = 14f, cap = StrokeCap.Round)
                            drawLine(M3Green, base, head, strokeWidth = 8f, cap = StrokeCap.Round)
                            drawCircle(Color.White, 15f, head)
                            drawCircle(M3Green, 11f, head)
                            drawCircle(Color.White, 14f, base)
                            drawCircle(M3Green, 10f, base)
                            drawIntoCanvas {
                                it.nativeCanvas.drawText("G1", head.x + 18f, head.y + 10f, halo)
                                it.nativeCanvas.drawText("G1", head.x + 18f, head.y + 10f, label)
                            }
                        }
                    }

                    // view controls
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        MapButton(Icons.Default.Add, if (german) "Näher" else "Zoom in") { zoomPx = (zoomPx * 1.3f).coerceAtMost(600f) }
                        MapButton(Icons.Default.Remove, if (german) "Weiter" else "Zoom out") { zoomPx = (zoomPx / 1.3f).coerceAtLeast(3f) }
                        MapTextButton(if (pitch > 1.45f) "3D" else (if (german) "Draufsicht" else "Top view")) {
                            pitch = if (pitch > 1.45f) 0.95f else (PI / 2).toFloat()
                        }
                        MapTextButton(if (german) "Zurücksetzen" else "Reset") {
                            follow = false; yaw = 0f; pitch = 0.95f; zoomPx = 0f
                            tx = (c.minX + c.maxX) / 2; ty = (c.minY + c.maxY) / 2
                        }
                        MapTextButton(if (german) "G1 folgen" else "Follow G1", active = follow, enabled = pose != null) {
                            follow = !follow
                            if (follow) pose?.let { zoomPx = max(zoomPx, 60f) }
                        }
                    }
                    Text(
                        if (german) "1 Finger: drehen · 2 Finger: verschieben / zoomen"
                        else "1 finger: rotate · 2 fingers: move / zoom",
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = M3Muted, fontSize = 12.sp
                    )
                }
            }
        }

        // ------------------------------------------------ side panel
        Column(
            Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SideCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (pose != null) M3Green else M3Muted))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (pose != null) (if (german) "G1 live auf der Karte" else "G1 live on the map")
                        else (if (german) "G1-Position nicht verfügbar" else "G1 position not available"),
                        fontWeight = FontWeight.Bold, color = M3Dark, fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                val p = pose
                Text(
                    if (p != null) {
                        val st = when (p.state) {
                            "running", "following" -> if (german) "G1 führt gerade" else "G1 is guiding"
                            "paused", "blocked" -> if (german) "G1 wartet" else "G1 is waiting"
                            else -> if (german) "G1 ist bereit" else "G1 is ready"
                        }
                        "$st · x ${"%.1f".format(p.x)} m, y ${"%.1f".format(p.y)} m"
                    } else if (german) "Die Position erscheint, sobald der Lab-Guide-Modus läuft."
                    else "The position appears when Lab Guide mode is running.",
                    color = M3Muted, fontSize = 12.sp
                )
            }

            if (routes.isNotEmpty()) {
                SideCard {
                    Text(if (german) "Touren & Wege" else "Tours & routes", fontWeight = FontWeight.Bold, color = M3Dark)
                    Spacer(Modifier.height(6.dp))
                    routes.forEach { r ->
                        val sel = r.name == selectedRoute
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (sel) M3Mint else Color.Transparent)
                                .clickable { selectedRoute = if (sel) null else r.name }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (r.isTour) Icons.Default.Tour else Icons.Default.Timeline, null,
                                tint = if (sel) M3Green else M3Muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(r.title, color = if (sel) M3Green else M3Dark,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        }
                    }
                    routes.firstOrNull { it.name == selectedRoute }?.stops?.takeIf { it.isNotEmpty() }?.let { stops ->
                        Spacer(Modifier.height(6.dp))
                        stops.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(M3Gold), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(s.title, color = M3Dark, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            SideCard {
                Text(if (german) "Höhe" else "Height", fontWeight = FontWeight.Bold, color = M3Dark)
                Spacer(Modifier.height(6.dp))
                BAND_COLORS.forEachIndexed { i, col ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(col))
                        Spacer(Modifier.width(8.dp))
                        Text(if (german) BAND_NAMES_DE[i] else BAND_NAMES_EN[i], color = M3Muted, fontSize = 12.sp)
                    }
                }
                cloud?.let { c ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (c.source == "lidar")
                            (if (german) "LiDAR-Scan (Session 03) · ${"%,d".format(c.count)} Punkte"
                            else "LiDAR scan (Session 03) · ${"%,d".format(c.count)} points")
                        else (if (german) "Vorschau aus der 2D-Karte" else "Preview from the 2D map"),
                        color = M3Muted, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SideCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, M3Line, RoundedCornerShape(14.dp)).padding(14.dp),
        content = content
    )
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.White).border(1.dp, M3Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = M3Green) }
}

@Composable
private fun MapTextButton(text: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(24.dp))
            .background(if (active) M3Green else Color.White)
            .border(1.dp, if (active) M3Green else M3Line, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = when {
            !enabled -> M3Muted.copy(alpha = 0.5f)
            active -> Color.White
            else -> M3Green
        }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
