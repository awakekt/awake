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
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android low-latency audio player built on [AudioTrack] for sound effects and streaming
 * [android.media.MediaPlayer] / `AudioTrack.MODE_STREAM` for background music.
 * Supports stereo and mono PCM clips, compressed music streaming, volume controls, dynamic stereo
 * panning, and loop modes.
 *
 * Music stream open and teardown run off the caller thread on a dedicated single-thread executor
 * so `MediaPlayer.prepare()` / `release()` Binder IPC never stalls frame rendering.
 *
 * @param maxConcurrentOneShots Maximum number of concurrent non-looping sound effects before the
 *   oldest active one-shot track is evicted and released.
 * @param streamOpener Internal factory seam for opening streaming music handles in host tests.
 * @param runMusicTask Internal scheduling seam for music stream open/stop tasks.
 * @param shutdownMusicExecutor Internal cleanup hook invoked when this player is disposed.
 * @param synchronousMusic Whether [runMusicTask] executes synchronously on the calling thread.
 */
class AndroidAudioPlayer internal constructor(
    maxConcurrentOneShots: Int,
    private val streamOpener: (MusicStreamSource, Float, Boolean) -> AndroidMusicStreamHandle?,
    private val runMusicTask: (() -> Unit) -> Unit,
    private val shutdownMusicExecutor: () -> Unit,
    private val synchronousMusic: Boolean,
) : AudioPlayer {
    private constructor(
        maxConcurrentOneShots: Int,
        streamOpener: (MusicStreamSource, Float, Boolean) -> AndroidMusicStreamHandle?,
        musicExecutor: ExecutorService,
    ) : this(
        maxConcurrentOneShots = maxConcurrentOneShots,
        streamOpener = streamOpener,
        runMusicTask = { task ->
            runCatching { musicExecutor.execute(task) }
        },
        shutdownMusicExecutor = { musicExecutor.shutdown() },
        synchronousMusic = false,
    )

    @JvmOverloads
    constructor(
        maxConcurrentOneShots: Int = OneShotTrackReaper.DEFAULT_MAX_CONCURRENT_ONE_SHOTS,
    ) : this(maxConcurrentOneShots, ::openAndroidMediaPlayerStream, createMusicExecutor())

    internal constructor(
        maxConcurrentOneShots: Int,
        streamOpener: (MusicStreamSource, Float, Boolean) -> AndroidMusicStreamHandle?,
    ) : this(
        maxConcurrentOneShots = maxConcurrentOneShots,
        streamOpener = streamOpener,
        runMusicTask = { task -> task() },
        shutdownMusicExecutor = {},
        synchronousMusic = true,
    )

    internal constructor(
        maxConcurrentOneShots: Int,
        streamOpener: (MusicStreamSource, Float, Boolean) -> AndroidMusicStreamHandle?,
        runMusicTask: (() -> Unit) -> Unit,
    ) : this(
        maxConcurrentOneShots = maxConcurrentOneShots,
        streamOpener = streamOpener,
        runMusicTask = runMusicTask,
        shutdownMusicExecutor = {},
        synchronousMusic = false,
    )

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
    private val musicLock = Any()
    private val oneShotReaper = OneShotTrackReaper(
        nowMs = SystemClock::elapsedRealtime,
        maxConcurrent = maxConcurrentOneShots,
    )
    private var activeMusicHandle: AndroidSoundHandle? = null
    private var activeMusicStream: AndroidMusicStreamHandle? = null
    private var activeMusicVolume: Float = 1f
    private var musicGeneration: Long = 0L

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

        val clampedVolume = volume.coerceIn(0f, 1f)
        val generation: Long
        val initialEffectiveVolume: Float
        synchronized(musicLock) {
            if (isDisposed) return
            generation = ++musicGeneration
            activeMusicVolume = clampedVolume
            initialEffectiveVolume = effectiveMusicVolume()
        }
        if (clip.pcmBytes.size > MAX_STATIC_MUSIC_BYTES) {
            runMusicTask {
                val stillCurrent = synchronized(musicLock) { !isDisposed && musicGeneration == generation }
                if (!stillCurrent) return@runMusicTask
                val stream = openAndroidPcmMusicStream(clip, initialEffectiveVolume, loop) ?: return@runMusicTask
                attachOpenedMusicStream(stream, generation, initialEffectiveVolume)
            }
        } else {
            createTrackHandle(clip = clip, volume = clampedVolume, pan = 0f, loop = loop, isMusic = true)?.let { handle ->
                val keep = synchronized(musicLock) {
                    if (!isDisposed && musicGeneration == generation) {
                        activeMusicHandle = handle
                        true
                    } else {
                        false
                    }
                }
                if (keep) {
                    handle.play()
                } else {
                    handle.stop()
                }
            }
        }
    }

    override fun playMusicStream(source: MusicStreamSource, volume: Float, loop: Boolean): Boolean {
        var previousHandle: AndroidSoundHandle? = null
        var previousStream: AndroidMusicStreamHandle? = null
        var generation = 0L
        var initialVolume = 0f
        val accepted = !isDisposed && source.isValidSource() && synchronized(musicLock) {
            if (isDisposed) {
                false
            } else {
                generation = ++musicGeneration
                previousHandle = activeMusicHandle
                activeMusicHandle = null
                previousStream = activeMusicStream
                activeMusicStream = null
                activeMusicVolume = volume.coerceIn(0f, 1f)
                initialVolume = effectiveMusicVolume()
                true
            }
        }
        if (!accepted) return false
        previousHandle?.stop()
        synchronized(reaperLock) {
            oneShotReaper.reap()
        }
        var syncStarted = true
        runMusicTask {
            previousStream?.stop()
            val stillCurrent = synchronized(musicLock) { !isDisposed && musicGeneration == generation }
            val opened = if (stillCurrent) streamOpener(source, initialVolume, loop) else null
            val kept = opened != null && attachOpenedMusicStream(opened, generation, initialVolume)
            if (synchronousMusic) syncStarted = kept
        }
        return !synchronousMusic || syncStarted
    }

    override fun stopMusic() {
        val previousHandle: AndroidSoundHandle?
        val previousStream: AndroidMusicStreamHandle?
        synchronized(musicLock) {
            musicGeneration++
            previousHandle = activeMusicHandle
            activeMusicHandle = null
            previousStream = activeMusicStream
            activeMusicStream = null
        }
        previousHandle?.stop()
        if (previousStream != null) {
            runMusicTask { previousStream.stop() }
        }
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
        shutdownMusicExecutor()
    }

    private fun attachOpenedMusicStream(
        stream: AndroidMusicStreamHandle,
        generation: Long,
        openedVolume: Float,
    ): Boolean {
        var latestVolume = openedVolume
        val keep = synchronized(musicLock) {
            if (!isDisposed && musicGeneration == generation) {
                activeMusicStream = stream
                latestVolume = effectiveMusicVolume()
                true
            } else {
                false
            }
        }
        if (!keep) {
            stream.stop()
            return false
        }
        if (latestVolume != openedVolume) {
            stream.setVolume(latestVolume)
        }
        return true
    }

    private fun createTrackHandle(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
        isMusic: Boolean,
    ): AndroidSoundHandle? = runCatching {
        val frameSize = (clip.bitsPerSample / 8) * clip.channels
        val alignedSize = if (frameSize > 0) (clip.pcmBytes.size / frameSize) * frameSize else 0
        val track = if (alignedSize > 0) buildStaticTrack(clip, alignedSize, isMusic) else null
        if (track == null) {
            null
        } else {
            if (loop) {
                val totalFrames = alignedSize / frameSize
                if (totalFrames > 0) track.setLoopPoints(0, totalFrames, -1)
            }
            AndroidSoundHandle(
                track = track,
                isMusic = isMusic,
                userVolume = volume,
                userPan = pan,
            )
        }
    }.onFailure { error ->
        logTrackWarning("AudioTrack creation failed for clip '${clip.id}': ${error.message}")
    }.getOrNull()

    private fun buildStaticTrack(clip: AudioClip, alignedSize: Int, isMusic: Boolean): AudioTrack? {
        val format = AudioFormat.Builder()
            .setSampleRate(clip.sampleRate)
            .setChannelMask(if (clip.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(if (clip.bitsPerSample == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(if (isMusic) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(alignedSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        val valid = track.state == AudioTrack.STATE_INITIALIZED && track.write(clip.pcmBytes, 0, alignedSize) > 0
        return if (valid) {
            track
        } else {
            track.release()
            logTrackWarning("AudioTrack init/write failed for clip '${clip.id}' (bytes=$alignedSize, rate=${clip.sampleRate}, ch=${clip.channels})")
            null
        }
    }

    private fun updateActiveHandles() {
        for (handle in activeHandles) {
            handle.applyCurrentVolumes()
        }
    }

    private fun effectiveMusicVolume(): Float =
        (masterVolume * musicVolume * activeMusicVolume).coerceIn(0f, 1f)

    private fun updateMusicVolume() {
        val handle: AndroidSoundHandle?
        val stream: AndroidMusicStreamHandle?
        val volume: Float
        synchronized(musicLock) {
            handle = activeMusicHandle
            stream = activeMusicStream
            volume = effectiveMusicVolume()
        }
        handle?.applyCurrentVolumes()
        stream?.setVolume(volume)
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

            val panClamped = userPan.coerceIn(-1f, 1f)
            runCatching {
                if (panClamped == 0f) {
                    track.setVolume(effectiveVolume)
                } else {
                    // Linear balance between left and right channels when spatially panned.
                    val leftGain = (effectiveVolume * (1f - panClamped.coerceAtLeast(0f))).coerceIn(0f, 1f)
                    val rightGain = (effectiveVolume * (1f + panClamped.coerceAtMost(0f))).coerceIn(0f, 1f)
                    track.setStereoVolume(leftGain, rightGain)
                }
            }
        }
    }
}

private const val ANDROID_AUDIO_LOG_TAG = "AwakeAudio"

private fun logTrackWarning(message: String) {
    runCatching { Log.w(ANDROID_AUDIO_LOG_TAG, message) }
}

private fun createMusicExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "awake-android-music-io").apply { isDaemon = true }
}

private fun AudioClip.isValidForPlayback(): Boolean =
    pcmBytes.isNotEmpty() &&
        sampleRate > 0 &&
        channels in 1..2 &&
        bitsPerSample in listOf(8, 16)
