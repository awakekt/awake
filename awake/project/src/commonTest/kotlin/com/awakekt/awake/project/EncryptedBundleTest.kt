/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.InMemoryFileSystem
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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

    @Test
    fun perBundleKeyIdsAskOnlyForTouchedBundlesAndRefuseUnauthorizedOnesWithoutBreakingCommon() = runTest {
        val commonKey = ByteArray(32) { (it * 3 + 1).toByte() }
        val expansionKey = ByteArray(32) { (it * 7 + 9).toByte() }
        val expansionPaths = setOf("scenes/pier.scene.json", "assets/models/buoy.glb")
        val bundles = LinkedHashMap<String, WrittenContentBundle>()
        val writer = ContentBundleWriter(
            grouping = { path -> if (path in expansionPaths) "expansion-a" else "common" },
            encryption = BundleEncryptionKey("common-2026", commonKey),
            groupEncryption = { group -> if (group == "expansion-a") BundleEncryptionKey("expansion-a", expansionKey) else null },
        )
        val index = writer.write(looseIndexOf(BundledProject.SAMPLE_FILES), { BundledProject.SAMPLE_FILES.getValue(it.path) }) {
            bundles[it.url] = it
        }

        assertEquals(ProjectBundleEncryption(keyId = "common-2026"), index.encryption)
        assertEquals(listOf(null, "expansion-a"), index.bundles.map { it.keyId })

        val entitled = mutableSetOf("common-2026")
        val asked = mutableListOf<String>()
        val keys = BundleKeyProvider { keyId ->
            asked += keyId
            check(keyId in entitled) { "not entitled to $keyId" }
            if (keyId == "common-2026") commonKey else expansionKey
        }
        val source = IndexedAssetSource(index, { url -> bundles.getValue(url).bytes }, keys = keys)

        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(scene), source.read(AssetPath(scene)).getOrThrow())
        assertContentEquals(BundledProject.SAMPLE_FILES.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        assertEquals(listOf("common-2026"), asked, "untouched expansion bundle never asks for its key")

        val denied = source.read(AssetPath("scenes/pier.scene.json")).exceptionOrNull()
        assertEquals("not entitled to expansion-a", denied?.message)
        assertEquals(listOf("common-2026", "expansion-a"), asked)

        // Common content keeps working without asking again.
        assertContentEquals(
            BundledProject.SAMPLE_FILES.getValue("assets/sounds/bell.ogg"),
            source.read(AssetPath("assets/sounds/bell.ogg")).getOrThrow(),
        )
        assertEquals(listOf("common-2026", "expansion-a"), asked)

        // Granting the expansion key lets the next read open it cleanly.
        entitled += "expansion-a"
        expansionPaths.forEach { path ->
            assertContentEquals(BundledProject.SAMPLE_FILES.getValue(path), source.read(AssetPath(path)).getOrThrow(), path)
        }
        assertEquals(listOf("common-2026", "expansion-a", "expansion-a"), asked)
    }

    @Test
    fun perBundleKeyIdsWorkOnRangeReads() = runTest {
        val (files, bundles, index) = multiKeySharedFixture()
        val sharedBytes = files.getValue("assets/textures/shared-common.bin")

        val rangeAsked = mutableListOf<String>()
        val rangeSource = IndexedAssetSource(
            index = index,
            fetch = { error("whole fetch unused: $it") },
            rangeFetch = { url, offset, length -> bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt()) },
            keys = { keyId ->
                rangeAsked += keyId
                if (keyId == "common-2026") COMMON_KEY else EXPANSION_KEY
            },
        )

        assertContentEquals(sharedBytes, rangeSource.read(AssetPath("assets/textures/shared-common.bin")).getOrThrow())
        assertEquals(listOf("common-2026"), rangeAsked)
        assertContentEquals(sharedBytes, rangeSource.read(AssetPath("assets/textures/shared-expansion.bin")).getOrThrow())
        assertEquals(listOf("common-2026", "expansion-a"), rangeAsked)
    }

    @Test
    fun perBundleKeyIdsWorkWithCachePrefetchAndRetainOnly() = runTest {
        val (files, bundles, index) = multiKeySharedFixture()
        val sharedBytes = files.getValue("assets/textures/shared-common.bin")
        val commonShared = index.files.single { it.path == "assets/textures/shared-common.bin" }
        val expansionShared = index.files.single { it.path == "assets/textures/shared-expansion.bin" }
        assertEquals(commonShared.sha256, expansionShared.sha256)
        assertNotEquals(index.cacheKeyOf(commonShared), index.cacheKeyOf(expansionShared))

        val cache = FileSystemContentCache(InMemoryFileSystem())
        val staleHash = "f".repeat(64)
        cache.put(staleHash, byteArrayOf(1, 2, 3))
        val prefetchAsked = mutableListOf<String>()
        val cachedSource = IndexedAssetSource(
            index = index,
            fetch = { url -> bundles.getValue(url).bytes },
            cache = cache,
            keys = { keyId ->
                prefetchAsked += keyId
                if (keyId == "common-2026") COMMON_KEY else EXPANSION_KEY
            },
        )

        cachedSource.prefetch(listOf("scenes/main.scene.json", "assets/textures/shared-common.bin"))
        assertEquals(listOf("common-2026"), prefetchAsked)

        cachedSource.prefetch(files.keys.toList())
        assertEquals(listOf("common-2026", "expansion-a"), prefetchAsked)
        assertEquals(1, cache.retainOnly(index))
        assertNull(cache.get(staleHash))
        assertFalse(cache.get(index.cacheKeyOf(commonShared))!!.contentEquals(cache.get(index.cacheKeyOf(expansionShared))!!))

        val offlineAsked = mutableListOf<String>()
        val offline = IndexedAssetSource(
            index = index,
            fetch = { error("offline: $it") },
            cache = cache,
            keys = { keyId ->
                offlineAsked += keyId
                check(keyId == "common-2026") { "expansion locked" }
                COMMON_KEY
            },
        )
        assertContentEquals(sharedBytes, offline.read(AssetPath("assets/textures/shared-common.bin")).getOrThrow())
        assertEquals("expansion locked", offline.read(AssetPath("assets/textures/shared-expansion.bin")).exceptionOrNull()?.message)
        assertEquals(listOf("common-2026", "expansion-a"), offlineAsked)
    }

    private suspend fun multiKeySharedFixture(): Triple<Map<String, ByteArray>, Map<String, WrittenContentBundle>, ProjectIndex> {
        val sharedBytes = ByteArray(96) { (it + 17).toByte() }
        val files = linkedMapOf(
            "awake.project.json" to "{\"formatVersion\":1,\"id\":\"com.example.harbor-town\"}".encodeToByteArray(),
            "scenes/main.scene.json" to "{\"version\":1,\"name\":\"harbor\"}".encodeToByteArray(),
            "assets/textures/shared-common.bin" to sharedBytes.copyOf(),
            "scenes/expansion.scene.json" to "{\"version\":1,\"name\":\"north-island\"}".encodeToByteArray(),
            "assets/textures/shared-expansion.bin" to sharedBytes.copyOf(),
        )
        val expansionPaths = setOf("scenes/expansion.scene.json", "assets/textures/shared-expansion.bin")
        val bundles = LinkedHashMap<String, WrittenContentBundle>()
        val writer = ContentBundleWriter(
            grouping = { path -> if (path in expansionPaths) "expansion-a" else "common" },
            encryption = BundleEncryptionKey("common-2026", COMMON_KEY),
            groupEncryption = { group -> if (group == "expansion-a") BundleEncryptionKey("expansion-a", EXPANSION_KEY) else null },
        )
        val index = writer.write(looseIndexOf(files), { files.getValue(it.path) }) { bundles[it.url] = it }
        return Triple(files, bundles, index)
    }

    @Test
    fun writerRejectsPartialGroupEncryptionAndConflictingKeysForOneId() = runTest {
        val keyA = ByteArray(32) { (it + 1).toByte() }
        val keyB = ByteArray(32) { (it + 2).toByte() }
        val loose = looseIndexOf(BundledProject.SAMPLE_FILES)

        val partial = assertFailsWith<IllegalArgumentException> {
            ContentBundleWriter(
                groupEncryption = { group -> if (group == "scenes") BundleEncryptionKey("expansion-a", keyA) else null },
            ).write(loose, { BundledProject.SAMPLE_FILES.getValue(it.path) }) { }
        }
        assertTrue(partial.message.orEmpty().contains("Every bundle group in an encrypted index needs a BundleEncryptionKey"), "$partial")

        val conflicting = assertFailsWith<IllegalArgumentException> {
            ContentBundleWriter(
                encryption = BundleEncryptionKey("same-id", keyA),
                groupEncryption = { group -> if (group == "scenes") BundleEncryptionKey("same-id", keyB) else null },
            ).write(loose, { BundledProject.SAMPLE_FILES.getValue(it.path) }) { }
        }
        assertTrue(conflicting.message.orEmpty().contains("same-id"), "$conflicting")

        val allViaGroupSelector = ContentBundleWriter(
            groupEncryption = { group ->
                if (group == "scenes") BundleEncryptionKey("expansion-a", keyB) else BundleEncryptionKey("common-2026", keyA)
            },
        ).write(loose, { BundledProject.SAMPLE_FILES.getValue(it.path) }) { }
        assertEquals("common-2026", allViaGroupSelector.encryption?.keyId)
        assertEquals(listOf(null, null, "expansion-a"), allViaGroupSelector.bundles.map { it.keyId })
    }

    private companion object {
        val COMMON_KEY = ByteArray(32) { (it * 5 + 2).toByte() }
        val EXPANSION_KEY = ByteArray(32) { (it * 11 + 4).toByte() }
    }
}
