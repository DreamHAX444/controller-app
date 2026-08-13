package com.aistudio.missioncontrol.pxytwe.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.missioncontrol.pxytwe.security.SecurityManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Two-phase PIN lock:
 *  - first launch + no PIN stored → enter PIN, then re-enter to confirm
 *  - subsequent launches → single 4-digit verify with shake on wrong entry
 *
 * Returns user to the same locking surface so muscle memory works either way.
 */
@Composable
fun PinLockScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    val haptic = LocalHapticFeedback.current

    // We don't know yet whether the user has set a PIN — fetch off-main on
    // composition. While loading we assume "needs setup".
    var isPinConfigured by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        isPinConfigured = securityManager.isPinSet()
    }
    val isSettingPin = isPinConfigured != true
    var pin by remember { mutableStateOf("") }
    var pendingPin by remember { mutableStateOf<String?>(null) } // used in confirm step
    var isError by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(isError) {
        if (isError) {
            for (i in 0..5) {
                shakeOffset.animateTo(
                    targetValue = if (i % 2 == 0) 10f else -10f,
                    animationSpec = tween(50, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
            delay(500.milliseconds)
            pin = ""
            isError = false
        }
    }

    LaunchedEffect(pin) {
        if (pin.length != 4) return@LaunchedEffect

        if (isSettingPin) {
            if (pendingPin == null) {
                pendingPin = pin
                pin = ""
            } else {
                if (pendingPin == pin) {
                    securityManager.setPin(pin)
                    onUnlockSuccess()
                } else {
                    pendingPin = null
                    isError = true
                }
            }
        } else {
            if (securityManager.verifyPin(pin)) {
                onUnlockSuccess()
            } else {
                isError = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when {
                    isSettingPin && pendingPin == null -> "SET MASTER PIN"
                    isSettingPin && pendingPin != null -> "CONFIRM PIN"
                    else -> "ENTER MASTER PIN"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = isError) {
                Text(
                    text = "PINS DON'T MATCH — TRY AGAIN",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (!isError) {
                Text(
                    text = if (isSettingPin && pendingPin != null)
                        "RE-ENTER THE 4-DIGIT CODE"
                    else
                        "SECURE ENCRYPTED UPLINK",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(x = shakeOffset.value.dp.roundToPx(), y = 0) },
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (i in 0 until 4) {
                    val isFilled = pin.length > i
                    val size by animateDpAsState(targetValue = if (isFilled) 14.dp else 10.dp, label = "dotSize")
                    val alpha by animateFloatAsState(targetValue = if (isFilled) 1f else 0.2f, label = "dotAlpha")
                    Box(
                        modifier = Modifier
                            .size(14.dp), // Fixed bounds to prevent shifting
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(
                                    if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            val keys = listOf(
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "", "0", "DEL"
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (row in keys.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (key in row) {
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else if (key == "DEL") {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (pin.isNotEmpty() && !isError) {
                                                pin = pin.dropLast(1)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete digit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (pin.length < 4 && !isError) {
                                                pin += key
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Light,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
