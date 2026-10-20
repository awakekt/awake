/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.FilePath
import com.awakekt.awake.core.io.InMemoryFileSystem
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileSystemContentCacheTest {
    private val model = ByteArray(64) { it.toByte() }
    private val modelHash = Sha256.digestHex(model)
    private val texture = ByteArray(32) { (it + 5).toByte() }
    private val textureHash = Sha256.digestHex(texture)

    @Test
    fun getReturnsNullWhenNotCached() = runTest {
        val fs = InMemoryFileSystem()
        val cache = FileSystemContentCache(fs)

        assertNull(cache.get(modelHash))
    }

    @Test
    fun putStoresAndGetRetrievesExactBytes() = runTest {
        val fs = InMemoryFileSystem()
        val cache = FileSystemContentCache(fs)

        cache.put(modelHash, model)
        val retrieved = cache.get(modelHash)

        assertContentEquals(model, retrieved)
    }

    @Test
    fun filesAreShardedByPrefix() = runTest {
        val fs = InMemoryFileSystem()
        val cache = FileSystemContentCache(fs)

        cache.put(modelHash, model)
        val expectedPath = FilePath.of("${modelHash.substring(0, 2)}/$modelHash")

        assertContentEquals(model, fs.read(expectedPath).getOrNull())
    }

    @Test
    fun namespacePlacesFilesUnderSubdirectory() = runTest {
        val fs = InMemoryFileSystem()
        val cache = FileSystemContentCache(fs, namespace = "game-project")

        cache.put(modelHash, model)
        val expectedPath = FilePath.of("game-project/${modelHash.substring(0, 2)}/$modelHash")

        assertContentEquals(model, fs.read(expectedPath).getOrNull())
        assertContentEquals(model, cache.get(modelHash))
    }

    @Test
    fun retainOnlyDeletesUnlistedFiles() = runTest {
        val fs = InMemoryFileSystem()
        val cache = FileSystemContentCache(fs)

        cache.put(modelHash, model)
        cache.put(textureHash, texture)

        val index = ProjectIndex(
            files = listOf(
                ProjectIndexEntry(
                    path = "assets/model.glb",
                    sizeBytes = model.size.toLong(),
                    sha256 = modelHash,
                    url = "model.glb",
                ),
            ),
        )

        val deleted = cache.retainOnly(index)

        assertEquals(1, deleted)
        assertContentEquals(model, cache.get(modelHash))
        assertNull(cache.get(textureHash))
    }

    @Test
    fun integratesWithIndexedAssetSource() = runTest {
        val fs = InMemoryFileSystem()
        val cache = FileSystemContentCache(fs)
        var networkFetches = 0
        val fetch: suspend (String) -> ByteArray = {
            networkFetches++
            model
        }

        val index = ProjectIndex(
            files = listOf(
                ProjectIndexEntry(
                    path = "assets/crate.glb",
                    sizeBytes = model.size.toLong(),
                    sha256 = modelHash,
                    url = "crate.glb",
                ),
            ),
        )

        val source = IndexedAssetSource(index, fetch, cache)

        val firstRead = source.read(AssetPath("assets/crate.glb")).getOrThrow()
        val secondRead = source.read(AssetPath("assets/crate.glb")).getOrThrow()

        assertContentEquals(model, firstRead)
        assertContentEquals(model, secondRead)
        assertEquals(1, networkFetches, "The second read must come from the FileSystemContentCache")
    }
}
