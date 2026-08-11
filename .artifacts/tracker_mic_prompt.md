# Prompt for Tracker App Microphone Feature

Implement a background microphone streaming feature in the Tracker app that integrates with the existing Supabase infrastructure.

## Requirements

### 1. Supabase Integration
- **Listen for Commands:** Monitor the `mic_commands` table for new rows where `device_id` matches the current device.
- **Actions:**
    - `start`: Begin capturing and uploading audio.
    - `stop`: Immediately cease capture and cleanup.

### 2. Audio Capture (Android)
- **Format:** PCM 16-bit, Little Endian, Mono, 16,000 Hz.
- **Chunking:** Buffer audio and upload in small bursts (e.g., 250ms chunks) to minimize latency.
- **Background Service:** Must run as a foreground service with a persistent notification to ensure capture isn't killed by the OS.

### 3. Data Flow
- **Storage:** Upload each PCM chunk to the Supabase Storage bucket `mic-audio`. 
    - Path format: `mic-audio/<device_id>/<session_id>/<sequence_number>.pcm`
- **Signaling:** For every uploaded chunk, insert a row into the `mic_audio_chunks` table with:
    - `session_id`: Unique ID for the current monitoring session.
    - `storage_path`: The relative path in the bucket.
    - `peak_amplitude`: The pre-computed peak value (0.0 to 1.0) of that specific chunk (used by the controller for the waveform).
    - `rms`: Root Mean Square power of the chunk.

### 4. Code Snippet for Amplitude Calculation (Kotlin)
```kotlin
fun calculatePeak(buffer: ShortArray, size: Int): Float {
    var max = 0
    for (i in 0 until size) {
        val absValue = Math.abs(buffer[i].toInt())
        if (absValue > max) max = absValue
    }
    return max.toFloat() / Short.MAX_VALUE.toFloat()
}
```

## Security & Ethics
- Ensure the app explicitly requests `RECORD_AUDIO` permission.
- The notification must clearly state that "Microphone Uplink is Active" whenever the feature is running.
