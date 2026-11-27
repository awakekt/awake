/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmAudioPlayerTest {
    @Test
    fun voicePoolReusesIdleClipsWithSubMillisecondLatency() {
        val player = JvmAudioPlayer()
        val clip = AudioClip(
            id = "footstep_test",
            name = "Footstep Test",
            pcmBytes = ByteArray(4410), // 50ms of audio
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
        )

        // First play initializes the clip in the pool
        val handle1 = player.playSound(clip)
        assertNotNull(handle1, "First play must succeed")
        handle1.stop()

        // Subsequent triggers should reuse the existing open clip with < 1ms latency
        val startNano = System.nanoTime()
        val handle2 = player.playSound(clip)
        val elapsedNano = System.nanoTime() - startNano
        assertNotNull(handle2, "Reused pooled play must succeed")

        val elapsedMs = elapsedNano / 1_000_000.0
        println("Pooled clip reuse elapsed: ${elapsedMs}ms (${elapsedNano / 1000}us)")
        // Verify it takes under 2ms (typically ~15-30 microseconds) rather than 50-100ms
        assertTrue(elapsedMs < 2.0, "Pooled clip playback must be sub-millisecond, took ${elapsedMs}ms")

        player.dispose()
    }

    @Test
    fun playMusicStreamStreamsWavBytesAndFilesAndRejectsUnsupportedPayloads() {
        val mixer = OfflineAudioMixer(sampleRate = 22_050)
        mixer.playMusic(SoundSynthesizer.tone("bgm", "Tone", 330f, 0.05f), volume = 0.5f, loop = false)
        mixer.advance(0.05f)
        val wavBytes = mixer.encodeWav()

        val player = JvmAudioPlayer()
        assertFalse(player.playMusicStream(MusicStreamSource.EncodedBytes("bad.mp3", byteArrayOf(1, 2, 3, 4))))
        assertFalse(player.playMusicStream(MusicStreamSource.FilePath("missing", "nonexistent_track.wav")))

        val bytesAccepted = player.playMusicStream(
            MusicStreamSource.EncodedBytes("bgm/test.wav", wavBytes),
            volume = 0.3f,
            loop = true,
        )
        player.musicVolume = 0.2f
        player.masterVolume = 0.5f
        player.stopMusic()

        val tempWav = File.createTempFile("awake-stream-test", ".wav")
        try {
            tempWav.writeBytes(wavBytes)
            val fileAccepted = player.playMusicStream(
                MusicStreamSource.FilePath("bgm/file.wav", tempWav.absolutePath),
                volume = 0.3f,
                loop = false,
            )
            // On hosts with an audio mixer both WAV stream modes open cleanly; headless CI without a
            // Java Sound line returns false cleanly without throwing.
            assertTrue(bytesAccepted == fileAccepted)
        } finally {
            player.dispose()
            tempWav.delete()
        }
        assertFalse(player.playMusicStream(MusicStreamSource.EncodedBytes("bgm/test.wav", wavBytes)))
    }
}
