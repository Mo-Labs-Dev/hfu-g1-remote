
package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val GuideGreen = Color(0xFF087B39)
private val GuideMint = Color(0xFFE7F5EC)
private val GuideBackground = Color(0xFFF7F9F8)
private val GuideDark = Color(0xFF172820)
private val GuideMuted = Color(0xFF64756B)

@Composable
fun GuidanceStatusScreen(
    onBack: () -> Unit
) {
    var guidance by remember {
        mutableStateOf<G1Backend.GuidanceStatus?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                guidance = G1Backend.getGuidanceStatus()
                error = null
            } catch (exception: Exception) {
                error = exception.message
                    ?: "Unable to connect to G1 Studio"
            } finally {
                loading = false
            }

            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuideBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }

            Spacer(Modifier.width(16.dp))

            Text(
                "Coffee Machine Guidance",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = GuideDark
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Live Workflow Status",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuideDark
                )

                Text(
                    "G1 Studio · IPSE",
                    fontSize = 12.sp,
                    color = GuideMuted
                )

                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = GuideGreen
                    )
                }

                if (error != null) {
                    Text(
                        "Backend connection error: $error",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val current = guidance

                if (current != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Current State",
                                color = GuideMuted,
                                fontSize = 12.sp
                            )

                            Text(
                                current.state
                                    .replace("_", " ")
                                    .replaceFirstChar {
                                        it.uppercase()
                                    },
                                color = GuideDark,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "${current.progress}%",
                            color = GuideGreen,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            current.progress / 100f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = GuideGreen
                    )

                    Text(
                        current.message,
                        fontSize = 13.sp,
                        color = GuideMuted
                    )

                    if (current.steps.isNotEmpty()) {
                        Text(
                            "Workflow Steps",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GuideDark
                        )

                        current.steps.forEachIndexed {
                                index, step ->

                            val completed =
                                step.status == "completed"

                            val running =
                                step.status == "running"

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (completed || running)
                                    GuideMint
                                else GuideBackground,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (completed)
                                            GuideGreen
                                        else GuideMuted
                                    )

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "${index + 1}. ${step.name}",
                                            color = GuideDark,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )

                                        Text(
                                            "${step.action} · ${step.status}",
                                            color = GuideMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (current.sessionId != null) {
                        Text(
                            "Execution: ${current.sessionId}",
                            color = GuideMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Surface(
            color = GuideMint,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = GuideGreen
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    "Physical G1 speech is enabled. " +
                            "Navigation remains simulated. " +
                            "This display cannot operate the robot.",
                    color = GuideDark,
                    fontSize = 12.sp
                )
            }
        }
    }
}
