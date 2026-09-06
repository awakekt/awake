/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

/**
 * In-memory audio clip containing linear uncompressed PCM audio bytes.
 *
 * 100% Kotlin Multiplatform (pure bytes and sample metadata).
 */
data class AudioClip(
    val id: String,
    val name: String,
    val pcmBytes: ByteArray,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitsPerSample: Int = 16,
) {
    val durationSeconds: Float
        get() {
            val bytesPerSample = (bitsPerSample / 8) * channels
            if (bytesPerSample <= 0 || sampleRate <= 0) return 0f
            val totalSamples = pcmBytes.size / bytesPerSample
            return totalSamples.toFloat() / sampleRate
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioClip) return false
        return id == other.id &&
            name == other.name &&
            pcmBytes.contentEquals(other.pcmBytes) &&
            sampleRate == other.sampleRate &&
            channels == other.channels &&
            bitsPerSample == other.bitsPerSample
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + pcmBytes.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitsPerSample
        return result
    }
}
