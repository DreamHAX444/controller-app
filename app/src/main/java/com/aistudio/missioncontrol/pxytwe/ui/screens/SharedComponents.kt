package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.SupabaseClientManager

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

@Composable
fun RealtimeStatusStrip(modifier: Modifier = Modifier) {
    val state by SupabaseClientManager.connectionState.collectAsState()
    val (label, color) = when (state) {
        SupabaseClientManager.ConnectionState.Connected ->
            "LIVE" to com.aistudio.missioncontrol.pxytwe.ui.theme.StatusLive
        SupabaseClientManager.ConnectionState.Connecting ->
            "CONNECTING…" to com.aistudio.missioncontrol.pxytwe.ui.theme.StatusConnecting
        SupabaseClientManager.ConnectionState.Reconnecting ->
            "RECONNECTING…" to com.aistudio.missioncontrol.pxytwe.ui.theme.StatusReconnecting
        SupabaseClientManager.ConnectionState.Disconnected ->
            "OFFLINE" to com.aistudio.missioncontrol.pxytwe.ui.theme.StatusOffline

    }
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(end = 24.dp, top = 16.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun LocationOffBadge(modifier: Modifier = Modifier) {
    Surface(
        color = Color.Red.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.width(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("LOCATION OFF", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

