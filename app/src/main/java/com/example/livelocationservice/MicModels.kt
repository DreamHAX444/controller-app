package com.example.livelocationservice

import kotlinx.serialization.Serializable

/**
 * Live `media` row shape the tracker inserts per audio chunk.
 *
 * Audio bytes are base64-encoded into `data_b64` because the live project
 * has zero Storage buckets — there is no separate `mic-audio` bucket to
 * land a raw PCM payload in. Per-chunk ordering is by `created_at`
 * (rows for the same `device_id` are insert-ordered for a single source).
 */
@Serializable
data class MediaUploadRow(
    val device_id: String,
    val type: String,             // "audio"
    val data_b64: String,          // base64(LE int16 mono PCM, 16 kHz)
    val mime_type: String? = null, // "audio/pcm"
    val duration_sec: Int? = null, // 250 ms rounds to 0; keep for parity
)
