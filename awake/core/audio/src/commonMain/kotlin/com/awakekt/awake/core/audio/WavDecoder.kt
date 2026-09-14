/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import com.awakekt.awake.core.io.BinaryReader

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
            val chunkSize = runCatching { BinaryReader(bytes).readIntLeAt(offset + 4) }.getOrNull() ?: return null
            offset += 8

            if (chunkId == "fmt ") {
                if (chunkSize >= 16 && offset + 16 <= bytes.size) {
                    val reader = BinaryReader(bytes, offset)
                    val audioFormat = reader.readShortLe()
                    channels = reader.readShortLe()
                    sampleRate = reader.readIntLe()
                    reader.skip(8)
                    bitsPerSample = reader.readShortLe()
                    if (audioFormat != 1 || channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) return null
                }
            } else if (chunkId == "data") {
                if (chunkSize < 0 || chunkSize > bytes.size - offset) return null
                pcmData = BinaryReader(bytes, offset).readBytes(chunkSize)
            }

            if (chunkSize < 0 || chunkSize > bytes.size - offset) return null
            offset += chunkSize
            if (chunkSize % 2 == 1) {
                if (offset >= bytes.size) break
                offset += 1
            }
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

}
