/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.audio

import kotlin.js.JsAny

/**
 * Browser audio playback through Web Audio, for mono/stereo signed 16-bit little-endian PCM.
 *
 * Install before the first user interaction. Trusted pointer and keyboard events resume the
 * browser's suspended audio context; sounds queued before that interaction begin once unlocked.
 * Volume changes affect active sounds, including looping sources and background music.
 * [dispose] stops all sources, removes the gesture listeners and closes the owned context.
 */
class WebAudioPlayer internal constructor(
    private val context: JsAny,
    output: JsAny = audioDestination(context),
    private val ownsContext: Boolean = false,
) : AudioPlayer {
    /** Creates a browser AudioContext and installs its user-gesture unlock handlers. */
    constructor() : this(createAudioContext(), ownsContext = true)

    private val graph = createAudioGraph(context, output)
    private val unlock = if (ownsContext) installAudioUnlock(context) else null
    private val buffers = mutableMapOf<AudioClip, JsAny>()
    private val sounds = mutableSetOf<WebSoundHandle>()
    private var music: WebSoundHandle? = null
    private var disposed = false

    override var masterVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            setAudioGain(graph.master, field)
        }

    override var sfxVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            setAudioGain(graph.sfx, field)
        }

    override var musicVolume: Float = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            setAudioGain(graph.music, field)
        }

    init {
        setAudioGain(graph.music, musicVolume)
    }

    override fun playSound(clip: AudioClip, volume: Float, pan: Float, loop: Boolean): SoundHandle? =
        play(clip, graph.sfx, volume, pan, loop)

    override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {
        stopMusic()
        music = play(clip, graph.music, volume, 0f, loop)
    }

    override fun stopMusic() {
        music?.stop()
        music = null
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        sounds.toList().forEach { it.stop() }
        music = null
        buffers.clear()
        unlock?.let(::removeAudioUnlock)
        disconnectAudioGraph(graph)
        if (ownsContext) closeAudioContext(context)
    }

    private fun play(clip: AudioClip, bus: JsAny, volume: Float, pan: Float, loop: Boolean): WebSoundHandle? {
        if (disposed || !clip.supportsWebAudio()) return null
        val buffer = buffers.getOrPut(clip) { createPcmBuffer(context, clip) }
        val sound = WebSoundHandle(buffer, bus, volume, pan, loop)
        sounds.add(sound)
        return sound
    }

    private inner class WebSoundHandle(buffer: JsAny, bus: JsAny, volume: Float, pan: Float, loop: Boolean) : SoundHandle {
        private val voice = startAudioVoice(context, buffer, bus, volume.coerceIn(0f, 1f), pan.coerceIn(-1f, 1f), loop) {
            sounds.remove(this)
        }

        override val isPlaying: Boolean get() = voice.playing

        override fun update(volume: Float, pan: Float) {
            updateAudioVoice(voice, volume.coerceIn(0f, 1f), pan.coerceIn(-1f, 1f))
        }

        override fun stop() = stopAudioVoice(voice)
    }
}

private fun AudioClip.supportsWebAudio(): Boolean =
    bitsPerSample == 16 && channels in 1..2 && sampleRate in 3_000..384_000 &&
        pcmBytes.isNotEmpty() && pcmBytes.size % (channels * 2) == 0

private fun createPcmBuffer(context: JsAny, clip: AudioClip): JsAny =
    createAudioBuffer(context, clip.channels, clip.pcmBytes.size / (clip.channels * 2), clip.sampleRate) { frame, channel ->
        val offset = (frame * clip.channels + channel) * 2
        val sample = (clip.pcmBytes[offset].toInt() and 0xFF) or (clip.pcmBytes[offset + 1].toInt() shl 8)
        sample.toShort().toDouble() / 32768.0
    }
