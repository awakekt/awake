/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A published project's files arrive checked against the index, once each, from the cache after that. */
class IndexedAssetSourceTest {
    private val scene = "{\"version\": 1}".encodeToByteArray()
    private val model = ByteArray(64) { it.toByte() }
    private val index = ProjectIndex(files = listOf(entry("scenes/main.scene.json", scene), entry("assets/crate.glb", model)))

    @Test
    fun aFileThatMatchesTheIndexReads() = runTest {
        val source = IndexedAssetSource(index, server())

        assertContentEquals(model, source.read(AssetPath("assets/crate.glb")).getOrThrow())
    }

    @Test
    fun aTamperedByteFailsTheReadAndNamesThePath() = runTest {
        val tampered = server(override = mapOf("files/assets/crate.glb" to model.copyOf().also { it[10] = 99 }))

        val failure = IndexedAssetSource(index, tampered).read(AssetPath("assets/crate.glb")).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("assets/crate.glb"), "the failure names the file: $failure")
        // The positive control: the same bytes through a source that doesn't check reach the caller.
        val unchecked = AssetSource { Result.success(tampered("files/assets/crate.glb")) }
        assertEquals(99, unchecked.read(AssetPath("assets/crate.glb")).getOrThrow()[10].toInt())
    }

    @Test
    fun aSecondReadComesFromTheCacheWithNoFetch() = runTest {
        var fetches = 0
        val serve = server()
        val source = IndexedAssetSource(index, { url -> fetches++; serve(url) }, MemoryCache())

        source.read(AssetPath("assets/crate.glb")).getOrThrow()
        source.read(AssetPath("assets/crate.glb")).getOrThrow()

        assertEquals(1, fetches)
    }

    @Test
    fun aCachedCopyOfTheWrongSizeIsFetchedAgain() = runTest {
        var fetches = 0
        val serve = server()
        val cache = MemoryCache().apply { stored[Sha256.digestHex(model)] = model.copyOf(32) }

        val bytes = IndexedAssetSource(index, { url -> fetches++; serve(url) }, cache).read(AssetPath("assets/crate.glb")).getOrThrow()

        assertContentEquals(model, bytes)
        assertEquals(1, fetches, "the truncated cached copy is replaced from the network")
    }

    @Test
    fun aCacheHitIsNotHashedAgain() = runTest {
        // Same size, different bytes: only a hash would notice, and keeping what was put intact is
        // ContentCache's job, so the hit is served as stored rather than hashed on every visit.
        val stored = ByteArray(64)
        val cache = MemoryCache().apply { this.stored[Sha256.digestHex(model)] = stored }

        val bytes = IndexedAssetSource(index, { error("a cache hit must not fetch") }, cache).read(AssetPath("assets/crate.glb")).getOrThrow()

        assertContentEquals(stored, bytes)
    }

    @Test
    fun prefetchReportsProgressUpToTheTotal() = runTest {
        val cache = MemoryCache()
        val progress = mutableListOf<Pair<Long, Long>>()

        IndexedAssetSource(index, server(), cache).prefetch(listOf("scenes/main.scene.json", "assets/crate.glb")) { done, total ->
            progress += done to total
        }

        val total = (scene.size + model.size).toLong()
        assertEquals(listOf(scene.size.toLong() to total, total to total), progress)
        assertEquals(2, cache.stored.size, "prefetched files are cached")
    }

    @Test
    fun prefetchFetchesMultipleFilesConcurrently() = runTest {
        val paths = listOf("scenes/main.scene.json", "assets/crate.glb")
        val started = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val serve = server()
        val source = IndexedAssetSource(index, { url -> started.send(Unit); release.await(); serve(url) })

        val job = launch { source.prefetch(paths) }
        // Both fetches start before either's fetch() call is allowed to return: they overlap,
        // rather than the second waiting for the first to finish.
        started.receive()
        started.receive()
        release.complete(Unit)
        job.join()
    }

    @Test
    fun prefetchFetchesNoMoreThanTheLimitAtOnce() = runTest {
        val contents = (0 until MAX_CONCURRENT_PREFETCHES + 3).associate { n -> "assets/file$n.bin" to ByteArray(8) { (n * 8 + it).toByte() } }
        var inFlight = 0
        var peak = 0
        val source = IndexedAssetSource(ProjectIndex(files = contents.map { (path, bytes) -> entry(path, bytes) }), { url ->
            inFlight++
            peak = maxOf(peak, inFlight)
            delay(10)
            inFlight--
            contents.getValue(url.removePrefix("files/"))
        })

        source.prefetch(contents.keys.toList())

        assertEquals(MAX_CONCURRENT_PREFETCHES, peak)
    }

    @Test
    fun prefetchDownloadsContentTwoPathsShareOnce() = runTest {
        val shared = ProjectIndex(files = listOf(entry("assets/a.glb", model), entry("assets/b.glb", model)))
        var fetches = 0
        val progress = mutableListOf<Long>()

        IndexedAssetSource(shared, { fetches++; model }).prefetch(listOf("assets/a.glb", "assets/b.glb")) { done, _ -> progress += done }

        assertEquals(1, fetches)
        assertEquals(listOf(2L * model.size), progress, "both paths' bytes count once the shared download is done")
    }

    @Test
    fun prefetchReportsEachFileAsItFinishes() = runTest {
        val release = CompletableDeferred<Unit>()
        val serve = server()
        val progress = mutableListOf<Long>()
        val source = IndexedAssetSource(index, { url ->
            if (url == "files/scenes/main.scene.json") release.await()
            serve(url)
        })

        val job = launch { source.prefetch(listOf("scenes/main.scene.json", "assets/crate.glb")) { done, _ -> progress += done } }
        advanceUntilIdle()

        assertEquals(listOf(model.size.toLong()), progress, "the model is reported while the scene ahead of it is still downloading")
        release.complete(Unit)
        job.join()
        assertEquals(listOf(model.size.toLong(), (model.size + scene.size).toLong()), progress)
    }

    @Test
    fun prefetchFailsForATamperedFileAndNamesIt() = runTest {
        val tampered = server(override = mapOf("files/assets/crate.glb" to model.copyOf().also { it[10] = 99 }))

        val failure = assertFailsWith<IllegalStateException> {
            IndexedAssetSource(index, tampered).prefetch(listOf("scenes/main.scene.json", "assets/crate.glb"))
        }

        assertTrue(failure.message.orEmpty().contains("assets/crate.glb"), "the failure names the file: $failure")
    }

    @Test
    fun aPathOutsideTheIndexFails() = runTest {
        val failure = IndexedAssetSource(index, server()).read(AssetPath("assets/missing.glb")).exceptionOrNull()

        assertTrue(failure is NoSuchElementException, "got $failure")
    }

    @Test
    fun anInvalidIndexIsRefused() {
        val twice = ProjectIndex(files = listOf(entry("assets/crate.glb", model), entry("assets/crate.glb", model)))

        assertFailsWith<IllegalArgumentException> { IndexedAssetSource(twice, server()) }
    }

    private fun entry(path: String, bytes: ByteArray) =
        ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")

    private fun server(override: Map<String, ByteArray> = emptyMap()): suspend (String) -> ByteArray {
        val files = mapOf("files/scenes/main.scene.json" to scene, "files/assets/crate.glb" to model) + override
        return { url -> files.getValue(url) }
    }

    private class MemoryCache : ContentCache {
        val stored = mutableMapOf<String, ByteArray>()
        override suspend fun get(sha256: String): ByteArray? = stored[sha256]
        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }
}
