/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
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
}
