/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioClipDurationTest {

    @Test
    fun mono16BitDurationMillisComputesExactMilliseconds() {
        val oneSecond = AudioClip(
            id = "mono_16_1s",
            name = "Mono 16-bit 1s",
            pcmBytes = ByteArray(44_100 * 1 * 2),
            sampleRate = 44_100,
            channels = 1,
            bitsPerSample = 16,
        )
        assertEquals(1_000L, oneSecond.durationMillis())

        val quarterSecond = AudioClip(
            id = "mono_16_250ms",
            name = "Mono 16-bit 250ms",
            pcmBytes = ByteArray(11_025 * 1 * 2),
            sampleRate = 44_100,
            channels = 1,
            bitsPerSample = 16,
        )
        assertEquals(250L, quarterSecond.durationMillis())
    }

    @Test
    fun stereo16BitDurationMillisAccountsForChannels() {
        val halfSecond = AudioClip(
            id = "stereo_16_500ms",
            name = "Stereo 16-bit 500ms",
            pcmBytes = ByteArray(22_050 * 2 * 2),
            sampleRate = 44_100,
            channels = 2,
            bitsPerSample = 16,
        )
        assertEquals(500L, halfSecond.durationMillis())

        val oneSecond48k = AudioClip(
            id = "stereo_16_48k_1s",
            name = "Stereo 16-bit 48kHz 1s",
            pcmBytes = ByteArray(48_000 * 2 * 2),
            sampleRate = 48_000,
            channels = 2,
            bitsPerSample = 16,
        )
        assertEquals(1_000L, oneSecond48k.durationMillis())
    }

    @Test
    fun monoAndStereo8BitDurationMillisComputeAccurately() {
        val mono8Bit = AudioClip(
            id = "mono_8_1s",
            name = "Mono 8-bit 1s",
            pcmBytes = ByteArray(8_000 * 1 * 1),
            sampleRate = 8_000,
            channels = 1,
            bitsPerSample = 8,
        )
        assertEquals(1_000L, mono8Bit.durationMillis())

        val stereo8Bit = AudioClip(
            id = "stereo_8_250ms",
            name = "Stereo 8-bit 250ms",
            pcmBytes = ByteArray(12_000 * 2 * 1),
            sampleRate = 48_000,
            channels = 2,
            bitsPerSample = 8,
        )
        assertEquals(250L, stereo8Bit.durationMillis())
    }

    @Test
    fun emptyAndInvalidFormatClipsSafelyReturnZeroMillis() {
        val emptyClip = AudioClip(
            id = "empty",
            name = "Empty",
            pcmBytes = byteArrayOf(),
            sampleRate = 44_100,
            channels = 2,
            bitsPerSample = 16,
        )
        assertEquals(0L, emptyClip.durationMillis())

        val zeroSampleRate = emptyClip.copy(pcmBytes = ByteArray(16), sampleRate = 0)
        assertEquals(0L, zeroSampleRate.durationMillis())

        val negativeSampleRate = emptyClip.copy(pcmBytes = ByteArray(16), sampleRate = -44_100)
        assertEquals(0L, negativeSampleRate.durationMillis())

        val zeroChannels = emptyClip.copy(pcmBytes = ByteArray(16), channels = 0)
        assertEquals(0L, zeroChannels.durationMillis())

        val negativeChannels = emptyClip.copy(pcmBytes = ByteArray(16), channels = -2)
        assertEquals(0L, negativeChannels.durationMillis())

        val zeroBitDepth = emptyClip.copy(pcmBytes = ByteArray(16), bitsPerSample = 0)
        assertEquals(0L, zeroBitDepth.durationMillis())

        val subByteBitDepth = emptyClip.copy(pcmBytes = ByteArray(16), bitsPerSample = 4)
        assertEquals(0L, subByteBitDepth.durationMillis())

        val negativeChannelsAndBits = emptyClip.copy(pcmBytes = ByteArray(16), channels = -1, bitsPerSample = -16)
        assertEquals(0L, negativeChannelsAndBits.durationMillis())

        val subFrameBuffer = emptyClip.copy(pcmBytes = ByteArray(3), channels = 2, bitsPerSample = 16)
        assertEquals(0L, subFrameBuffer.durationMillis())
    }

    @Test
    fun partialTrailingBytesDoNotInflateDuration() {
        val clip = AudioClip(
            id = "trailing_bytes",
            name = "Trailing Bytes",
            pcmBytes = ByteArray(44_100 * 2 * 2 + 3),
            sampleRate = 44_100,
            channels = 2,
            bitsPerSample = 16,
        )
        assertEquals(1_000L, clip.durationMillis())
    }

    @Test
    fun longClipDurationUsesLongArithmeticWithoutIntOverflow() {
        // 50s at 48kHz mono 8-bit = 2,400,000 frames; 2,400,000 * 1000 exceeds Int.MAX_VALUE.
        val clip = AudioClip(
            id = "long_mono_8bit",
            name = "Long Mono 8-bit",
            pcmBytes = ByteArray(48_000 * 50),
            sampleRate = 48_000,
            channels = 1,
            bitsPerSample = 8,
        )
        assertEquals(50_000L, clip.durationMillis())
    }
}
