package com.aistudio.missioncontrol.pxytwe

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.HttpTimeout

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Live `locations` row shape. Extra columns the controller doesn't read
// (altitude, pitch, roll, pressure, charging, etc.) are ignored by Kotlin
// serialization — that's by design and survives schema growth.
@Serializable
data class LocationData(
    val device_id: String, // carries the human-readable device name as before
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float,
    val created_at: String
)

// Live `commands` row shape — this is what the per-device wake/sleep /
// mic toggle goes into. `params` and `status` are nullable on the table.
@Serializable
data class CommandPayload(
    val device_id: String,
    val command: String, // "wake" | "sleep" | "start_mic" | "stop_mic"
    val params: String? = null, // JSON encoded payload for the tracker
    val status: String? = "pending"
)

/**
 * Single source of truth for Supabase calls. Owns one realtime client
 * and one WebSocket per process. The realtime connection state is
 * surfaced via [connectionState] so the UI can show a connection badge
 * (E3 from the brief).
 */
object SupabaseClientManager {

    enum class ConnectionState { Disconnected, Connecting, Connected, Reconnecting }

    @OptIn(SupabaseInternal::class)
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            // OkHttp engine is auto-discovered from the classpath
            install(Postgrest)
            // Ponytail: increase timeout to 30s to avoid HttpRequestTimeoutException
            // on large initial location fetches or slow networks.
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30000L
                    connectTimeoutMillis = 10000L
                    socketTimeoutMillis = 10000L
                }
            }
            // Ponytail: shorter heartbeat + bounded reconnect so the SDK stops
            // soaking battery on a dead socket. Add when: replace with a true
            // exponential backoff if reconnects thunder on a flaky network.
            install(Realtime) {
                heartbeatInterval = 10.seconds
                reconnectDelay = 2.seconds
            }
        }
    }

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Single, process-scoped supervisor so the realtime status collector
    // can outlive every Composable scope and not crash on a single bad emit.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    private val _pingFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    private val _statusFlow = MutableSharedFlow<CommandPayload>(extraBufferCapacity = 10)

    init {
        // Realtime.status is a public StateFlow<Realtime.Status> with
        // DISCONNECTED/CONNECTING/CONNECTED. No public Reconnecting enum
        // in 2.4.0 — we bucket CONNECTING (COVER-R for our badge) and
        // treat DISCONNECTED as Offline.
        client.realtime.status.collectSafely(scope) { status ->
            _connectionState.value = when (status) {
                Realtime.Status.CONNECTED ->
                    ConnectionState.Connected
                Realtime.Status.CONNECTING ->
                    ConnectionState.Connecting
                Realtime.Status.DISCONNECTED ->
                    ConnectionState.Disconnected
            }
        }
    }

    // Lazily-initialized shared channel for commands — both sendCommand()
    // and startListeningForPongs() use the same object so broadcast events
    // are never lost to a stale/duplicate subscription.
    private var commandsChannel: RealtimeChannel? = null

    private suspend fun getOrCreateCommandsChannel(): RealtimeChannel {
        val topic = "public:commands"
        commandsChannel?.let { ch ->
            if (ch.status.value == RealtimeChannel.Status.SUBSCRIBED) return ch
        }
        // Remove any stale subscription with the same topic
        client.realtime.subscriptions.values.find { it.topic == topic }?.let {
            try { client.realtime.removeChannel(it) } catch (_: Exception) {}
        }
        val ch = client.realtime.channel(topic)
        ch.subscribe(blockUntilSubscribed = true)
        commandsChannel = ch
        return ch
    }

    private var pongListenerJob: kotlinx.coroutines.Job? = null

    suspend fun startListeningForPongs() {
        try {
            val channel = getOrCreateCommandsChannel()
            pongListenerJob?.cancel()
            pongListenerJob = scope.launch {
                launch {
                    channel.broadcastFlow<CommandPayload>(event = "pong").collect { pong ->
                        Log.d("SupabaseClient", "Pong received from ${pong.device_id}")
                        _pingFlow.emit(pong.device_id)
                    }
                }
                launch {
                    channel.broadcastFlow<CommandPayload>(event = "command").collect { payload ->
                        if (payload.command == "status_response") {
                            Log.d("SupabaseClient", "Status response received from ${payload.device_id}: ${payload.params}")
                            _statusFlow.emit(payload)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "startListeningForPongs failed", e)
        }
    }

    suspend fun connectRealtime() {
        client.realtime.connect()
    }


    internal fun encodeFilterValue(raw: String): String =
        URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")

    // --- Per-device wake/sleep ---

    suspend fun sendWakeCommand(deviceId: String) {
        sendCommand(deviceId, "wake")
    }

    suspend fun sendSleepCommand(deviceId: String) {
        sendCommand(deviceId, "sleep")
    }

    suspend fun sendAutoHealCommand(deviceId: String) {
        sendCommand(deviceId, "auto_heal")
    }

    suspend fun sendFixAllCommand(deviceId: String) {
        sendCommand(deviceId, "auto_heal")
    }

    suspend fun pingDevice(deviceId: String): Long? {
        val start = System.currentTimeMillis()
        return try {
            withTimeout(10.seconds) {
                val pingDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    _pingFlow.first { it == deviceId }
                }
                
                sendCommand(deviceId, "ping")
                
                pingDeferred.await()
                System.currentTimeMillis() - start
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            Log.e("SupabaseClient", "pingDevice failed", e)
            null
        }
    }

    suspend fun checkDeviceStatus(deviceId: String): String? {
        return try {
            withTimeout(10.seconds) {
                val statusDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    _statusFlow.first { it.device_id == deviceId }.params
                }
                
                sendCommand(deviceId, "check_status")
                
                statusDeferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            Log.e("SupabaseClient", "checkDeviceStatus failed", e)
            null
        }
    }

    suspend fun sendCommand(deviceId: String, command: String, params: String? = null) {
        try {
            val payload = CommandPayload(device_id = deviceId, command = command, params = params, status = "pending")
            
            // 1. Insert into database for persistence
            try {
                client.postgrest["commands"].insert(payload)
            } catch (e: Exception) {
                Log.e("SupabaseClient", "Database insert failed for command ($command for $deviceId)", e)
            }
            
            // 2. Broadcast for real-time delivery
            val channel = getOrCreateCommandsChannel()
            channel.broadcast(
                event = "command",
                message = payload
            )
            
            // Stagger commands to respect Supabase's 10 events/sec Realtime rate limit
            kotlinx.coroutines.delay(150)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Broadcast to commands failed ($command for $deviceId)", e)
            // Do not rethrow. A failed command broadcast shouldn't crash the entire app.
        }
    }

    // --- Location stream: the only thing actually flowing right now ---

    suspend fun listenToLocations(): Flow<LocationData> {
        val topic = "public:locations"
        val existing = client.realtime.subscriptions.values.find { it.topic == topic }
        if (existing != null) {
            client.realtime.removeChannel(existing)
        }
        
        val channel = client.realtime.channel(topic)
        val flow = channel.broadcastFlow<LocationData>(event = "location")
        channel.subscribe(blockUntilSubscribed = true)
        Log.d("SupabaseClient", "Subscribed to locations channel")
        return flow
    }

    suspend fun getInitialLocations(): List<LocationData> {
        // Ponytail: Don't swallow network exceptions here — let the caller 
        // (AppState) decide how to retry.
        val result = client.postgrest["locations"].select(
            Columns.list("device_id", "latitude", "longitude", "accuracy", "bearing", "created_at")
        ) {
            order("created_at", order = Order.DESCENDING)
            limit(5000)
        }
        val allLocs = result.decodeList<LocationData>()
        // group by device_id and take the most recent
        return allLocs.groupBy { it.device_id }.map { it.value.first() }
    }

    suspend fun deleteDevice(deviceId: String) {
        try {
            client.postgrest["locations"].delete {
                filter {
                    eq("device_id", deviceId)
                }
            }
            client.postgrest["commands"].delete {
                filter {
                    eq("device_id", deviceId)
                }
            }
            client.postgrest["media"].delete {
                filter {
                    eq("device_id", deviceId)
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "deleteDevice failed", e)
            throw e
        }
    }
}

// Ponytail: collect the channel.status flow inside a printable scope so we
// can wrap it once with an exception-trap guard.
private fun <T> Flow<T>.collectSafely(
    scope: CoroutineScope,
    block: suspend (T) -> Unit,
) {
    scope.launch {
        try {
            collect { block(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SupabaseClient", "realtime state collector died", e)
        }
    }
}
