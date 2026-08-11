package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonLoader(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), shape)
    )
}
