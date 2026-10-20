
package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OperatorGreen = Color(0xFF087B39)
private val OperatorMint = Color(0xFFE7F5EC)
private val OperatorDark = Color(0xFF172820)
private val OperatorMuted = Color(0xFF64756B)
private val OperatorBackground = Color(0xFFF7F9F8)

// DEMONSTRATION ONLY.
// Never use this hardcoded PIN to authorize
// real robot movement.
private const val DEMO_PIN = "1234"

private enum class PreviewMotion(
    val label: String,
    val vx: Float,
    val vy: Float,
    val yaw: Float
) {
    STOP("STOPPED", 0f, 0f, 0f),
    FORWARD("FORWARD", 0.15f, 0f, 0f),
    BACKWARD("BACKWARD", -0.15f, 0f, 0f),
    LEFT("LEFT", 0f, 0.10f, 0f),
    RIGHT("RIGHT", 0f, -0.10f, 0f),
    ROTATE_LEFT("ROTATE LEFT", 0f, 0f, 0.20f),
    ROTATE_RIGHT("ROTATE RIGHT", 0f, 0f, -0.20f)
}

@Composable
fun OperatorJoystickScreen(
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf(PreviewMotion.STOP) }

    fun stop() {
        motion = PreviewMotion.STOP
    }

    fun lock() {
        stop()
        unlocked = false
        pin = ""
        error = false
    }

    // When the screen closes, reset preview movement.
    DisposableEffect(Unit) {
        onDispose {
            stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OperatorBackground)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    stop()
                    onBack()
                }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = null
                )
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

            Surface(
                color = Color(0xFFFFF1DA),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "SIMULATION ONLY",
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    color = Color(0xFF8B5700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            IconButton(
                onClick = {
                    stop()
                    onHome()
                }
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    tint = OperatorGreen
                )
            }
        }

        // PIN entry is directly above the joystick.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
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
                    if (unlocked)
                        Icons.Default.LockOpen
                    else
                        Icons.Default.Lock,
                    contentDescription = null,
                    tint = OperatorGreen,
                    modifier = Modifier.size(28.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (unlocked)
                            "Operator Preview Unlocked"
                        else
                            "Operator PIN Required",
                        color = OperatorDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        if (unlocked)
                            "Joystick UI enabled · Robot disconnected"
                        else
                            "Enter demo PIN to enable joystick preview",
                        color = OperatorMuted,
                        fontSize = 11.sp
                    )
                }

                if (!unlocked) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            pin = value
                                .filter { it.isDigit() }
                                .take(4)
                            error = false
                        },
                        modifier = Modifier.width(130.dp),
                        singleLine = true,
                        label = { Text("PIN") },
                        isError = error,
                        visualTransformation =
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )

                    Button(
                        onClick = {
                            if (pin == DEMO_PIN) {
                                unlocked = true
                                error = false
                                stop()
                            } else {
                                error = true
                                pin = ""
                            }
                        },
                        enabled = pin.length == 4
                    ) {
                        Text("Unlock")
                    }
                } else {
                    OutlinedButton(
                        onClick = { lock() }
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Lock")
                    }
                }
            }
        }

        if (error) {
            Text(
                "Incorrect PIN. Please try again.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main directional control
            Card(
                modifier = Modifier.weight(1.25f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "Walking Direction",
                        fontWeight = FontWeight.Bold,
                        color = OperatorDark,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HoldDirectionButton(
                            label = "Forward",
                            icon = Icons.Default.ArrowUpward,
                            enabled = unlocked,
                            active = motion == PreviewMotion.FORWARD,
                            onPress = {
                                motion = PreviewMotion.FORWARD
                            },
                            onRelease = { stop() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                                Alignment.CenterHorizontally
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HoldDirectionButton(
                            label = "Left",
                            icon = Icons.Default.KeyboardArrowLeft,
                            enabled = unlocked,
                            active = motion == PreviewMotion.LEFT,
                            onPress = {
                                motion = PreviewMotion.LEFT
                            },
                            onRelease = { stop() }
                        )

                        Button(
                            onClick = { stop() },
                            modifier = Modifier
                                .width(105.dp)
                                .height(68.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB3261E)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = null
                                )
                                Text(
                                    "STOP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HoldDirectionButton(
                            label = "Right",
                            icon = Icons.Default.KeyboardArrowRight,
                            enabled = unlocked,
                            active = motion == PreviewMotion.RIGHT,
                            onPress = {
                                motion = PreviewMotion.RIGHT
                            },
                            onRelease = { stop() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HoldDirectionButton(
                            label = "Backward",
                            icon = Icons.Default.ArrowDownward,
                            enabled = unlocked,
                            active = motion == PreviewMotion.BACKWARD,
                            onPress = {
                                motion = PreviewMotion.BACKWARD
                            },
                            onRelease = { stop() }
                        )
                    }
                }
            }

            // Rotation controls
            Card(
                modifier = Modifier.weight(0.85f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Robot Rotation",
                        fontWeight = FontWeight.Bold,
                        color = OperatorDark,
                        fontSize = 16.sp
                    )

                    HoldDirectionButton(
                        label = "Rotate Left",
                        icon = Icons.Default.Refresh,
                        enabled = unlocked,
                        active = motion == PreviewMotion.ROTATE_LEFT,
                        onPress = {
                            motion = PreviewMotion.ROTATE_LEFT
                        },
                        onRelease = { stop() },
                        wide = true
                    )

                    HoldDirectionButton(
                        label = "Rotate Right",
                        icon = Icons.Default.Refresh,
                        enabled = unlocked,
                        active = motion == PreviewMotion.ROTATE_RIGHT,
                        onPress = {
                            motion = PreviewMotion.ROTATE_RIGHT
                        },
                        onRelease = { stop() },
                        wide = true
                    )

                    Text(
                        "Press and hold to preview movement.",
                        color = OperatorMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Status and velocity preview
            Card(
                modifier = Modifier.weight(0.95f),
                colors = CardDefaults.cardColors(
                    containerColor = OperatorMint
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Motion Preview",
                        color = OperatorDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = if (motion == PreviewMotion.STOP)
                            Color.White
                        else
                            Color(0xFFD3F4DE),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            motion.label,
                            modifier = Modifier.padding(12.dp),
                            color = OperatorGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "vx: %.2f m/s".format(motion.vx),
                        color = OperatorDark,
                        fontSize = 13.sp
                    )

                    Text(
                        "vy: %.2f m/s".format(motion.vy),
                        color = OperatorDark,
                        fontSize = 13.sp
                    )

                    Text(
                        "yaw: %.2f rad/s".format(motion.yaw),
                        color = OperatorDark,
                        fontSize = 13.sp
                    )

                    HorizontalDivider()

                    Text(
                        "Robot: Not connected",
                        color = OperatorMuted,
                        fontSize = 12.sp
                    )

                    Text(
                        "Physical motion: DISABLED",
                        color = Color(0xFF9D4B00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            color = OperatorMint,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Demo PIN 1234 unlocks the interface only. " +
                        "No movement commands are sent to Unitree G1. " +
                        "Real control requires G1 Studio authentication, " +
                        "motion safety checks and an independent " +
                        "physical emergency stop.",
                modifier = Modifier.padding(12.dp),
                color = OperatorDark,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun HoldDirectionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    active: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    wide: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)

    val backgroundColor = when {
        !enabled -> Color(0xFFE8EBE9)
        active -> OperatorGreen
        else -> OperatorMint
    }

    val contentColor = when {
        !enabled -> Color.Gray
        active -> Color.White
        else -> OperatorGreen
    }

    Column(
        modifier = Modifier
            .width(if (wide) 165.dp else 105.dp)
            .height(68.dp)
            .background(backgroundColor, shape)
            .border(
                width = 1.dp,
                color = if (active)
                    OperatorGreen
                else
                    Color(0xFFDCE7DF),
                shape = shape
            )
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                onRelease()
                            }
                        }
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(25.dp)
        )

        Text(
            label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
