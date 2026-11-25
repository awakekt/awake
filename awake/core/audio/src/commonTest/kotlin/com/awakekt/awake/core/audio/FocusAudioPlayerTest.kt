/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusAudioPlayerTest {
    private val sfx = AudioClip(
        id = "sfx/swing.wav",
        name = "Swing",
        pcmBytes = ByteArray(16),
        sampleRate = 44_100,
        channels = 1,
    )
    private val bgm = AudioClip(
        id = "music/town.ogg",
        name = "Town",
        pcmBytes = ByteArray(32),
        sampleRate = 44_100,
        channels = 2,
    )

    private class TrackingSoundHandle(
        var playing: Boolean = true,
    ) : SoundHandle {
        var stopCalls: Int = 0

        override val isPlaying: Boolean
            get() = playing

        override fun stop() {
            stopCalls++
            playing = false
        }
    }

    private class FakeAudioPlayer : NoOpAudioPlayer() {
        val playedSounds = mutableListOf<AudioClip>()
        val emittedHandles = mutableListOf<TrackingSoundHandle>()
        var activeMusic: AudioClip? = null
        var musicStops = 0
        var disposed = false

        override fun playSound(clip: AudioClip, volume: Float, pan: Float, loop: Boolean): SoundHandle {
            playedSounds += clip
            return TrackingSoundHandle().also { emittedHandles += it }
        }

        override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) {
            activeMusic = clip
        }

        override fun stopMusic() {
            activeMusic = null
            musicStops++
        }

        override fun dispose() {
            disposed = true
        }
    }

    @Test
    fun mutesSfxWhenUnfocusedAndPausesAndResumesMusicAcrossFocusTransitions() {
        val backend = FakeAudioPlayer()
        var windowFocused = false
        val player = FocusAudioPlayer(backend) { windowFocused }

        // While unfocused, SFX is dropped and music is remembered but not started.
        assertNull(player.playSound(sfx))
        assertTrue(backend.playedSounds.isEmpty())

        player.playMusic(bgm)
        assertNull(backend.activeMusic)

        // Gaining focus starts remembered music and allows SFX.
        windowFocused = true
        player.syncFocus()
        assertEquals(bgm, backend.activeMusic)

        val handle = player.playSound(sfx)
        assertNotNull(handle)
        assertTrue(handle.isPlaying)

        // Losing focus stops active SFX and pauses music.
        windowFocused = false
        player.syncFocus()
        assertFalse(handle.isPlaying)
        assertNull(backend.activeMusic)

        // Regaining focus resumes music unless stopMusic was called.
        windowFocused = true
        player.syncFocus()
        assertEquals(bgm, backend.activeMusic)

        player.stopMusic()
        windowFocused = false
        player.syncFocus()
        windowFocused = true
        player.syncFocus()
        assertNull(backend.activeMusic)
    }

    @Test
    fun playSoundCallsStopBeforeDroppingFinishedNonPlayingHandles() {
        val backend = FakeAudioPlayer()
        val player = FocusAudioPlayer(backend, initiallyFocused = true)

        assertNotNull(player.playSound(sfx))
        assertNotNull(player.playSound(sfx))
        val first = backend.emittedHandles[0]
        val second = backend.emittedHandles[1]

        // Simulate a backend handle whose playback ended without stop() being called yet.
        first.playing = false
        assertEquals(0, first.stopCalls)

        // Playing another sound prunes non-playing handles and explicitly calls stop() before drop.
        assertNotNull(player.playSound(sfx))
        assertEquals(1, first.stopCalls)
        assertEquals(0, second.stopCalls)
        assertTrue(second.isPlaying)

        // Subsequent plays must not invoke stop() again on the already-dropped handle.
        assertNotNull(player.playSound(sfx))
        assertEquals(1, first.stopCalls)
        assertEquals(0, second.stopCalls)
    }

    @Test
    fun disposeStopsRemainingTrackedHandlesBeforeClearing() {
        val backend = FakeAudioPlayer()
        val player = FocusAudioPlayer(backend, initiallyFocused = true)

        assertNotNull(player.playSound(sfx))
        assertNotNull(player.playSound(sfx))
        val first = backend.emittedHandles[0]
        val second = backend.emittedHandles[1]
        // Also ensure a handle whose isPlaying flipped false before dispose still gets stop().
        first.playing = false

        player.dispose()
        assertTrue(backend.disposed)
        assertEquals(1, first.stopCalls)
        assertEquals(1, second.stopCalls)
        assertFalse(first.isPlaying)
        assertFalse(second.isPlaying)

        // Repeated dispose is a no-op and subsequent playSound calls are ignored.
        player.dispose()
        assertEquals(1, first.stopCalls)
        assertEquals(1, second.stopCalls)
        assertNull(player.playSound(sfx))
    }
}
