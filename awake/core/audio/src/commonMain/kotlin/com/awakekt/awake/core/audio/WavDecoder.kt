/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

/**
 * Pure Kotlin Multiplatform decoder for uncompressed standard 16-bit PCM RIFF/WAVE audio bytes.
 */
object WavDecoder {

    /**
     * Decodes standard PCM WAV [bytes] into an [AudioClip].
     * Returns null if [bytes] is not a valid PCM WAVE buffer.
     */
    fun decode(id: String, name: String, bytes: ByteArray): AudioClip? {
        if (bytes.size < 44) return null

        // Check RIFF header
        if (bytes[0].toInt() != 'R'.code ||
            bytes[1].toInt() != 'I'.code ||
            bytes[2].toInt() != 'F'.code ||
            bytes[3].toInt() != 'F'.code
        ) {
            return null
        }

        // Check WAVE signature
        if (bytes[8].toInt() != 'W'.code ||
            bytes[9].toInt() != 'A'.code ||
            bytes[10].toInt() != 'V'.code ||
            bytes[11].toInt() != 'E'.code
        ) {
            return null
        }

        var offset = 12
        var channels = 2
        var sampleRate = 44100
        var bitsPerSample = 16
        var pcmData: ByteArray? = null

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.decodeToString(offset, offset + 4)
            val chunkSize = readIntLe(bytes, offset + 4)
            offset += 8

            if (chunkId == "fmt ") {
                if (chunkSize >= 16 && offset + 16 <= bytes.size) {
                    val audioFormat = readShortLe(bytes, offset)
                    channels = readShortLe(bytes, offset + 2)
                    sampleRate = readIntLe(bytes, offset + 4)
                    bitsPerSample = readShortLe(bytes, offset + 14)
                }
            } else if (chunkId == "data") {
                val available = (bytes.size - offset).coerceAtLeast(0)
                val len = chunkSize.coerceIn(0, available)
                pcmData = bytes.copyOfRange(offset, offset + len)
            }

            offset += chunkSize
        }

        val data = pcmData ?: return null
        return AudioClip(
            id = id,
            name = name,
            pcmBytes = data,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
        )
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        return (b1 shl 8) or b0
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
}
