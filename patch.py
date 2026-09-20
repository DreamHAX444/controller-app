import os

path = r'app/src/main/java/com/aistudio/missioncontrol/pxytwe/ui/screens/CameraAccessScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''
                        if (startState != null) {
                            when (startState.status) {
                                AppState.CameraStartStatus.REQUESTING -> {
                                    Text("Requesting camera start...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                AppState.CameraStartStatus.ACCEPTED -> {
                                    Text("Camera start request accepted. Waiting for capture...", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.OPENING -> {
                                    Text("Camera opening...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                AppState.CameraStartStatus.CAPTURING -> {
                                    Text("Camera capture active", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.STOPPING -> {
                                    Text("Camera stopping...", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                AppState.CameraStartStatus.STOPPED -> {
                                    Text("Camera stopped", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AppState.CameraStartStatus.REJECTED -> {
                                    Text("Request rejected: ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.FAILED -> {
                                    Text("Request failed: ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                AppState.CameraStartStatus.ERROR -> {
                                    Text("Camera capture error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                else -> {}
                            }
                        }

                        if (startState == null || (startState.status != AppState.CameraStartStatus.REQUESTING && startState.status != AppState.CameraStartStatus.ACCEPTED && startState.status != AppState.CameraStartStatus.OPENING && startState.status != AppState.CameraStartStatus.CAPTURING)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    AppState.requestStartCamera(
                                        deviceId = deviceId,
                                        cameraId = finalConfig.cameraId,
                                        width = finalConfig.width,
                                        height = finalConfig.height,
                                        fps = finalConfig.fps
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start Camera")
                            }
                        }
                        
                        if (startState != null && (startState.status == AppState.CameraStartStatus.ACCEPTED || startState.status == AppState.CameraStartStatus.OPENING || startState.status == AppState.CameraStartStatus.CAPTURING)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    AppState.requestStopCamera(deviceId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop Camera")
                            }
                        }
'''

start_marker = 'if (startState != null) {'
end_marker = '                        }'

if 'AppState.CameraStartStatus.CAPTURING' not in content:
    start_idx = content.find(start_marker)
    if start_idx != -1:
        end_idx = content.find('                        }', content.find('Text("Start Camera")')) + 25
        if end_idx != -1:
            content = content[:start_idx] + replacement.strip() + '\n' + content[end_idx:]

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
