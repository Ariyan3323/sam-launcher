package com.fgmembers.samlauncher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RoboticMascot(isOnline: Boolean = true, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sam_mascot")
    val floatY by transition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )
    val neon = if (isOnline) Color(0xFF00FF66) else Color(0xFFFF3344)
    Surface(
        modifier = modifier.offset(y = floatY.dp).size(72.dp)
            .border(2.dp, neon, CircleShape),
        shape = CircleShape, color = Color(0xFF0A0E17), shadowElevation = 10.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(10.dp).background(neon, CircleShape))
                Box(Modifier.size(10.dp).background(neon, CircleShape))
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(22.dp).height(3.dp).background(neon, RoundedCornerShape(2.dp)))
        }
    }
}
