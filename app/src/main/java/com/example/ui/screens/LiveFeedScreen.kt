package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun LiveFeedScreen() {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf("Drone_Alpha_X1") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = "https://lh3.googleusercontent.com/aida-public/AB6AXuAquK--3WJDSzLYHS4YjrEt72lGbCdhni8OTGBjLSO0FzONvBOxRrLPqdFfPokb-BePk888CMQKgT9aditUoM6kTQ6VbKhof0iHFoazKpe9LlpOwiY131IvWoI7sDtWarJi27cQBQETfdTTDpFh3IdtWwHuewY00SYnxsGxwYWLET2afkA7B2-B9LodhlyVfYADLcJNw9CjeyQZbTIvvNk0kNhlNmxnobOE0uSefEw9DesuoXZRz6tT",
            contentDescription = "Live Feed",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .padding(top = 48.dp, start = 24.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "00:42:18",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(top = 48.dp, end = 24.dp)
                .align(Alignment.TopEnd)
        ) {
            IconButton(
                onClick = { /*TODO*/ },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(
                onClick = { /*TODO*/ },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 120.dp)
                .align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDropdownExpanded = !isDropdownExpanded },
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectedDevice,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isDropdownExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            AnimatedVisibility(visible = isDropdownExpanded) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val devices = listOf("Drone_Alpha_X1", "Rover_Beta_V2", "Sensor_Gamma_L")
                        devices.forEach { deviceName ->
                            val isSelected = selectedDevice == deviceName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedDevice = deviceName
                                        isDropdownExpanded = false
                                    }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (deviceName == "Drone_Alpha_X1") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = { /*TODO*/ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 220.dp, end = 24.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
