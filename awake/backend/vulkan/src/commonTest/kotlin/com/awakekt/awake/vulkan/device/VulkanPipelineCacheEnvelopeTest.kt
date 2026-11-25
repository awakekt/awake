/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.VK_UUID_SIZE
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceProperties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VulkanPipelineCacheEnvelopeTest {
    @Test
    fun crc32MatchesStandardIeeeTestVector() {
        val vector = "123456789".encodeToByteArray()
        assertEquals(0xCBF43926.toInt(), VulkanPipelineCacheEnvelope.crc32(vector))
    }

    @Test
    fun encodeAndDecodeRoundTripPreservesPayload() {
        val payload = ByteArray(128) { index -> ((index * 37) xor 0x5A).toByte() }
        val encoded = VulkanPipelineCacheEnvelope.encode(payload)
        assertEquals(VulkanPipelineCacheEnvelope.HEADER_SIZE + payload.size, encoded.size)
        assertEquals("AWKVKPC1", encoded.copyOfRange(0, 8).decodeToString())

        val decoded = VulkanPipelineCacheEnvelope.decode(encoded)
        assertNotNull(decoded)
        assertContentEquals(payload, decoded)
    }

    @Test
    fun decodeRejectsEmptyShortMagicVersionLengthAndChecksumCorruption() {
        val payload = ByteArray(48) { it.toByte() }
        val valid = VulkanPipelineCacheEnvelope.encode(payload)

        assertNull(VulkanPipelineCacheEnvelope.decode(ByteArray(0)))
        assertNull(VulkanPipelineCacheEnvelope.decode(ByteArray(VulkanPipelineCacheEnvelope.HEADER_SIZE)))

        // Corrupt magic byte.
        assertNull(
            VulkanPipelineCacheEnvelope.decode(
                valid.copyOf().also { it[0] = 'X'.code.toByte() },
            ),
        )

        // Unsupported format version.
        assertNull(
            VulkanPipelineCacheEnvelope.decode(
                valid.copyOf().also { it[11] = 2 },
            ),
        )

        // Truncated payload.
        assertNull(VulkanPipelineCacheEnvelope.decode(valid.copyOf(valid.size - 1)))

        // Trailing garbage byte.
        assertNull(VulkanPipelineCacheEnvelope.decode(valid + byteArrayOf(0x7F)))

        // Flipped payload bit (CRC-32 mismatch).
        assertNull(
            VulkanPipelineCacheEnvelope.decode(
                valid.copyOf().also { it[VulkanPipelineCacheEnvelope.HEADER_SIZE] = (it[VulkanPipelineCacheEnvelope.HEADER_SIZE].toInt() xor 1).toByte() },
            ),
        )

        // Payload exceeds configured size cap.
        assertNull(VulkanPipelineCacheEnvelope.decode(valid, maxPayloadBytes = payload.size - 1))
    }

    @Test
    fun fingerprintMatchesIdenticalPayloadsAndDetectsSingleBitChanges() {
        val a = ByteArray(64) { (it * 13).toByte() }
        val b = a.copyOf()
        val mutated = a.copyOf().also { it[31] = (it[31].toInt() xor 0x01).toByte() }

        assertEquals(VulkanPipelineCacheEnvelope.fingerprint(a), VulkanPipelineCacheEnvelope.fingerprint(b))
        assertNotEquals(VulkanPipelineCacheEnvelope.fingerprint(a), VulkanPipelineCacheEnvelope.fingerprint(mutated))
    }

    @Test
    fun compatiblePipelineCacheBlobValidatesVulkanHeaderFields() {
        val uuid = ByteArray(VK_UUID_SIZE) { (0xA0 + it).toByte() }
        val props = VkPhysicalDeviceProperties(
            vendorID = 0x13B5,
            deviceID = 0x90930010.toInt(),
            pipelineCacheUUID = uuid,
        )
        val validBlob = buildVulkanPipelineCacheHeader(
            headerSize = 32,
            headerVersion = 1,
            vendorId = props.vendorID,
            deviceId = props.deviceID,
            uuid = uuid,
            extraBytes = 16,
        )

        assertTrue(isCompatiblePipelineCacheBlob(validBlob, props))
        assertFalse(isCompatiblePipelineCacheBlob(ByteArray(31), props))
        assertFalse(
            isCompatiblePipelineCacheBlob(
                buildVulkanPipelineCacheHeader(16, 1, props.vendorID, props.deviceID, uuid, 16),
                props,
            ),
        )
        assertFalse(
            isCompatiblePipelineCacheBlob(
                buildVulkanPipelineCacheHeader(1024, 1, props.vendorID, props.deviceID, uuid, 16),
                props,
            ),
        )
        assertFalse(
            isCompatiblePipelineCacheBlob(
                buildVulkanPipelineCacheHeader(32, 2, props.vendorID, props.deviceID, uuid, 16),
                props,
            ),
        )
        assertFalse(
            isCompatiblePipelineCacheBlob(
                buildVulkanPipelineCacheHeader(32, 1, props.vendorID xor 1, props.deviceID, uuid, 16),
                props,
            ),
        )
        assertFalse(
            isCompatiblePipelineCacheBlob(
                buildVulkanPipelineCacheHeader(32, 1, props.vendorID, props.deviceID xor 1, uuid, 16),
                props,
            ),
        )
        val differentUuid = uuid.copyOf().also { it[VK_UUID_SIZE - 1] = (it[VK_UUID_SIZE - 1].toInt() xor 1).toByte() }
        assertFalse(
            isCompatiblePipelineCacheBlob(
                buildVulkanPipelineCacheHeader(32, 1, props.vendorID, props.deviceID, differentUuid, 16),
                props,
            ),
        )
    }

    @Test
    fun noneStoreIsNoOp() {
        VulkanPipelineCacheStore.None.save(byteArrayOf(1, 2, 3, 4))
        assertNull(VulkanPipelineCacheStore.None.load())
    }

    private fun buildVulkanPipelineCacheHeader(
        headerSize: Int,
        headerVersion: Int,
        vendorId: Int,
        deviceId: Int,
        uuid: ByteArray,
        extraBytes: Int,
    ): ByteArray {
        val bytes = ByteArray(32 + extraBytes)
        writeIntLe(bytes, 0, headerSize)
        writeIntLe(bytes, 4, headerVersion)
        writeIntLe(bytes, 8, vendorId)
        writeIntLe(bytes, 12, deviceId)
        uuid.copyInto(bytes, destinationOffset = 16)
        return bytes
    }

    private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }
}
