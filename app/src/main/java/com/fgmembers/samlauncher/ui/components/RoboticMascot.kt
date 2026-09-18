package com.fgmembers.samlauncher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun RoboticMascot(isOnline: Boolean = true, state: MascotState = MascotState.Idle, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sam_cyber_motion")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "ring_a")
    val reverseRotation by transition.animateFloat(360f, 0f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "ring_b")
    val floatY by transition.animateFloat(-4f, 4f, infiniteRepeatable(tween(if (state == MascotState.Idle) 1400 else 800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val pulse by transition.animateFloat(.82f, if (state == MascotState.Speaking) 1.28f else 1.12f, infiniteRepeatable(tween(if (state == MascotState.Thinking) 420 else 900), RepeatMode.Reverse), label = "eyes")
    val headTilt by transition.animateFloat(if (state == MascotState.Idle) 0f else -4f, if (state == MascotState.Idle) 0f else 4f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "head_tilt")
    val neon = if (isOnline) Color(0xFF00FF66) else Color(0xFFFF3344)

    Canvas(modifier.size(88.dp).offset(y = floatY.dp).rotate(headTilt)) {
        val center = this.center
        val radius = min(size.width, size.height) * .31f
        // Counter-rotating mechanical rings.
        val ringRadiusA = radius * 1.42f
        val ringRadiusB = radius * 1.58f
        drawArc(neon.copy(alpha = .22f), rotation, 250f, false, topLeft = Offset(center.x - ringRadiusA, center.y - ringRadiusA), size = Size(ringRadiusA * 2, ringRadiusA * 2), style = Stroke(7f, cap = StrokeCap.Round))
        drawArc(Color(0xFF36D9FF).copy(alpha = .8f), -reverseRotation, 115f, false, topLeft = Offset(center.x - ringRadiusB, center.y - ringRadiusB), size = Size(ringRadiusB * 2, ringRadiusB * 2), style = Stroke(3f, cap = StrokeCap.Round))
        drawCircle(Color(0xFF080D17), radius)
        drawCircle(neon.copy(alpha = .32f), radius, style = Stroke(5f))
        drawCircle(neon.copy(alpha = .18f), radius * 1.08f, style = Stroke(2f))
        // Layered glow behind the animated eyes.
        val eyeY = center.y - radius * .12f
        listOf(.24f, .14f, .06f).forEachIndexed { index, alpha ->
            val eyeRadius = radius * (.23f + index * .08f) * pulse
            drawCircle(neon.copy(alpha = alpha), radius = eyeRadius, center = center.copy(x = center.x - radius * .34f, y = eyeY))
            drawCircle(neon.copy(alpha = alpha), radius = eyeRadius, center = center.copy(x = center.x + radius * .34f, y = eyeY))
        }
        drawCircle(neon, radius = radius * .10f * pulse, center = center.copy(x = center.x - radius * .34f, y = eyeY))
        drawCircle(neon, radius = radius * .10f * pulse, center = center.copy(x = center.x + radius * .34f, y = eyeY))
        val mouthWidth = if (state == MascotState.Speaking) radius * (.28f + pulse * .10f) else radius * .28f
        drawLine(neon, center.copy(x = center.x - mouthWidth, y = center.y + radius * .35f), center.copy(x = center.x + mouthWidth, y = center.y + radius * .35f), 4f, StrokeCap.Round)
        drawLine(neon, center.copy(y = center.y - radius * 1.02f), center.copy(y = center.y - radius * 1.28f), 3f, StrokeCap.Round)
        drawCircle(neon, radius = 4f, center = center.copy(y = center.y - radius * 1.32f))
    }
}

enum class MascotState { Idle, Thinking, Speaking }
