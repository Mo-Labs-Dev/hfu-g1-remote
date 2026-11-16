package com.example.g1remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/**
 * Analog thumb stick. Reports normalized values in -1..1:
 *   nx: right = +1, ny: up = +1.
 * Springs back to (0, 0) on release or touch cancel (dead-man).
 */
@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    enabled: Boolean,
    horizontalOnly: Boolean = false,
    accent: Color = Color(0xFF087B39),
    onChange: (nx: Float, ny: Float) -> Unit
) {
    var knob by remember { mutableStateOf(Offset.Zero) } // normalized
    val currentOnChange by rememberUpdatedState(onChange)

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(enabled, horizontalOnly) {
                if (!enabled) {
                    knob = Offset.Zero
                    currentOnChange(0f, 0f)
                    return@pointerInput
                }

                val radius = min(this.size.width, this.size.height) / 2f * 0.78f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                fun update(position: Offset) {
                    var dx = position.x - center.x
                    var dy = if (horizontalOnly) 0f else position.y - center.y
                    val dist = hypot(dx, dy)
                    if (dist > radius) {
                        dx = dx / dist * radius
                        dy = dy / dist * radius
                    }
                    val nx = dx / radius
                    val ny = -dy / radius
                    knob = Offset(nx, ny)
                    currentOnChange(nx, ny)
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    update(down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            change.consume()
                            update(change.position)
                        }
                    } finally {
                        knob = Offset.Zero
                        currentOnChange(0f, 0f)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val r = min(this.size.width, this.size.height) / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val travel = r * 0.78f
            val baseColor = if (enabled) accent else Color.Gray

            drawCircle(color = baseColor.copy(alpha = 0.10f), radius = r, center = c)
            drawCircle(
                color = baseColor.copy(alpha = 0.45f),
                radius = r - 2f,
                center = c,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = baseColor.copy(alpha = 0.25f),
                radius = travel * 0.5f,
                center = c,
                style = Stroke(width = 2f)
            )
            // Axis guides
            drawLine(
                baseColor.copy(alpha = 0.25f),
                Offset(c.x - travel, c.y), Offset(c.x + travel, c.y), 2f
            )
            if (!horizontalOnly) {
                drawLine(
                    baseColor.copy(alpha = 0.25f),
                    Offset(c.x, c.y - travel), Offset(c.x, c.y + travel), 2f
                )
            }

            val knobCenter = Offset(c.x + knob.x * travel, c.y - knob.y * travel)
            drawCircle(
                color = if (enabled) accent else Color.LightGray,
                radius = r * 0.24f,
                center = knobCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = r * 0.12f,
                center = knobCenter
            )
        }
    }
}
