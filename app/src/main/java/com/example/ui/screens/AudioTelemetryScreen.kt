package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun AudioTelemetryScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 120.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Waveform (Simulated)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..40) {
                    val height = Random.nextInt(20, 100).dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .width(3.dp)
                            .height(height)
                            .clip(CircleShape)
                            .background(Color(0xFF6D8B74))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "REAL-TIME SPECTRUM ANALYSIS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("VOLUME LEVEL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("-12.4", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("dB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("CENTER FREQ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("44.1", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("kHz", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("NETWORK LATENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("18", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp, 20.dp).clip(CircleShape).background(Color(0xFF6D8B74).copy(alpha = 0.2f)))
                        Box(modifier = Modifier.size(6.dp, 20.dp).clip(CircleShape).background(Color(0xFF6D8B74).copy(alpha = 0.4f)))
                        Box(modifier = Modifier.size(6.dp, 20.dp).clip(CircleShape).background(Color(0xFF6D8B74)))
                        Box(modifier = Modifier.size(6.dp, 20.dp).clip(CircleShape).background(Color(0xFF6D8B74)))
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha=0.5f), RoundedCornerShape(16.dp))
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("MASTER GAIN", style = MaterialTheme.typography.labelSmall)
                                Text("75%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = 0.75f,
                                onValueChange = {},
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RECORD SESSION")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
