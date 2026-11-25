/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileVulkanPipelineCacheStoreTest {
    @Test
    fun roundTripCreatesParentDirectoriesAndLeavesNoTempFiles() {
        withTempDir { root ->
            val cacheFile = File(root, "nested/cache/awake-vk-pipeline-cache.bin")
            val store = FileVulkanPipelineCacheStore(cacheFile)
            val payload = ByteArray(256) { ((it * 19) + 7).toByte() }

            assertNull(store.load())
            store.save(payload)

            assertTrue(cacheFile.isFile, "Cache file should be created along with missing parent directories")
            val siblings = cacheFile.parentFile.listFiles().orEmpty().map(File::getName)
            assertEquals(listOf(cacheFile.name), siblings, "No temporary sibling files may remain after atomic move")

            val loaded = FileVulkanPipelineCacheStore(cacheFile).load()
            assertNotNull(loaded)
            assertContentEquals(payload, loaded)
        }
    }

    @Test
    fun atomicOverwriteReplacesPreviousPayloadCleanly() {
        withTempDir { root ->
            val cacheFile = File(root, "cache.bin")
            val store = FileVulkanPipelineCacheStore(cacheFile)
            val payloadA = ByteArray(64) { 0x11 }
            val payloadB = ByteArray(192) { 0x22 }

            store.save(payloadA)
            assertContentEquals(payloadA, store.load())

            store.save(payloadB)
            assertContentEquals(payloadB, FileVulkanPipelineCacheStore(cacheFile).load())
            val siblings = root.listFiles().orEmpty().map(File::getName)
            assertEquals(listOf(cacheFile.name), siblings)
        }
    }

    @Test
    fun unchangedPayloadSkipsDiskRewrite() {
        withTempDir { root ->
            val cacheFile = File(root, "cache.bin")
            val store = FileVulkanPipelineCacheStore(cacheFile)
            val payload = ByteArray(128) { (it * 5).toByte() }

            store.save(payload)
            val pinnedTimestamp = 1_600_000_000_000L
            assertTrue(cacheFile.setLastModified(pinnedTimestamp))

            // Saving identical bytes on the same store skips rewriting.
            store.save(payload.copyOf())
            assertEquals(pinnedTimestamp, cacheFile.lastModified(), "Unchanged payload must skip redundant disk write")

            // A fresh store instance that loaded the same payload also skips rewriting identical bytes.
            val reloadedStore = FileVulkanPipelineCacheStore(cacheFile)
            assertContentEquals(payload, reloadedStore.load())
            reloadedStore.save(payload.copyOf())
            assertEquals(pinnedTimestamp, cacheFile.lastModified(), "Loaded payload saved unchanged must skip disk write")

            // Saving modified bytes rewrites the file.
            val modified = payload.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
            reloadedStore.save(modified)
            assertContentEquals(modified, FileVulkanPipelineCacheStore(cacheFile).load())
        }
    }

    @Test
    fun emptyPayloadIsSkippedWithoutClobberingValidCache() {
        withTempDir { root ->
            val cacheFile = File(root, "cache.bin")
            val store = FileVulkanPipelineCacheStore(cacheFile)
            store.save(ByteArray(0))
            assertFalse(cacheFile.exists())

            val validPayload = ByteArray(40) { 0x33 }
            store.save(validPayload)
            store.save(ByteArray(0))
            assertContentEquals(validPayload, FileVulkanPipelineCacheStore(cacheFile).load())
        }
    }

    @Test
    fun corruptTruncatedChecksumMismatchAndOversizedFilesReturnNullAndRecoverOnSave() {
        withTempDir { root ->
            val cacheFile = File(root, "cache.bin")
            val store = FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024)

            // Empty file -> null.
            cacheFile.writeBytes(ByteArray(0))
            assertNull(store.load())

            // Random garbage -> null.
            cacheFile.writeBytes(ByteArray(64) { 0x7E })
            assertNull(store.load())

            // Valid save, then flip a payload bit -> checksum mismatch returns null.
            val validPayload = ByteArray(96) { (it + 1).toByte() }
            store.save(validPayload)
            val rawBytes = cacheFile.readBytes()
            rawBytes[rawBytes.lastIndex] = (rawBytes[rawBytes.lastIndex].toInt() xor 0x01).toByte()
            cacheFile.writeBytes(rawBytes)
            assertNull(FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024).load())

            // Truncated envelope -> null.
            cacheFile.writeBytes(rawBytes.copyOf(VulkanPipelineCacheEnvelope.HEADER_SIZE + 5))
            assertNull(FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024).load())

            // Oversized file exceeding maxBytes -> null; oversized save is also ignored.
            cacheFile.writeBytes(ByteArray(2048) { 0x01 })
            assertNull(FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024).load())
            FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024).save(ByteArray(2048) { 0x02 })
            assertNull(FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024).load())

            // Subsequent valid save recovers cleanly by overwriting the corrupt file.
            val freshStore = FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024)
            freshStore.save(validPayload)
            assertContentEquals(validPayload, FileVulkanPipelineCacheStore(cacheFile, maxBytes = 1024).load())
        }
    }

    @Test
    fun unwriteableAndDirectoryTargetsAreHandledGracefully() {
        withTempDir { root ->
            // Case 1: parent path is an existing regular file, so directory creation and temp file fail.
            val blockingFile = File(root, "not-a-directory").apply { writeText("blocker") }
            val blockedStore = FileVulkanPipelineCacheStore(File(blockingFile, "cache.bin"))
            assertNull(blockedStore.load())
            blockedStore.save(ByteArray(64) { 0x44 })
            assertNull(blockedStore.load())

            // Case 2: target path itself is a directory.
            val dirTarget = File(root, "existing-dir").apply { mkdirs() }
            val dirStore = FileVulkanPipelineCacheStore(dirTarget)
            assertNull(dirStore.load())
            dirStore.save(ByteArray(64) { 0x55 })
            assertTrue(dirTarget.isDirectory)
            assertNull(dirStore.load())
        }
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("awake-vk-cache-test-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
