/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaDataSource
import android.media.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Maximum PCM byte length played via `AudioTrack.MODE_STATIC` for music before switching to `MODE_STREAM`. */
internal const val MAX_STATIC_MUSIC_BYTES: Int = 512 * 1024

private const val PCM_STREAM_CHUNK_BYTES: Int = 16 * 1024
private const val PCM_STREAM_MIN_BUFFER_BYTES: Int = 32 * 1024

/** Control handle for an active streaming Android music track. */
internal interface AndroidMusicStreamHandle {
    val isPlaying: Boolean
    fun setVolume(effectiveVolume: Float)
    fun stop()
}

/**
 * Pure-Kotlin bounds-checked slice copy backing [ByteArrayMediaDataSource.readAt].
 * Returns `-1` on EOF or invalid position, `0` when [size] is non-positive, or the number of bytes copied.
 */
internal fun readMediaSlice(
    source: ByteArray,
    position: Long,
    destination: ByteArray,
    offset: Int,
    size: Int,
): Int {
    val invalidBounds = position < 0L || offset < 0 || offset > destination.size
    return when {
        invalidBounds || position >= source.size.toLong() -> -1
        size <= 0 -> 0
        else -> {
            val remainingSource = (source.size.toLong() - position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val remainingDest = destination.size - offset
            val count = minOf(size, remainingSource, remainingDest)
            if (count <= 0) {
                0
            } else {
                source.copyInto(
                    destination = destination,
                    destinationOffset = offset,
                    startIndex = position.toInt(),
                    endIndex = position.toInt() + count,
                )
                count
            }
        }
    }
}

/** Zero-copy [MediaDataSource] streaming directly from an in-memory encoded [ByteArray]. */
internal class ByteArrayMediaDataSource(
    private val bytes: ByteArray,
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        readMediaSlice(bytes, position, buffer, offset, size)

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}

/**
 * Opens an Android [MediaPlayer] streaming compressed audio (`.mp3`, `.ogg`, `.m4a`, `.wav`, etc.)
 * directly from [source] without expanding uncompressed PCM onto the Java heap or AudioFlinger static memory.
 */
internal fun openAndroidMediaPlayerStream(
    source: MusicStreamSource,
    effectiveVolume: Float,
    loop: Boolean,
): AndroidMusicStreamHandle? {
    if (!source.isValidSource()) return null
    var player: MediaPlayer? = null
    return runCatching {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val created = MediaPlayer().also { player = it }
        created.setAudioAttributes(attributes)
        when (source) {
            is MusicStreamSource.EncodedBytes -> created.setDataSource(ByteArrayMediaDataSource(source.bytes))
            is MusicStreamSource.FilePath -> created.setDataSource(source.path)
        }
        created.isLooping = loop
        created.prepare()
        val gain = effectiveVolume.coerceIn(0f, 1f)
        created.setVolume(gain, gain)
        created.start()
        AndroidMediaPlayerHandle(created)
    }.getOrElse {
        runCatching { player?.release() }
        null
    }
}

/**
 * Streams a large uncompressed [AudioClip] through `AudioTrack.MODE_STREAM` using a compact ring
 * buffer instead of pinning the full waveform in static AudioFlinger shared memory.
 */
internal fun openAndroidPcmMusicStream(
    clip: AudioClip,
    effectiveVolume: Float,
    loop: Boolean,
): AndroidMusicStreamHandle? = runCatching {
    val channelConfig = if (clip.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
    val encoding = if (clip.bitsPerSample == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
    val minBuffer = AudioTrack.getMinBufferSize(clip.sampleRate, channelConfig, encoding)
    val bufferBytes = maxOf(minBuffer, PCM_STREAM_MIN_BUFFER_BYTES)
    val format = AudioFormat.Builder()
        .setSampleRate(clip.sampleRate)
        .setChannelMask(channelConfig)
        .setEncoding(encoding)
        .build()
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    val track = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(format)
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
        track.release()
        return null
    }
    track.setVolume(effectiveVolume.coerceIn(0f, 1f))
    track.play()
    AndroidPcmStreamHandle(track, clip.pcmBytes, loop)
}.getOrNull()

private class AndroidMediaPlayerHandle(
    private val player: MediaPlayer,
) : AndroidMusicStreamHandle {
    private val stopped = AtomicBoolean(false)

    override val isPlaying: Boolean
        get() = !stopped.get() && runCatching { player.isPlaying }.getOrDefault(false)

    override fun setVolume(effectiveVolume: Float) {
        if (stopped.get()) return
        val gain = effectiveVolume.coerceIn(0f, 1f)
        runCatching { player.setVolume(gain, gain) }
    }

    override fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching {
            if (player.isPlaying) player.stop()
        }
        runCatching { player.release() }
    }
}

private class AndroidPcmStreamHandle(
    private val track: AudioTrack,
    private val pcm: ByteArray,
    private val loop: Boolean,
) : AndroidMusicStreamHandle {
    private val stopped = AtomicBoolean(false)

    // Two owners (the handle and the feeder thread) guarantee track.release() runs once after both finish.
    private val releaseRefs = AtomicInteger(2)
    private val writerThread = Thread(::pumpPcm, "awake-android-pcm-music").apply {
        isDaemon = true
        start()
    }

    override val isPlaying: Boolean
        get() = !stopped.get() && runCatching {
            track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING
        }.getOrDefault(false)

    override fun setVolume(effectiveVolume: Float) {
        if (stopped.get()) return
        runCatching { track.setVolume(effectiveVolume.coerceIn(0f, 1f)) }
    }

    override fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.pause()
                track.flush()
                track.stop()
            }
        }
        writerThread.interrupt()
        releaseWhenDone()
    }

    private fun pumpPcm() {
        try {
            var offset = 0
            while (!stopped.get() && offset < pcm.size) {
                val count = minOf(PCM_STREAM_CHUNK_BYTES, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
                if (offset >= pcm.size && loop) offset = 0
            }
        } catch (_: Throwable) {
            // Interrupted or AudioTrack stopped during teardown.
        } finally {
            stopped.set(true)
            releaseWhenDone()
        }
    }

    private fun releaseWhenDone() {
        if (releaseRefs.decrementAndGet() == 0) {
            runCatching { track.release() }
        }
    }
}
