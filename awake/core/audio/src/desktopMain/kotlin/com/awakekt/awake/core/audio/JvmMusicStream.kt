/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.Line
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

private const val JVM_STREAM_CHUNK_BYTES: Int = 16 * 1024
private const val JVM_STREAM_LINE_BUFFER_BYTES: Int = 32 * 1024
private const val PCM_BITS: Int = 16
private const val BYTES_PER_SAMPLE_16: Int = 2
private const val MIN_LINEAR_GAIN: Float = 0.0001f
private const val DECIBEL_SCALE: Double = 20.0

/**
 * Streaming JVM background music playback over [SourceDataLine] so long tracks decode and play in
 * fixed 16 KiB chunks rather than holding an entire uncompressed [javax.sound.sampled.Clip].
 */
internal class JvmMusicStream private constructor(
    private val source: MusicStreamSource,
    private val pcmFormat: AudioFormat,
    private val line: SourceDataLine,
    initialStream: AudioInputStream,
    private val loop: Boolean,
) {
    private val stopped = AtomicBoolean(false)
    private val releaseRefs = AtomicInteger(2)

    @Volatile
    private var currentStream: AudioInputStream? = initialStream

    private val worker = Thread(::pumpStream, "awake-jvm-music-stream").apply {
        isDaemon = true
        start()
    }

    val isPlaying: Boolean
        get() = !stopped.get() && line.isOpen

    fun setVolume(effectiveVolume: Float) {
        if (!stopped.get() && line.isOpen) {
            applyLineGain(line, effectiveVolume.coerceIn(0f, 1f))
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching {
            if (line.isRunning) line.stop()
            line.flush()
        }
        worker.interrupt()
        releaseWhenDone()
    }

    private fun pumpStream() {
        val chunk = ByteArray(JVM_STREAM_CHUNK_BYTES)
        var stream: AudioInputStream? = currentStream
        try {
            while (!stopped.get() && stream != null) {
                val read = stream.read(chunk, 0, chunk.size)
                if (read > 0) {
                    line.write(chunk, 0, read)
                } else {
                    runCatching { stream.close() }
                    stream = if (loop && !stopped.get()) openPcmStream(source, pcmFormat) else null
                    currentStream = stream
                }
            }
        } catch (_: Exception) {
            // Stream closed or line stopped during teardown.
        } finally {
            stopped.set(true)
            runCatching { stream?.close() }
            currentStream = null
            releaseWhenDone()
        }
    }

    private fun releaseWhenDone() {
        if (releaseRefs.decrementAndGet() == 0) {
            runCatching {
                if (line.isRunning) line.stop()
                if (line.isOpen) line.close()
            }
        }
    }

    companion object {
        fun open(source: MusicStreamSource, initialVolume: Float, loop: Boolean): JvmMusicStream? {
            if (!source.isValidSource()) return null
            var stream: AudioInputStream? = null
            var line: SourceDataLine? = null
            return runCatching {
                val raw = openRawStream(source)
                if (raw == null) {
                    null
                } else {
                    val targetFormat = toPcm16Format(raw.format)
                    val decoded = if (raw.format.matches(targetFormat)) {
                        raw
                    } else {
                        AudioSystem.getAudioInputStream(targetFormat, raw)
                    }
                    stream = decoded
                    val info = DataLine.Info(SourceDataLine::class.java, targetFormat)
                    val openedLine = (AudioSystem.getLine(info) as SourceDataLine).also { line = it }
                    openedLine.open(targetFormat, JVM_STREAM_LINE_BUFFER_BYTES)
                    applyLineGain(openedLine, initialVolume.coerceIn(0f, 1f))
                    openedLine.start()
                    JvmMusicStream(source, targetFormat, openedLine, decoded, loop)
                }
            }.getOrElse {
                runCatching { stream?.close() }
                runCatching { line?.close() }
                null
            }
        }
    }
}

internal fun applyLineGain(line: Line, linearVolume: Float) {
    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        val gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val dB = (DECIBEL_SCALE * log10(linearVolume.coerceAtLeast(MIN_LINEAR_GAIN).toDouble())).toFloat()
        gainControl.value = dB.coerceIn(gainControl.minimum, gainControl.maximum)
    }
}

private fun openRawStream(source: MusicStreamSource): AudioInputStream? = when (source) {
    is MusicStreamSource.EncodedBytes -> AudioSystem.getAudioInputStream(ByteArrayInputStream(source.bytes))
    is MusicStreamSource.FilePath -> {
        val file = File(source.path)
        if (!file.isFile) null else AudioSystem.getAudioInputStream(BufferedInputStream(file.inputStream()))
    }
}

private fun openPcmStream(source: MusicStreamSource, targetFormat: AudioFormat): AudioInputStream? = runCatching {
    val raw = openRawStream(source) ?: return null
    if (raw.format.matches(targetFormat)) raw else AudioSystem.getAudioInputStream(targetFormat, raw)
}.getOrNull()

private fun toPcm16Format(base: AudioFormat): AudioFormat {
    val channels = base.channels.coerceAtLeast(1)
    val sampleRate = if (base.sampleRate > 0f) base.sampleRate else 44_100f
    return AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate,
        PCM_BITS,
        channels,
        channels * BYTES_PER_SAMPLE_16,
        sampleRate,
        false,
    )
}
