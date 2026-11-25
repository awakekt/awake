/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.vulkan.VK_UUID_SIZE
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceProperties

/** Compact non-retaining digest used to skip redundant disk writes without pinning cache blobs in RAM. */
internal data class PipelineCacheFingerprint(
    val size: Int,
    val crc32: Int,
    val fnv1a64: Long,
)

/**
 * Versioned integrity envelope for persisted `VkPipelineCache` blobs.
 *
 * Layout (big-endian header, 20 bytes total):
 * - bytes `0..7`: ASCII magic `"AWKVKPC1"`
 * - bytes `8..11`: format version (`1`)
 * - bytes `12..15`: raw payload length in bytes (`> 0`)
 * - bytes `16..19`: IEEE 802.3 CRC-32 of the raw payload
 * - bytes `20..(20 + length - 1)`: raw `VkPipelineCache` payload
 */
internal object VulkanPipelineCacheEnvelope {
    private val MAGIC = byteArrayOf(
        'A'.code.toByte(),
        'W'.code.toByte(),
        'K'.code.toByte(),
        'V'.code.toByte(),
        'K'.code.toByte(),
        'P'.code.toByte(),
        'C'.code.toByte(),
        '1'.code.toByte(),
    )

    const val FORMAT_VERSION: Int = 1
    const val HEADER_SIZE: Int = 20

    private const val OFFSET_VERSION = 8
    private const val OFFSET_LENGTH = 12
    private const val OFFSET_CRC32 = 16
    private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325UL
    private const val FNV_PRIME = 1099511628211L

    private val CRC32_TABLE = IntArray(256) { index ->
        var crc = index
        repeat(8) {
            crc = if ((crc and 1) != 0) {
                (crc ushr 1) xor 0xEDB88320.toInt()
            } else {
                crc ushr 1
            }
        }
        crc
    }

    /** Wraps non-empty [payload] in a validated binary envelope. */
    fun encode(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Cannot encode an empty Vulkan pipeline cache payload." }
        val checksum = crc32(payload)
        val out = ByteArray(HEADER_SIZE + payload.size)
        MAGIC.copyInto(out, destinationOffset = 0)
        writeIntBe(out, OFFSET_VERSION, FORMAT_VERSION)
        writeIntBe(out, OFFSET_LENGTH, payload.size)
        writeIntBe(out, OFFSET_CRC32, checksum)
        payload.copyInto(out, destinationOffset = HEADER_SIZE)
        return out
    }

    /**
     * Validates [raw] and extracts its inner `VkPipelineCache` payload, returning `null` if the
     * envelope is empty, truncated, oversized, has a mismatched magic/version, or fails CRC-32.
     */
    fun decode(
        raw: ByteArray,
        maxPayloadBytes: Int = VulkanPipelineCacheStore.DEFAULT_MAX_BYTES,
    ): ByteArray? {
        val valid = hasValidEnvelopeHeader(raw, maxPayloadBytes)
        if (!valid) return null
        val payloadLength = readIntBe(raw, OFFSET_LENGTH)
        val expectedCrc = readIntBe(raw, OFFSET_CRC32)
        val actualCrc = crc32(raw, offset = HEADER_SIZE, length = payloadLength)
        return if (actualCrc == expectedCrc) {
            raw.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLength)
        } else {
            null
        }
    }

    /** Computes a compact fingerprint for [payload] to detect unchanged saves without retaining bytes. */
    fun fingerprint(payload: ByteArray): PipelineCacheFingerprint {
        var crc = -1
        var fnv = FNV_OFFSET_BASIS
        for (i in payload.indices) {
            val b = payload[i].toInt() and 0xFF
            crc = (crc ushr 8) xor CRC32_TABLE[(crc xor b) and 0xFF]
            fnv = (fnv xor b.toLong()) * FNV_PRIME
        }
        return PipelineCacheFingerprint(
            size = payload.size,
            crc32 = crc.inv(),
            fnv1a64 = fnv,
        )
    }

    /** Computes standard IEEE 802.3 CRC-32 over `[offset, offset + length)` of [bytes]. */
    fun crc32(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var crc = -1
        val end = offset + length
        var i = offset
        while (i < end) {
            val b = bytes[i].toInt() and 0xFF
            crc = (crc ushr 8) xor CRC32_TABLE[(crc xor b) and 0xFF]
            i++
        }
        return crc.inv()
    }

    private fun hasValidEnvelopeHeader(raw: ByteArray, maxPayloadBytes: Int): Boolean {
        if (maxPayloadBytes <= 0 || raw.size <= HEADER_SIZE) return false
        val magicMatches = matchesMagic(raw)
        val version = readIntBe(raw, OFFSET_VERSION)
        val payloadLength = readIntBe(raw, OFFSET_LENGTH)
        return magicMatches &&
            version == FORMAT_VERSION &&
            payloadLength in 1..maxPayloadBytes &&
            raw.size - HEADER_SIZE == payloadLength
    }

    private fun matchesMagic(raw: ByteArray): Boolean {
        for (i in MAGIC.indices) {
            if (raw[i] != MAGIC[i]) return false
        }
        return true
    }

    private fun writeIntBe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readIntBe(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}

/**
 * Verifies that [data] begins with a valid Vulkan `VkPipelineCacheHeaderVersionOne` matching the
 * active physical device's [VkPhysicalDeviceProperties] (`vendorID`, `deviceID`, and
 * `pipelineCacheUUID`).
 *
 * Pre-validating before `vkCreatePipelineCache` prevents older mobile Vulkan drivers from crashing
 * on stale or truncated blobs after an OS/driver update.
 */
internal fun isCompatiblePipelineCacheBlob(
    data: ByteArray,
    properties: VkPhysicalDeviceProperties,
    maxBytes: Int = VulkanPipelineCacheStore.DEFAULT_MAX_BYTES,
): Boolean {
    if (data.size < VK_PIPELINE_CACHE_HEADER_MIN_SIZE || data.size > maxBytes) return false
    val headerSize = readIntLe(data, VK_HEADER_OFFSET_SIZE)
    val headerVersion = readIntLe(data, VK_HEADER_OFFSET_VERSION)
    val vendorId = readIntLe(data, VK_HEADER_OFFSET_VENDOR_ID)
    val deviceId = readIntLe(data, VK_HEADER_OFFSET_DEVICE_ID)
    val expectedUuid = properties.pipelineCacheUUID
    val headerFieldsValid = headerSize >= VK_PIPELINE_CACHE_HEADER_MIN_SIZE &&
        headerSize <= data.size &&
        headerVersion == VK_PIPELINE_CACHE_HEADER_VERSION_ONE &&
        vendorId == properties.vendorID &&
        deviceId == properties.deviceID &&
        expectedUuid.size == VK_UUID_SIZE
    return headerFieldsValid && matchesPipelineCacheUuid(data, expectedUuid)
}

/**
 * Loads initial pipeline cache bytes from [store], swallowing any store errors so startup can
 * safely proceed with an empty cache.
 */
// TooGenericExceptionCaught: a custom store failure must never prevent Vulkan startup.
@Suppress("TooGenericExceptionCaught")
internal fun loadInitialPipelineCacheData(store: VulkanPipelineCacheStore?): ByteArray? {
    if (store == null) return null
    return try {
        store.load()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        pipelineCacheLogger.warn { "Failed to load Vulkan pipeline cache; continuing with empty cache: ${e.message}" }
        null
    }
}

/**
 * Exports [graphicsDevice]'s live `VkPipelineCache` and saves it to [store], swallowing export or
 * storage errors so rendering, pause, and teardown never fail due to cache persistence.
 */
// TooGenericExceptionCaught: cache persistence is best-effort and must never fail rendering or teardown.
@Suppress("TooGenericExceptionCaught")
internal fun flushDevicePipelineCache(
    graphicsDevice: GraphicsDevice,
    store: VulkanPipelineCacheStore,
) {
    if (graphicsDevice.device == 0L || graphicsDevice.sharedPipelineCache == 0L) return
    try {
        val data = graphicsDevice.exportPipelineCacheData()
        if (data.isNotEmpty()) {
            store.save(data)
        }
    } catch (e: Exception) {
        pipelineCacheLogger.warn { "Failed to export or save Vulkan pipeline cache: ${e.message}" }
    }
}

private fun matchesPipelineCacheUuid(data: ByteArray, expectedUuid: ByteArray): Boolean {
    for (i in 0 until VK_UUID_SIZE) {
        if (data[VK_HEADER_OFFSET_UUID + i] != expectedUuid[i]) return false
    }
    return true
}

private fun readIntLe(source: ByteArray, offset: Int): Int =
    (source[offset].toInt() and 0xFF) or
        ((source[offset + 1].toInt() and 0xFF) shl 8) or
        ((source[offset + 2].toInt() and 0xFF) shl 16) or
        ((source[offset + 3].toInt() and 0xFF) shl 24)

private const val VK_PIPELINE_CACHE_HEADER_MIN_SIZE = 32
private const val VK_PIPELINE_CACHE_HEADER_VERSION_ONE = 1
private const val VK_HEADER_OFFSET_SIZE = 0
private const val VK_HEADER_OFFSET_VERSION = 4
private const val VK_HEADER_OFFSET_VENDOR_ID = 8
private const val VK_HEADER_OFFSET_DEVICE_ID = 12
private const val VK_HEADER_OFFSET_UUID = 16

internal val pipelineCacheLogger = Logger("VulkanPipelineCache")
