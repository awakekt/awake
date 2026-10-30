/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Encrypted bundles are format 2 and their indexes format 4, which older engines refuse. */
class EncryptedBundleFormatTest {
    private val members = listOf(ContentBundleMember("a.bin", ByteArray(40) { 1 }), ContentBundleMember("b.bin", ByteArray(40) { 1 }))
    private val key = BundleEncryptionKey(EncryptedProject.KEY_ID, EncryptedProject.KEY)

    @Test
    fun anEncryptedBundleIsVersion2WithTheEncryptedFlagAndSealedSizes() = runTest {
        val bytes = ContentBundleFormat.encodeEncrypted(members, key)

        val header = ContentBundleFormat.readHeader(bytes)
        val toc = ContentBundleFormat.readTableOfContents(bytes)
        assertEquals(ContentBundleFormat.ENCRYPTED_VERSION, header.formatVersion)
        assertTrue(header.membersEncrypted)
        assertEquals(listOf(0, 2, 0, 1), bytes.copyOfRange(4, 8).map { it.toInt() }, "version 2, flags 1, big-endian")
        assertEquals(listOf(56L, 56L), toc.map { it.sizeBytes }, "40 bytes plus the 16-byte tag")
        assertEquals(1, toc.map { it.offset }.distinct().size, "equal members are sealed and stored once")
        assertEquals(header.dataOffset + 56, bytes.size)
    }

    @Test
    fun aPlaintextBundleIsStillVersion1() {
        val header = ContentBundleFormat.readHeader(ContentBundleFormat.encode(members))

        assertEquals(ContentBundleFormat.VERSION, header.formatVersion)
        assertEquals(false, header.membersEncrypted)
    }

    @Test
    fun aVersion1ReaderRefusesAnEncryptedBundleWithAVersionError() = runTest {
        val bytes = ContentBundleFormat.encodeEncrypted(members, key)
        // The check every engine that reads only version 1 makes, as released.
        val version = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        val failure = runCatching {
            require(version == 1) { "Content bundle format $version isn't one this engine reads (it reads 1)" }
        }.exceptionOrNull()

        assertEquals("Content bundle format 2 isn't one this engine reads (it reads 1)", failure?.message)
    }

    @Test
    fun aLaterVersionOrAnUnknownFlagIsRefusedWithAClearError() = runTest {
        val bytes = ContentBundleFormat.encodeEncrypted(members, key)
        val later = bytes.copyOf().also { it[5] = 3 }
        val flagged = bytes.copyOf().also { it[7] = 3 }

        val version = assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readHeader(later) }
        val flags = assertFailsWith<IllegalArgumentException> { ContentBundleFormat.readHeader(flagged) }

        assertTrue(version.message.orEmpty().contains("format 3 isn't one this engine reads"), "$version")
        assertTrue(flags.message.orEmpty().contains("flags 3"), "$flags")
    }

    @Test
    fun anEngineThatReadsIndexesUpToVersion3RefusesAnEncryptedIndex() = runTest {
        val index = EncryptedProject.write().index
        // The version rule of an engine that reads indexes 2 and 3 only, as released.
        val readable = index.formatVersion == ProjectIndex.LOOSE_FORMAT_VERSION || index.formatVersion == ProjectIndex.BUNDLED_FORMAT_VERSION

        assertEquals(false, readable)
        val later = ProjectContentValidator.indexIssueDetails(index.copy(formatVersion = 5)).map { it.message }
        assertTrue(later.any { it.contains("formatVersion must be 2, 3 or 4") }, "$later")
    }

    @Test
    fun encryptionAndItsVersionGoTogether() = runTest {
        val index = EncryptedProject.write().index

        val withoutEncryption = ProjectContentValidator.indexIssueDetails(index.copy(encryption = null)).map { it.message }
        val onVersion3 = ProjectContentValidator.indexIssueDetails(index.copy(formatVersion = ProjectIndex.BUNDLED_FORMAT_VERSION)).map { it.message }
        val blankKey = ProjectContentValidator.indexIssueDetails(index.copy(encryption = ProjectBundleEncryption(keyId = " "))).map { it.message }

        assertTrue(withoutEncryption.any { it.contains("needs encryption") }, "$withoutEncryption")
        assertTrue(onVersion3.any { it.contains("encryption needs formatVersion 4") }, "$onVersion3")
        assertTrue(blankKey.any { it.contains("keyId must not be blank") }, "$blankKey")
    }

    @Test
    fun aBundleKeyIs32BytesAndNeverPrinted() {
        assertFailsWith<IllegalArgumentException> { BundleEncryptionKey("k", ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { BundleEncryptionKey(" ", ByteArray(32)) }
        assertEquals("BundleEncryptionKey(keyId=k, bytes=<redacted>)", BundleEncryptionKey("k", ByteArray(32)).toString())
    }
}
