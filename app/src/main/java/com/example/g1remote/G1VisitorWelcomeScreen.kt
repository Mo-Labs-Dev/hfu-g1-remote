
package com.example.g1remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WelcomeGreen = Color(0xFF087B39)
private val WelcomeMint = Color(0xFFE7F5EC)
private val WelcomeDark = Color(0xFF172820)
private val WelcomeMuted = Color(0xFF64756B)

@Composable
fun G1VisitorWelcomeScreen(
    welcomeText: String,
    isGerman: Boolean,
    isLoading: Boolean,
    error: String?,
    onStartSafety: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9F8))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = WelcomeMint
                    )
                ) {
                    Image(
                        painter = painterResource(R.drawable.robot),
                        contentDescription = "Unitree G1 humanoid robot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (isGerman) {
                            "Willkommen bei HFU IPSE!"
                        } else {
                            "Welcome to HFU IPSE!"
                        },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = WelcomeGreen
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (isGerman) {
                            "Ich bin G1, Ihr Roboterassistent."
                        } else {
                            "I'm G1, your robotic assistant."
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WelcomeDark
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (isGerman) {
                            "Bevor Sie unser Labor erkunden, " +
                                    "lesen Sie bitte die Sicherheitshinweise."
                        } else {
                            "Before exploring our laboratory, " +
                                    "please review the visitor safety instructions."
                        },
                        fontSize = 15.sp,
                        color = WelcomeMuted,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    Surface(
                        color = WelcomeMint,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = WelcomeGreen
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = if (isGerman) {
                                    "Sicherheitseinweisung erforderlich"
                                } else {
                                    "Visitor safety briefing required"
                                },
                                fontSize = 13.sp,
                                color = WelcomeDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onStartSafety,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WelcomeGreen
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isGerman) {
                                    "Sicherheitseinweisung starten"
                                } else {
                                    "Start Safety Briefing"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.width(10.dp))

                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }

                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Connecting Humans and Robots",
            color = WelcomeGreen,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Institute for Product and Service Engineering",
            color = WelcomeMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
