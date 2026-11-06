/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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

    @Test
    fun legacyVersion4IndexWithoutBundleKeyIdDecodesAndHashesIdentically() = runTest {
        val project = EncryptedProject.write()
        val encoded = Json.encodeToString(ProjectIndex.serializer(), project.index)
        // Only the top-level encryption object carries "keyId" when every bundle uses the default key.
        assertEquals(2, encoded.split("\"keyId\"").size, encoded)

        val decoded = Json.decodeFromString(ProjectIndex.serializer(), encoded)

        assertEquals(project.index, decoded)
        decoded.bundles.forEach { bundle ->
            assertNull(bundle.keyId)
            assertEquals(EncryptedProject.KEY_ID, decoded.keyIdOf(bundle))
        }
        decoded.files.forEach { entry ->
            assertEquals(EncryptedProject.KEY_ID, decoded.keyIdOf(entry))
            assertEquals(
                Sha256.digestHex("awake-awkb-v2-cache\n${EncryptedProject.KEY_ID}\n${entry.sha256}"),
                decoded.cacheKeyOf(entry),
            )
        }
    }

    @Test
    fun bundleKeyIdOverridesDefaultKeyIdForValidationAndCacheKeys() = runTest {
        val index = EncryptedProject.write().index
        val firstBundle = index.bundles.first()
        val overridden = index.copy(
            bundles = listOf(firstBundle.copy(keyId = "north-island-2026")) + index.bundles.drop(1),
        )

        assertEquals(emptyList(), ProjectContentValidator.indexIssues(overridden))
        assertEquals("north-island-2026", overridden.keyIdOf(overridden.bundles.first()))
        assertEquals(EncryptedProject.KEY_ID, overridden.keyIdOf(overridden.bundles.last()))
        val inOverridden = overridden.files.first { it.bundle == firstBundle.sha256 }
        val inDefault = overridden.files.first { it.bundle != firstBundle.sha256 }
        assertEquals(
            Sha256.digestHex("awake-awkb-v2-cache\nnorth-island-2026\n${inOverridden.sha256}"),
            overridden.cacheKeyOf(inOverridden),
        )
        assertNotEquals(index.cacheKeyOf(inOverridden), overridden.cacheKeyOf(inOverridden))
        assertEquals(index.cacheKeyOf(inDefault), overridden.cacheKeyOf(inDefault))

        val unencrypted = BundledProject().index
        val onVersion3 = ProjectContentValidator.indexIssues(
            unencrypted.copy(bundles = listOf(unencrypted.bundles.first().copy(keyId = "north-island-2026")) + unencrypted.bundles.drop(1)),
        )
        val onVersion2 = ProjectContentValidator.indexIssues(
            ProjectIndex(bundles = listOf(firstBundle.copy(keyId = "north-island-2026"))),
        )
        val blankOnVersion4 = ProjectContentValidator.indexIssues(
            index.copy(bundles = listOf(firstBundle.copy(keyId = " ")) + index.bundles.drop(1)),
        )

        assertTrue(onVersion3.any { it.contains("bundles[0].keyId needs formatVersion 4") }, "$onVersion3")
        assertTrue(onVersion2.any { it.contains("bundles[0].keyId needs formatVersion 4") }, "$onVersion2")
        assertTrue(blankOnVersion4.any { it.contains("bundles[0].keyId must not be blank") }, "$blankOnVersion4")
        assertFalse(unencrypted.keyIdOf(unencrypted.bundles.first()) != null)
    }
}
