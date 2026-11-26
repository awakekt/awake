/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import com.awakekt.awake.core.math.Vec3f

/**
 * Decorator around an [AudioPlayer] that mutes sound effects while the application window is
 * unfocused and automatically pauses/resumes background music across focus transitions.
 */
class FocusAudioPlayer private constructor(
    private val delegate: AudioPlayer,
    private val focusProvider: (() -> Boolean)?,
    initiallyFocused: Boolean,
) : AudioPlayer by delegate {

    /**
     * Creates a focus-aware audio player that polls [focused] on [syncFocus] and playback calls.
     *
     * @param delegate The underlying platform [AudioPlayer].
     * @param focused Callback returning `true` while the host window or tab holds focus.
     */
    constructor(
        delegate: AudioPlayer,
        focused: () -> Boolean,
    ) : this(delegate, focusProvider = focused, initiallyFocused = focused())

    /**
     * Creates a focus-aware audio player whose [focused] property is updated directly by the host.
     *
     * @param delegate The underlying platform [AudioPlayer].
     * @param initiallyFocused Whether the host window starts focused.
     */
    constructor(
        delegate: AudioPlayer,
        initiallyFocused: Boolean = true,
    ) : this(delegate, focusProvider = null, initiallyFocused = initiallyFocused)

    private sealed interface ActiveMusic {
        data class Clip(val clip: AudioClip, val volume: Float, val loop: Boolean) : ActiveMusic
        data class Stream(val source: MusicStreamSource, val volume: Float, val loop: Boolean) : ActiveMusic
    }

    private var music: ActiveMusic? = null
    private val sounds = ArrayList<SoundHandle>()
    private var disposed = false

    /**
     * Whether the host window currently holds focus. Setting this to `false` stops active sound
     * effects and pauses background music; setting it back to `true` resumes remembered music.
     */
    var focused: Boolean = initiallyFocused
        set(value) {
            if (field == value || disposed) return
            field = value
            if (value) {
                resumeRememberedMusic()
            } else {
                delegate.stopMusic()
                stopAndClearSounds()
            }
        }

    /**
     * Synchronizes [focused] with the focus provider callback if one was supplied at construction.
     */
    fun syncFocus() {
        focusProvider?.let { focused = it() }
    }

    override fun playSound(
        clip: AudioClip,
        volume: Float,
        pan: Float,
        loop: Boolean,
    ): SoundHandle? {
        syncFocus()
        if (!focused || disposed) return null
        pruneStoppedSounds()
        return delegate.playSound(clip, volume, pan, loop)?.also { sounds += it }
    }

    override fun playSound3D(
        clip: AudioClip,
        emitterPos: Vec3f,
        listenerPos: Vec3f,
        listenerRight: Vec3f,
        maxDistance: Float,
        loop: Boolean,
    ): SoundHandle? = super<AudioPlayer>.playSound3D(
        clip = clip,
        emitterPos = emitterPos,
        listenerPos = listenerPos,
        listenerRight = listenerRight,
        maxDistance = maxDistance,
        loop = loop,
    )

    override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {
        if (disposed) return
        syncFocus()
        music = ActiveMusic.Clip(clip, volume, loop)
        if (focused) {
            delegate.playMusic(clip, volume, loop)
        }
    }

    override fun playMusicStream(source: MusicStreamSource, volume: Float, loop: Boolean): Boolean {
        if (disposed || !source.isValidSource()) return false
        syncFocus()
        val remembered = ActiveMusic.Stream(source, volume, loop)
        return if (focused) {
            val started = delegate.playMusicStream(source, volume, loop)
            music = if (started) remembered else null
            started
        } else {
            music = remembered
            true
        }
    }

    override fun stopMusic() {
        music = null
        delegate.stopMusic()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        music = null
        stopAndClearSounds()
        delegate.dispose()
    }

    private fun resumeRememberedMusic() {
        when (val remembered = music) {
            is ActiveMusic.Clip -> delegate.playMusic(remembered.clip, remembered.volume, remembered.loop)
            is ActiveMusic.Stream -> delegate.playMusicStream(remembered.source, remembered.volume, remembered.loop)
            null -> Unit
        }
    }

    private fun pruneStoppedSounds() {
        var index = 0
        while (index < sounds.size) {
            val handle = sounds[index]
            if (!handle.isPlaying) {
                sounds.removeAt(index)
                handle.stop()
            } else {
                index++
            }
        }
    }

    private fun stopAndClearSounds() {
        for (i in sounds.indices) {
            sounds[i].stop()
        }
        sounds.clear()
    }
}
