package com.example.g1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BatGreen = Color(0xFF009B77)
private val BatOrange = Color(0xFFD79616)
private val BatRed = Color(0xFFD62828)

private fun batteryColor(b: G1StudioSync.Battery) = when (b.level) {
    "critical" -> BatRed
    "low" -> BatOrange
    "ok" -> BatGreen
    else -> Color(0xFF8A949E)
}

/** Small battery badge for the top bar: "G1 76 %". */
@Composable
fun BatteryChip(battery: G1StudioSync.Battery) {
    val soc = battery.soc ?: return
    val color = batteryColor(battery)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when {
                battery.charging -> Icons.Default.BatteryChargingFull
                battery.level == "ok" -> Icons.Default.BatteryFull
                else -> Icons.Default.BatteryAlert
            },
            contentDescription = "G1 battery",
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("G1 ${soc.toInt()} %", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Warning bar for the home screen (below 50 % orange, below 20 % red). */
@Composable
fun BatteryBanner(battery: G1StudioSync.Battery, german: Boolean) {
    val soc = battery.soc ?: return
    if (battery.charging || battery.level !in setOf("low", "critical")) return
    val critical = battery.level == "critical"
    val text = when {
        critical && german -> "Akku fast leer (${soc.toInt()} %) – bitte G1 jetzt aufladen."
        critical -> "Battery almost empty (${soc.toInt()} %) – please charge G1 now."
        german -> "Akku schwach (${soc.toInt()} %) – G1 bitte bald aufladen."
        else -> "Battery low (${soc.toInt()} %) – please charge G1 soon."
    }
    Surface(
        color = if (critical) Color(0xFFFDECEC) else Color(0xFFFFF6E5),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BatteryAlert, null, tint = if (critical) BatRed else BatOrange)
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                color = if (critical) Color(0xFF8F1010) else Color(0xFF7A520E),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/** Pop-up once when the level drops to low or critical. */
@Composable
fun BatteryAlertPopup(battery: G1StudioSync.Battery, german: Boolean) {
    var shownFor by remember { mutableStateOf("ok") }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(battery.level, battery.charging) {
        if (!battery.charging && battery.level in setOf("low", "critical") && battery.level != shownFor) {
            open = true
            shownFor = battery.level
        }
        if (battery.level == "ok") shownFor = "ok"
    }
    if (!open) return
    val critical = battery.level == "critical"
    val soc = battery.soc?.toInt() ?: 0
    AlertDialog(
        onDismissRequest = { open = false },
        icon = { Icon(Icons.Default.BatteryAlert, null, tint = if (critical) BatRed else BatOrange) },
        title = {
            Text(
                if (critical) (if (german) "Bitte G1 aufladen!" else "Please charge G1!")
                else (if (german) "Akku schwach" else "Battery low"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (critical) (if (german) "Nur noch $soc % Akku. Bitte G1 jetzt hinsetzen und aufladen."
                else "Only $soc % battery left. Please let G1 sit down and charge it now.")
                else (if (german) "Der Akku hat noch $soc %. Bitte bald aufladen."
                else "The battery is at $soc %. Please plan to charge G1 soon.")
            )
        },
        confirmButton = {
            TextButton(onClick = { open = false }) { Text("OK") }
        }
    )
}
