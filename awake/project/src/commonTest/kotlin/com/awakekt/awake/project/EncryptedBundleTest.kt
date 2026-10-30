/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Encrypted bundles read back with the key, by range or whole, and nothing readable is stored. */
class EncryptedBundleTest {
    private val crate = "assets/models/crate.glb"
    private val scene = "scenes/main.scene.json"

    @Test
    fun everyFileReadsBackWithTheKey() = runTest {
        val project = EncryptedProject.write()
        val source = IndexedAssetSource(project.index, project.server(), keys = project.keys())

        project.files.forEach { (path, bytes) -> assertContentEquals(bytes, source.read(AssetPath(path)).getOrThrow(), path) }
        assertEquals(ProjectIndex.ENCRYPTED_FORMAT_VERSION, project.index.formatVersion)
        assertEquals(ProjectBundleEncryption(keyId = EncryptedProject.KEY_ID), project.index.encryption)
    }

    @Test
    fun aRangeReadFetchesAndAuthenticatesOneSealedMember() = runTest {
        val project = EncryptedProject.write()
        val ranges = mutableListOf<Long>()
        val range: suspend (String, Long, Long) -> ByteArray = { url, offset, length ->
            ranges += length
            project.bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
        }
        val source = IndexedAssetSource(project.index, { error("no whole-bundle fetch: $it") }, rangeFetch = range, keys = project.keys())

        assertContentEquals(project.files.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        assertEquals(listOf(project.files.getValue(crate).size + 16L), ranges, "one request, for the sealed member only")
    }

    @Test
    fun aTamperedByteInARangeReadFailsAuthentication() = runTest {
        val project = EncryptedProject.write()
        val range: suspend (String, Long, Long) -> ByteArray = { url, offset, length ->
            project.bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt()).also { it[3] = (it[3] + 1).toByte() }
        }
        val source = IndexedAssetSource(project.index, { error("unused") }, rangeFetch = range, keys = project.keys())

        val failure = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("failed authentication"), "got $failure")
    }

    @Test
    fun aTamperedByteInAWholeBundleFailsNamingTheMember() = runTest {
        val project = EncryptedProject.write()
        val url = project.index.bundles.single { bundle -> project.index.files.any { it.path == crate && it.bundle == bundle.sha256 } }.url
        val entry = project.index.files.single { it.path == crate }
        val tampered = project.bundles.getValue(url).bytes.copyOf().also { it[entry.offset!!.toInt()] = (it[entry.offset!!.toInt()] + 1).toByte() }
        val source = IndexedAssetSource(project.index, project.server(mapOf(url to tampered)), keys = project.keys())

        val failure = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains(crate), "got $failure")
    }

    @Test
    fun theCacheHoldsOnlySealedBytesAndOpensThemOnRead() = runTest {
        val project = EncryptedProject.write()
        val cache = MemoryContentCache()
        IndexedAssetSource(project.index, project.server(), cache, keys = project.keys()).prefetch(project.files.keys.toList())

        assertTrue(cache.stored.isNotEmpty())
        cache.stored.values.forEach { stored ->
            project.files.values.forEach { plain -> assertFalse(stored.containsRun(plain.copyOf(minOf(plain.size, 24))), "plaintext at rest") }
        }
        assertTrue(project.files.values.none { plain -> cache.stored.containsKey(Sha256.digestHex(plain)) }, "not keyed by plaintext SHA-256")

        val offline = IndexedAssetSource(project.index, { error("offline: $it") }, cache, keys = project.keys())
        assertContentEquals(project.files.getValue(scene), offline.read(AssetPath(scene)).getOrThrow())
    }

    @Test
    fun aCachedCopyThatDoesNotOpenIsFetchedAgain() = runTest {
        val project = EncryptedProject.write()
        val cache = MemoryContentCache()
        val entry = project.index.files.single { it.path == crate }
        cache.stored[project.index.cacheKeyOf(entry)] = ByteArray(entry.sizeBytes.toInt() + 16)

        val bytes = IndexedAssetSource(project.index, project.server(), cache, keys = project.keys()).read(AssetPath(crate)).getOrThrow()

        assertContentEquals(project.files.getValue(crate), bytes)
    }

    @Test
    fun theKeyIsAskedOnceAndAFailureIsAskedAgain() = runTest {
        val project = EncryptedProject.write()
        var asks = 0
        val keys = BundleKeyProvider { keyId ->
            asks++
            check(asks > 1) { "offline" }
            assertEquals(EncryptedProject.KEY_ID, keyId)
            project.key
        }
        val source = IndexedAssetSource(project.index, project.server(), keys = keys)

        assertTrue(source.read(AssetPath(crate)).exceptionOrNull()?.message == "offline")
        project.files.keys.forEach { source.read(AssetPath(it)).getOrThrow() }
        assertEquals(2, asks)
    }

    @Test
    fun anEncryptedIndexWithoutAKeyProviderIsRefused() = runTest {
        val project = EncryptedProject.write()

        val failure = assertFailsWith<IllegalArgumentException> { IndexedAssetSource(project.index, project.server()) }

        assertTrue(failure.message.orEmpty().contains("BundleKeyProvider"), "$failure")
    }

    @Test
    fun bundlesAreByteIdenticalForTheSameKeyAndHideThePlaintext() = runTest {
        val first = EncryptedProject.write()
        val second = EncryptedProject.write()
        val otherKey = EncryptedProject.write(key = ByteArray(32) { (it * 5 + 1).toByte() })

        assertEquals(first.index, second.index)
        first.bundles.forEach { (url, bundle) -> assertContentEquals(bundle.bytes, second.bundles.getValue(url).bytes, url) }
        assertNotEquals(first.index.bundles, otherKey.index.bundles, "another key seals other bytes")
        first.bundles.values.forEach { bundle ->
            first.files.values.forEach { plain -> assertFalse(bundle.bytes.containsRun(plain.copyOf(minOf(plain.size, 24)))) }
        }
    }

    @Test
    fun theIndexNamesTheKeyButNeverCarriesIt() = runTest {
        val project = EncryptedProject.write()
        val json = kotlinx.serialization.json.Json.encodeToString(ProjectIndex.serializer(), project.index)

        assertTrue(json.contains("\"keyId\":\"${EncryptedProject.KEY_ID}\"") && json.contains(ProjectBundleEncryption.SCHEME), json)
        assertFalse(json.contains(project.key.hex()))
        assertFalse(BundleEncryptionKey(EncryptedProject.KEY_ID, project.key).toString().contains(project.key.hex()))
    }
}
