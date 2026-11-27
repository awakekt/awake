/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android low-latency audio player built on [AudioTrack] for sound effects and streaming
 * [android.media.MediaPlayer] / `AudioTrack.MODE_STREAM` for background music.
 * Supports stereo and mono PCM clips, compressed music streaming, volume controls, dynamic stereo
 * panning, and loop modes.
 *
 * @param maxConcurrentOneShots Maximum number of concurrent non-looping sound effects before the
 *   oldest active one-shot track is evicted and released.
 * @param streamOpener Internal factory seam for opening streaming music handles in host tests.
 */
class AndroidAudioPlayer internal constructor(
    maxConcurrentOneShots: Int,
    private val streamOpener: (MusicStreamSource, Float, Boolean) -> AndroidMusicStreamHandle?,
) : AudioPlayer {
    @JvmOverloads
    constructor(
        maxConcurrentOneShots: Int = OneShotTrackReaper.DEFAULT_MAX_CONCURRENT_ONE_SHOTS,
    ) : this(maxConcurrentOneShots, ::openAndroidMediaPlayerStream)

    override var masterVolume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateActiveHandles()
            updateMusicVolume()
        }

    override var sfxVolume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateActiveHandles()
        }

    override var musicVolume: Float = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateMusicVolume()
        }

    private val activeHandles = CopyOnWriteArrayList<AndroidSoundHandle>()
    private val reaperLock = Any()
    private val oneShotReaper = OneShotTrackReaper(
        nowMs = SystemClock::elapsedRealtime,
        maxConcurrent = maxConcurrentOneShots,
    )
    private var activeMusicHandle: AndroidSoundHandle? = null
    private var activeMusicStream: AndroidMusicStreamHandle? = null
    private var activeMusicVolume: Float = 1f

    @Volatile
    private var isDisposed = false

    override fun playSound(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
    ): SoundHandle? {
        if (isDisposed || !clip.isValidForPlayback()) return null

        synchronized(reaperLock) {
            if (loop) {
                oneShotReaper.reap()
            } else {
                oneShotReaper.reserveSlot()
            }
        }

        val handle = createTrackHandle(
            clip = clip,
            volume = volume,
            pan = pan,
            loop = loop,
            isMusic = false,
        )
        return handle?.let { created ->
            activeHandles.add(created)
            created.play()
            val disposedDuringStart = synchronized(reaperLock) {
                if (!isDisposed && !loop) {
                    oneShotReaper.track(created, clip.durationMillis())
                }
                isDisposed
            }
            if (disposedDuringStart) {
                created.stop()
                null
            } else {
                created
            }
        }
    }

    override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {
        stopMusic()
        if (isDisposed || !clip.isValidForPlayback()) return

        activeMusicVolume = volume.coerceIn(0f, 1f)
        if (clip.pcmBytes.size > MAX_STATIC_MUSIC_BYTES) {
            openAndroidPcmMusicStream(clip, effectiveMusicVolume(), loop)?.let { stream ->
                activeMusicStream = stream
                if (isDisposed) stopMusic()
            }
        } else {
            createTrackHandle(clip = clip, volume = volume, pan = 0f, loop = loop, isMusic = true)?.let { handle ->
                activeMusicHandle = handle
                handle.play()
            }
        }
    }

    override fun playMusicStream(source: MusicStreamSource, volume: Float, loop: Boolean): Boolean {
        if (isDisposed || !source.isValidSource()) return false
        stopMusic()
        activeMusicVolume = volume.coerceIn(0f, 1f)
        val stream = streamOpener(source, effectiveMusicVolume(), loop)
        val started = stream != null && !isDisposed
        if (started) {
            activeMusicStream = stream
        } else {
            stream?.stop()
        }
        return started
    }

    override fun stopMusic() {
        activeMusicHandle?.stop()
        activeMusicHandle = null
        activeMusicStream?.stop()
        activeMusicStream = null
        synchronized(reaperLock) {
            oneShotReaper.reap()
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true

        stopMusic()
        synchronized(reaperLock) {
            oneShotReaper.stopAll()
        }
        for (handle in activeHandles) {
            handle.stop()
        }
        activeHandles.clear()
    }

    private fun createTrackHandle(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
        isMusic: Boolean,
    ): AndroidSoundHandle? {
        return runCatching {
            val channelConfig = if (clip.channels == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }

            val encoding = if (clip.bitsPerSample == 8) {
                AudioFormat.ENCODING_PCM_8BIT
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            val format = AudioFormat.Builder()
                .setSampleRate(clip.sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(encoding)
                .build()

            val usage = if (isMusic) {
                AudioAttributes.USAGE_MEDIA
            } else {
                AudioAttributes.USAGE_GAME
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(clip.pcmBytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            val written = track.write(clip.pcmBytes, 0, clip.pcmBytes.size)
            if (written < 0) {
                track.release()
                return null
            }

            if (loop) {
                val frameSize = (clip.bitsPerSample / 8) * clip.channels
                val totalFrames = if (frameSize > 0) clip.pcmBytes.size / frameSize else 0
                if (totalFrames > 0) {
                    track.setLoopPoints(0, totalFrames, -1)
                }
            }

            AndroidSoundHandle(
                track = track,
                isMusic = isMusic,
                userVolume = volume,
                userPan = pan,
            )
        }.getOrNull()
    }

    private fun updateActiveHandles() {
        for (handle in activeHandles) {
            handle.applyCurrentVolumes()
        }
    }

    private fun effectiveMusicVolume(): Float =
        (masterVolume * musicVolume * activeMusicVolume).coerceIn(0f, 1f)

    private fun updateMusicVolume() {
        activeMusicHandle?.applyCurrentVolumes()
        activeMusicStream?.setVolume(effectiveMusicVolume())
    }

    private inner class AndroidSoundHandle(
        private val track: AudioTrack,
        private val isMusic: Boolean,
        private var userVolume: Float,
        private var userPan: Float,
    ) : SoundHandle {
        private val stopped = AtomicBoolean(false)

        init {
            applyCurrentVolumes()
        }

        fun play() {
            runCatching {
                if (!stopped.get() && track.state == AudioTrack.STATE_INITIALIZED) {
                    track.play()
                }
            }
        }

        override val isPlaying: Boolean
            get() = runCatching {
                !stopped.get() &&
                    track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING
            }.getOrDefault(false)

        override fun stop() {
            if (!stopped.compareAndSet(false, true)) return
            runCatching {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                }
            }
            if (!isMusic) {
                activeHandles.remove(this)
            }
        }

        override fun update(volume: Float, pan: Float) {
            userVolume = volume
            userPan = pan
            applyCurrentVolumes()
        }

        @Suppress("DEPRECATION")
        fun applyCurrentVolumes() {
            if (stopped.get() || track.state != AudioTrack.STATE_INITIALIZED) return
            val busVolume = if (isMusic) musicVolume else sfxVolume
            val effectiveVolume = (masterVolume * busVolume * userVolume).coerceIn(0f, 1f)

            // Equal-power stereo panning curve or linear panning between left and right channels
            val panClamped = userPan.coerceIn(-1f, 1f)
            val leftGain = (effectiveVolume * (1f - panClamped.coerceAtLeast(0f))).coerceIn(0f, 1f)
            val rightGain = (effectiveVolume * (1f + panClamped.coerceAtMost(0f))).coerceIn(0f, 1f)

            runCatching {
                track.setStereoVolume(leftGain, rightGain)
            }
        }
    }
}

private fun AudioClip.isValidForPlayback(): Boolean =
    pcmBytes.isNotEmpty() &&
        sampleRate > 0 &&
        channels in 1..2 &&
        bitsPerSample in listOf(8, 16)
