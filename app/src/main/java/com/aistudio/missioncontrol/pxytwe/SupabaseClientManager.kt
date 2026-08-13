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
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.engine.cio.CIO
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    val id: Long = 0,
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
            httpEngine = CIO.create()
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

    init {
        // Realtime.status is a public StateFlow<Realtime.Status> with
        // DISCONNECTED/CONNECTING/CONNECTED. No public Reconnecting enum
        // in 2.4.0 — we bucket CONNECTING (COVER-R for our badge) and
        // treat DISCONNECTED as Offline.
        client.realtime.status.collectSafely(scope) { status ->
            _connectionState.value = when (status) {
                io.github.jan.supabase.realtime.Realtime.Status.CONNECTED ->
                    ConnectionState.Connected
                io.github.jan.supabase.realtime.Realtime.Status.CONNECTING ->
                    ConnectionState.Connecting
                io.github.jan.supabase.realtime.Realtime.Status.DISCONNECTED ->
                    ConnectionState.Disconnected
                else -> ConnectionState.Disconnected
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
            if (ch.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) return ch
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

    suspend fun startListeningForPongs() {
        try {
            val channel = getOrCreateCommandsChannel()
            scope.launch {
                channel.broadcastFlow<CommandPayload>(event = "pong").collect { pong ->
                    Log.d("SupabaseClient", "Pong received from ${pong.device_id}")
                    _pingFlow.emit(pong.device_id)
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "startListeningForPongs failed", e)
        }
    }

    suspend fun connectRealtime() {
        client.realtime.connect()
    }

    suspend fun disconnectRealtime() {
        client.realtime.disconnect()
    }

    /**
     * Ponytail: device id strings contain literal spaces (e.g. "Samsung
     * SM-S908B"). PostgREST realtime filter values must use RFC 3986 percent
     * encoding, where spaces are `%20`. Java's [URLEncoder] produces
     * `application/x-www-form-urlencoded` style where spaces are `+` —
     * PostgREST does not decode `+` to a space, so without this replace
     * filter subscribes silently no-op.
     */
    internal fun encodeFilterValue(raw: String): String =
        URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")

    // --- Per-device wake/sleep ---

    suspend fun sendWakeCommand(deviceId: String) {
        sendCommand(deviceId, "wake")
    }

    suspend fun sendSleepCommand(deviceId: String) {
        sendCommand(deviceId, "sleep")
    }

    suspend fun pingDevice(deviceId: String): Long? {
        val start = System.currentTimeMillis()
        return try {
            withTimeout(10_000) {
                val pingDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    _pingFlow.first { it == deviceId }
                }
                
                sendCommand(deviceId, "ping")
                
                pingDeferred.await()
                System.currentTimeMillis() - start
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            Log.e("SupabaseClient", "pingDevice failed", e)
            null
        }
    }

    suspend fun sendCommand(deviceId: String, command: String, params: String? = null) {
        try {
            val channel = getOrCreateCommandsChannel()
            channel.broadcast(
                event = "command",
                message = CommandPayload(device_id = deviceId, command = command, params = params, status = "pending")
            )
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Broadcast to commands failed ($command for $deviceId)", e)
            throw e
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
        return try {
            // Ponytail: Only fetch columns we actually need for the dashboard.
            // This prevents fetching heavy metadata/audit columns and avoids hitting the timeout.
            val result = client.postgrest["locations"].select(
                Columns.list("device_id", "latitude", "longitude", "accuracy", "bearing", "created_at")
            ) {
                order("created_at", order = Order.DESCENDING)
                limit(500)
            }
            val allLocs = result.decodeList<LocationData>()
            // group by device_id and take the most recent
            allLocs.groupBy { it.device_id }.map { it.value.first() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SupabaseClient", "getInitialLocations failed", e)
            emptyList()
        }
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
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectSafely(
    scope: CoroutineScope,
    block: suspend (T) -> Unit,
) {
    scope.launch {
        try {
            collect { block(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SupabaseClient", "realtime state collector died", e)
        }
    }
}
