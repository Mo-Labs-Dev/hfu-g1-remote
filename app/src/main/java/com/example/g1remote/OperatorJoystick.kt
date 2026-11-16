package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val OperatorGreen = Color(0xFF087B39)
private val OperatorMint = Color(0xFFE7F5EC)
private val OperatorDark = Color(0xFF172820)
private val OperatorMuted = Color(0xFF64756B)
private val OperatorBackground = Color(0xFFF7F9F8)
private val StopRed = Color(0xFFB3261E)

private const val SEND_PERIOD_MS = 100L      // 10 Hz (server dead-man: 300 ms)
private const val RECONNECT_PERIOD_MS = 1000L
private const val SLOW_SCALE = 0.5f

/**
 * Real operator joystick for the G1.
 *
 * Left stick: walk (up = forward, sideways = strafe).
 * Right stick: turn (horizontal only).
 * Releasing a stick sends zero; leaving the screen stops and locks.
 * All limits and safety checks are enforced on the robot server.
 */
@Composable
fun OperatorJoystickScreen(
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val client = remember { G1TeleopClient() }
    val scope = rememberCoroutineScope()

    val status by client.status.collectAsState()
    val connection by client.connection.collectAsState()
    val message by client.message.collectAsState()
    val token by client.token.collectAsState()
    val unlocked = token != null

    var pin by remember { mutableStateOf("") }
    var operatorName by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var slowMode by remember { mutableStateOf(true) }

    // Raw stick values (-1..1). nx: right+, ny: up+.
    var walkX by remember { mutableStateOf(0f) }
    var walkY by remember { mutableStateOf(0f) }
    var turnX by remember { mutableStateOf(0f) }

    // Robot mode change (damp / stand_up / walk) waiting for confirmation.
    var pendingMode by remember { mutableStateOf<String?>(null) }
    var modeBusy by remember { mutableStateOf(false) }

    // Poll status while locked (who has control, robot FSM).
    LaunchedEffect(unlocked) {
        while (isActive && client.token.value == null) {
            client.refreshStatus()
            delay(1000L)
        }
    }

    // Stream joystick at 10 Hz while unlocked. Zeros are sent too,
    // which keeps the dead-man alive and lets the robot slow down smoothly.
    LaunchedEffect(unlocked) {
        if (!unlocked) return@LaunchedEffect
        var lastConnectAttempt = 0L
        while (isActive && client.token.value != null) {
            val now = System.currentTimeMillis()
            if (client.connection.value == TeleopConnection.DISCONNECTED &&
                now - lastConnectAttempt > RECONNECT_PERIOD_MS
            ) {
                lastConnectAttempt = now
                client.connect()
            }
            client.sendCommand(
                x = walkY,          // stick up    -> forward
                y = -walkX,         // stick right -> move right (robot -y)
                yaw = -turnX,       // stick right -> turn right (robot -yaw)
                scale = if (slowMode) SLOW_SCALE else 1f
            )
            delay(SEND_PERIOD_MS)
        }
    }

    // Leaving the screen: stop and release control.
    DisposableEffect(Unit) {
        onDispose {
            CoroutineScope(Dispatchers.IO).launch { client.lock() }
        }
    }

    val connected = connection == TeleopConnection.CONNECTED
    val sticksEnabled = unlocked && connected && status?.estop != true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorBackground)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---------------- Header ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Back")
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "G1 Operator Joystick",
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OperatorDark
            )
            ConnectionChip(unlocked = unlocked, connection = connection)
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = onHome) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = OperatorGreen)
            }
        }

        // ---------------- Unlock / session bar ----------------
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = OperatorGreen,
                    modifier = Modifier.size(28.dp)
                )

                if (!unlocked) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Operator PIN required",
                            color = OperatorDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        val holder = status?.operator
                        Text(
                            if (status?.teleopActive == true && holder != null)
                                "Currently controlled by: $holder"
                            else
                                "PIN is checked on the robot server",
                            color = OperatorMuted,
                            fontSize = 11.sp
                        )
                    }
                    OutlinedTextField(
                        value = operatorName,
                        onValueChange = { operatorName = it.take(20) },
                        modifier = Modifier.width(150.dp),
                        singleLine = true,
                        label = { Text("Name") }
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v ->
                            pin = v.filter { it.isDigit() }.take(8)
                            unlockError = null
                        },
                        modifier = Modifier.width(130.dp),
                        singleLine = true,
                        label = { Text("PIN") },
                        isError = unlockError != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                unlockError = client.unlock(
                                    pin,
                                    operatorName.ifBlank { "operator" }
                                )
                                pin = ""
                                busy = false
                            }
                        },
                        enabled = pin.length >= 4 && !busy
                    ) {
                        Text(if (busy) "..." else "Unlock")
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "You have control",
                            color = OperatorDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Release a stick to stop. Leaving this screen locks control.",
                            color = OperatorMuted,
                            fontSize = 11.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Slow", fontSize = 12.sp, color = OperatorDark)
                        Switch(
                            checked = !slowMode,
                            onCheckedChange = { slowMode = !it },
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Text("Normal", fontSize = 12.sp, color = OperatorDark)
                    }
                    OutlinedButton(onClick = { scope.launch { client.lock() } }) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Lock")
                    }
                }
            }
        }

        unlockError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        message?.let { msg ->
            Surface(color = Color(0xFFFFF1DA), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF8B5700))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = Color(0xFF8B5700), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { client.clearMessage() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        // ---------------- Sticks + controls ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StickCard(
                title = "Walk",
                hint = "Up = forward · sideways = step",
                modifier = Modifier.weight(1f)
            ) {
                VirtualJoystick(
                    enabled = sticksEnabled,
                    onChange = { nx, ny ->
                        walkX = nx
                        walkY = ny
                    }
                )
            }

            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // STOP is always available, even when locked.
                Button(
                    onClick = { scope.launch { client.stop() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StopRed),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("STOP", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                if (status?.estop == true) {
                    Surface(
                        color = StopRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "SOFTWARE E-STOP ACTIVE" +
                                    (status?.estopReason?.takeIf { it.isNotBlank() }
                                        ?.let { " ($it)" } ?: ""),
                            modifier = Modifier.padding(10.dp),
                            color = StopRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                client.resetEstop()?.let { unlockError = it }
                            }
                        },
                        enabled = unlocked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Reset E-stop")
                    }
                } else {
                    OutlinedButton(
                        onClick = { scope.launch { client.estop() } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StopRed)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Software E-STOP (latched)")
                    }
                }

                StatusPanel(status = status, connected = connected, unlocked = unlocked)
            }

            StickCard(
                title = "Turn",
                hint = "Left / right = rotate",
                modifier = Modifier.weight(1f)
            ) {
                VirtualJoystick(
                    enabled = sticksEnabled,
                    horizontalOnly = true,
                    onChange = { nx, _ -> turnX = nx }
                )
            }
        }

        RobotModeCard(
            status = status,
            enabled = unlocked && !modeBusy,
            busy = modeBusy,
            onRequest = { pendingMode = it }
        )

        pendingMode?.let { mode ->
            AlertDialog(
                onDismissRequest = { pendingMode = null },
                title = { Text(modeTitle(mode)) },
                text = { Text(modeWarning(mode)) },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingMode = null
                            modeBusy = true
                            scope.launch {
                                unlockError = client.setMode(mode)
                                modeBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == "damp") StopRed else OperatorGreen
                        )
                    ) { Text("Yes, ${modeTitle(mode)}") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pendingMode = null }) { Text("Cancel") }
                }
            )
        }

        Surface(color = OperatorMint, shape = RoundedCornerShape(12.dp)) {
            Text(
                "The physical E-stop / remote remains the primary safety device. " +
                        "The robot stops automatically if this app stops sending " +
                        "(released stick, closed screen, Wi-Fi loss) and only moves " +
                        "in walking mode (FSM 500). Speeds are limited on the robot.",
                modifier = Modifier.padding(12.dp),
                color = OperatorDark,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ConnectionChip(unlocked: Boolean, connection: TeleopConnection) {
    val (label, bg, fg) = when {
        !unlocked -> Triple("LOCKED", Color(0xFFE8EBE9), OperatorMuted)
        connection == TeleopConnection.CONNECTED ->
            Triple("LIVE", OperatorMint, OperatorGreen)
        connection == TeleopConnection.CONNECTING ->
            Triple("CONNECTING…", Color(0xFFFFF1DA), Color(0xFF8B5700))
        else -> Triple("DISCONNECTED", StopRed.copy(alpha = 0.12f), StopRed)
    }
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StickCard(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = OperatorDark, fontSize = 16.sp)
            content()
            Text(hint, color = OperatorMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusPanel(status: TeleopStatus?, connected: Boolean, unlocked: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val stateText = when {
                status == null -> "No data from robot"
                !unlocked -> "Locked"
                !connected -> "Reconnecting…"
                else -> when (status.state) {
                    "driving" -> "Driving"
                    "ready" -> "Ready"
                    "idle" -> "Ready"
                    "estop" -> "E-stop active"
                    "not_locomotion_ready" -> "Robot not in walking mode"
                    "gesture" -> "Performing gesture…"
                    "locked" -> "Locked"
                    else -> status.state
                }
            }
            Text(stateText, color = OperatorGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            val fsm = status?.fsmId
            Text(
                "FSM: ${fsm ?: "?"} (${fsmName(fsm)})  ·  " +
                        if (status?.locomotionReady == true) "walking mode OK"
                        else "not walking mode",
                color = if (status?.locomotionReady == true) OperatorDark else Color(0xFF9D4B00),
                fontSize = 12.sp
            )
            HorizontalDivider()
            Text("vx:  %+.2f m/s".format(status?.vx ?: 0.0), color = OperatorDark, fontSize = 13.sp)
            Text("vy:  %+.2f m/s".format(status?.vy ?: 0.0), color = OperatorDark, fontSize = 13.sp)
            Text("yaw: %+.2f rad/s".format(status?.yaw ?: 0.0), color = OperatorDark, fontSize = 13.sp)
            status?.lastError?.takeIf { it.isNotBlank() }?.let {
                Text("Error: $it", color = StopRed, fontSize = 11.sp)
            }
        }
    }
}

private fun fsmName(fsm: Int?): String = when (fsm) {
    null -> "unknown"
    0 -> "Zero torque"
    1 -> "Damp"
    4 -> "Stand up"
    500 -> "Walking"
    else -> "other"
}

private fun modeTitle(mode: String): String = when (mode) {
    "damp" -> "Damp"
    "stand_up" -> "Stand up"
    "walk" -> "Walking mode"
    else -> mode
}

private fun modeWarning(mode: String): String = when (mode) {
    "damp" -> "The robot goes soft and can sink down. " +
            "Only do this when the robot is in the harness or sitting/lying safely."
    "stand_up" -> "The robot will stand up (FSM 4). " +
            "Is the robot in the harness and is nobody next to it?"
    "walk" -> "The robot switches to walking mode (FSM 500) and will balance " +
            "by itself. Is the robot in the harness and is the area clear?"
    else -> ""
}

@Composable
private fun RobotModeCard(
    status: TeleopStatus?,
    enabled: Boolean,
    busy: Boolean,
    onRequest: (String) -> Unit
) {
    val fsm = status?.fsmId
    val estop = status?.estop == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Robot mode",
                    fontWeight = FontWeight.Bold,
                    color = OperatorDark,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (busy) "Changing…" else "Now: FSM ${fsm ?: "?"} (${fsmName(fsm)})",
                    color = OperatorMuted,
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Damp is always allowed (safety).
                OutlinedButton(
                    onClick = { onRequest("damp") },
                    enabled = enabled && fsm != 1,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StopRed)
                ) { Text("1 · Damp") }

                Button(
                    onClick = { onRequest("stand_up") },
                    enabled = enabled && !estop && fsm == 1,
                    modifier = Modifier.weight(1f)
                ) { Text("2 · Stand up") }

                Button(
                    onClick = { onRequest("walk") },
                    enabled = enabled && !estop && fsm == 4,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = OperatorGreen)
                ) { Text("3 · Walking mode") }
            }
            Text(
                "Order: Damp → Stand up → Walking mode. Zero torque is only " +
                        "available on the Unitree remote (the robot would fall).",
                color = OperatorMuted,
                fontSize = 11.sp
            )
        }
    }
}
