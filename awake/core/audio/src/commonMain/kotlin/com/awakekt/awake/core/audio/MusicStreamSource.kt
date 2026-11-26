/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

/**
 * Encoded audio source for streaming background music without inflating full uncompressed PCM
 * waveforms into heap or static native audio buffers.
 *
 * Platform [AudioPlayer] implementations decode incrementally (for example Android `MediaPlayer`
 * with `MediaDataSource`, JVM `SourceDataLine`, or browser `HTMLAudioElement`) and honor
 * [AudioPlayer.masterVolume], [AudioPlayer.musicVolume], [AudioPlayer.stopMusic], and
 * [AudioPlayer.dispose].
 */
sealed interface MusicStreamSource {
    /** Stable identifier for diagnostics and deduplication (typically the project asset path). */
    val id: String

    /** Optional MIME type hint such as `"audio/mpeg"`, `"audio/ogg"`, or `"audio/wav"`. */
    val mimeType: String?

    /**
     * In-memory encoded audio bytes (for example `.mp3`, `.ogg`, `.m4a`, or `.wav` bytes read from
     * an asset source) streamed without decoding the entire track into an uncompressed PCM array.
     *
     * @property id Stable identifier or asset path for the track.
     * @property bytes Raw encoded container bytes.
     * @property mimeType Optional MIME type hint inferred from [id] by default.
     */
    class EncodedBytes(
        override val id: String,
        val bytes: ByteArray,
        override val mimeType: String? = inferAudioMimeType(id),
    ) : MusicStreamSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncodedBytes) return false
            return id == other.id && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + (mimeType?.hashCode() ?: 0)
            result = 31 * result + bytes.contentHashCode()
            return result
        }

        override fun toString(): String = "EncodedBytes(id=$id, bytes=${bytes.size}, mimeType=$mimeType)"
    }

    /**
     * Platform file path or media URL for zero-heap streaming directly from storage or the browser
     * media pipeline.
     *
     * @property id Stable identifier for the track.
     * @property path Platform file path (Android/Desktop) or media URL (Web).
     * @property mimeType Optional MIME type hint inferred from [path] by default.
     */
    data class FilePath(
        override val id: String,
        val path: String,
        override val mimeType: String? = inferAudioMimeType(path),
    ) : MusicStreamSource
}

/**
 * Infers a standard audio MIME type from the filename extension of [pathOrId], ignoring any URL
 * query string or fragment, or returns `null` when the extension is unrecognized.
 */
fun inferAudioMimeType(pathOrId: String): String? {
    val clean = pathOrId.substringBefore('?').substringBefore('#')
    val dot = clean.lastIndexOf('.')
    val slash = maxOf(clean.lastIndexOf('/'), clean.lastIndexOf('\\'))
    val extension = if (dot > slash) clean.substring(dot + 1).lowercase() else ""
    return when (extension) {
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> null
    }
}

internal fun MusicStreamSource.isValidSource(): Boolean = when (this) {
    is MusicStreamSource.EncodedBytes -> bytes.isNotEmpty()
    is MusicStreamSource.FilePath -> path.isNotBlank()
}
