/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.audio

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Measures the actual Web Audio graph with Chrome's OfflineAudioContext, without audio hardware. */
class WebAudioPlayerTest {
    @Test
    fun signedPcmAndStereoChannelsReachTheBrowserOutput() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        player.playSound(pcm(-32768, -16384, 0, 16384), pan = -1f)
        val output = renderAudio(context).await<JsAny>()
        assertEquals(-1.0, sampleAt(output, 0, 0), 0.00001)
        assertEquals(-0.5, sampleAt(output, 0, 1), 0.00001)
        assertEquals(0.5, sampleAt(output, 0, 3), 0.00001)
        assertEquals(0.0, sampleAt(output, 1, 3), 0.00001)
        player.dispose()

        val stereoContext = offlineContext()
        val stereoPlayer = WebAudioPlayer(stereoContext)
        stereoPlayer.playSound(pcm(8192, -16384, 16384, -8192).copy(channels = 2))
        val stereo = renderAudio(stereoContext).await<JsAny>()
        assertEquals(0.25, sampleAt(stereo, 0, 0), 0.00001)
        assertEquals(-0.5, sampleAt(stereo, 1, 0), 0.00001)
        stereoPlayer.dispose()
    }

    @Test
    fun liveMasterAndBusVolumesMultiplyTheSourceGain() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        player.playSound(pcm(16384), volume = 0.5f, pan = -1f, loop = true)
        player.masterVolume = 0.5f
        player.sfxVolume = 0.5f
        val output = renderAudio(context).await<JsAny>()
        assertEquals(0.0625, sampleAt(output, 0, 100), 0.00001)
        player.dispose()
    }

    @Test
    fun aLoopCanStartMutedAndBeUnmutedAndPannedWhileActive() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        player.masterVolume = 0f
        val sound = assertNotNull(player.playSound(pcm(16384), loop = true))
        assertTrue(sound.isPlaying)
        player.masterVolume = 1f
        sound.update(volume = 0.25f, pan = 1f)
        val output = renderAudio(context).await<JsAny>()
        assertEquals(0.0, sampleAt(output, 0, 100), 0.00001)
        assertEquals(0.125, sampleAt(output, 1, 100), 0.00001)
        sound.stop()
        sound.stop()
        assertFalse(sound.isPlaying)
        player.dispose()
    }

    @Test
    fun mutingAnExistingLoopProducesSilence() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        val sound = assertNotNull(player.playSound(pcm(16384), loop = true))
        player.masterVolume = 0f
        val output = renderAudio(context).await<JsAny>()
        assertEquals(0.0, sampleAt(output, 0, 100), 0.00001)
        assertEquals(0.0, sampleAt(output, 1, 100), 0.00001)
        assertTrue(sound.isPlaying)
        player.dispose()
    }

    @Test
    fun aOneShotEndsAndAStoppedLoopDoesNotRender() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        val oneShot = assertNotNull(player.playSound(pcm(16384), pan = -1f))
        val loop = assertNotNull(player.playSound(pcm(-16384), pan = -1f, loop = true))
        loop.stop()
        val output = renderAudio(context).await<JsAny>()
        assertEquals(0.5, sampleAt(output, 0, 0), 0.00001)
        assertEquals(0.0, sampleAt(output, 0, 100), 0.00001)
        assertFalse(oneShot.isPlaying)
        player.dispose()
    }

    @Test
    fun replacingMusicAndChangingItsBusDoesNotAffectSoundEffects() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        player.playSound(pcm(8192), pan = -1f, loop = true)
        player.playMusic(pcm(16384), volume = 1f, loop = true)
        player.playMusic(pcm(-16384), volume = 1f, loop = true)
        player.musicVolume = 0.5f
        val output = renderAudio(context).await<JsAny>()
        val musicSample = -0.25 / kotlin.math.sqrt(2.0)
        assertEquals(0.25 + musicSample, sampleAt(output, 0, 100), 0.00001)
        assertEquals(musicSample, sampleAt(output, 1, 100), 0.00001)
        player.dispose()
    }

    @Test
    fun stopMusicLeavesEffectsPlayingAndDisposeStopsEverything() = runTest {
        val context = offlineContext()
        val player = WebAudioPlayer(context)
        val effect = assertNotNull(player.playSound(pcm(16384), pan = -1f, loop = true))
        player.playMusic(pcm(-16384), volume = 1f, loop = true)
        player.stopMusic()
        val output = renderAudio(context).await<JsAny>()
        assertEquals(0.5, sampleAt(output, 0, 100), 0.00001)
        assertEquals(0.0, sampleAt(output, 1, 100), 0.00001)
        player.dispose()
        player.dispose()
        assertFalse(effect.isPlaying)
        assertNull(player.playSound(pcm(16384)))
    }

    @Test
    fun malformedAndUnsupportedPcmIsRejected() {
        val player = WebAudioPlayer(offlineContext())
        val valid = pcm(16384)
        listOf(
            valid.copy(pcmBytes = byteArrayOf()),
            valid.copy(pcmBytes = byteArrayOf(1)),
            valid.copy(channels = 0),
            valid.copy(channels = 3),
            valid.copy(bitsPerSample = 8),
            valid.copy(sampleRate = 0),
        ).forEach { assertNull(player.playSound(it)) }
        player.dispose()
    }

    @Test
    fun syntheticInputCannotUnlockAudio() {
        val context = trackedSuspendedContext()
        val unlock = installAudioUnlock(context)
        dispatchSyntheticInput()
        assertEquals(0, resumeCount(context))
        removeAudioUnlock(unlock)
    }

    private fun pcm(vararg samples: Int): AudioClip = AudioClip(
        id = "browser-pcm",
        name = "Browser PCM",
        pcmBytes = ByteArray(samples.size * 2) { index -> (samples[index / 2] shr ((index % 2) * 8)).toByte() },
        sampleRate = 48_000,
        channels = 1,
    )
}

@JsFun("() => new OfflineAudioContext(2, 128, 48000)")
private external fun offlineContext(): JsAny

@JsFun("(context) => context.startRendering()")
private external fun renderAudio(context: JsAny): Promise<JsAny>

@JsFun("(buffer, channel, frame) => buffer.getChannelData(channel)[frame]")
private external fun sampleAt(buffer: JsAny, channel: Int, frame: Int): Double

@JsFun("() => ({ state: 'suspended', resumes: 0, resume() { this.resumes++; return Promise.resolve(); } })")
private external fun trackedSuspendedContext(): JsAny

@JsFun("(context) => context.resumes")
private external fun resumeCount(context: JsAny): Int

@JsFun("() => { window.dispatchEvent(new PointerEvent('pointerdown')); window.dispatchEvent(new PointerEvent('pointerup')); window.dispatchEvent(new KeyboardEvent('keydown')); }")
private external fun dispatchSyntheticInput()
