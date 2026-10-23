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

    private class FakeAudioPlayer : NoOpAudioPlayer() {
        val playedSounds = mutableListOf<AudioClip>()
        var activeMusic: AudioClip? = null
        var musicStops = 0
        var disposed = false

        override fun playSound(clip: AudioClip, volume: Float, pan: Float, loop: Boolean): SoundHandle {
            playedSounds += clip
            return object : SoundHandle {
                override var isPlaying: Boolean = true
                override fun stop() {
                    isPlaying = false
                }
            }
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
}
