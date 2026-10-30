/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Bundle members arrive checked, one request per bundle, through either fetch. */
class BundledAssetSourceTest {
    private val project = BundledProject()
    private val crate = "assets/models/crate.glb"

    @Test
    fun aDamagedMemberFailsTheReadAndNamesItsPath() = runTest {
        val source = IndexedAssetSource(project.index, project.server(override = damaged(crate)))

        val failure = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(failure.message.orEmpty().contains(crate), "the failure names the damaged member: $failure")
    }

    @Test
    fun aDamagedBundleFailsEveryReadFromItAndCachesNothing() = runTest {
        val cache = MemoryContentCache()
        val source = IndexedAssetSource(project.index, project.server(override = damaged(crate)), cache)

        val neighbour = source.read(AssetPath("assets/models/buoy.glb")).exceptionOrNull()

        assertTrue(neighbour?.message.orEmpty().contains(crate), "a read of another member names the damaged one: $neighbour")
        assertTrue(cache.stored.isEmpty(), "nothing from a damaged bundle is kept")
    }

    @Test
    fun prefetchOfADamagedBundleFailsNamingTheMember() = runTest {
        val source = IndexedAssetSource(project.index, project.server(override = damaged(crate)), MemoryContentCache())

        val failure = assertFailsWith<IllegalStateException> { source.prefetch(project.files.keys.toList()) }

        assertTrue(failure.message.orEmpty().contains(crate), "$failure")
    }

    @Test
    fun aDamagedHeaderFailsWithTheBundleNamed() = runTest {
        val url = bundleUrlOf(crate)
        val bytes = project.bundles.getValue(url).bytes.copyOf().also { it[0] = 'X'.code.toByte() }

        val failure = IndexedAssetSource(project.index, project.server(override = mapOf(url to bytes))).read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains(url), "$failure")
        assertTrue(failure?.message.orEmpty().contains("header or table of contents"), "$failure")
    }

    @Test
    fun aValidBundleWhoseIndexDisagreesWithItsTableOfContentsFailsNamingTheMember() = runTest {
        val tampered = project.index.copy(
            files = project.index.files.map { entry ->
                if (entry.path == crate) entry.copy(sha256 = "0".repeat(64)) else entry
            },
        )
        val source = IndexedAssetSource(tampered, project.server())

        val failure = source.read(AssetPath("assets/models/buoy.glb")).exceptionOrNull()

        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(failure.message.orEmpty().contains(crate), "names the mismatched member: $failure")
    }

    @Test
    fun nonBundleBytesWhoseShaMatchesTheIndexStillFailOnTableOfContents() = runTest {
        val bogus = ByteArray(64) { 7 }
        val bogusSha = com.awakekt.awake.core.io.Sha256.digestHex(bogus)
        val bogusUrl = "bundles/$bogusSha.awkb"
        val forged = ProjectIndex(
            formatVersion = ProjectIndex.BUNDLED_FORMAT_VERSION,
            files = listOf(
                ProjectIndexEntry(
                    path = crate,
                    sizeBytes = 16,
                    sha256 = com.awakekt.awake.core.io.Sha256.digestHex(ByteArray(16) { 7 }),
                    bundle = bogusSha,
                    offset = 16,
                ),
            ),
            bundles = listOf(ProjectIndexBundle(sha256 = bogusSha, sizeBytes = bogus.size.toLong(), url = bogusUrl)),
        )
        val source = IndexedAssetSource(forged, { bogus })

        val failure = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure is IllegalStateException, "got $failure")
        assertTrue(failure.message.orEmpty().contains(bogusUrl), "$failure")
    }

    @Test
    fun aFullLoadMakesOneRequestPerBundleNotPerFile() = runTest {
        val looseRequests = mutableListOf<String>()
        val bundledRequests = mutableListOf<String>()
        val serve = project.server()
        val loose = IndexedAssetSource(project.looseIndex, { url -> looseRequests += url; serve(url) }, MemoryContentCache())
        val bundled = IndexedAssetSource(project.index, { url -> bundledRequests += url; serve(url) }, MemoryContentCache())

        project.files.keys.forEach { path ->
            loose.read(AssetPath(path)).getOrThrow()
            assertContentEquals(project.files.getValue(path), bundled.read(AssetPath(path)).getOrThrow())
        }

        // Two of the files have the same bytes, so the loose source downloads that content once.
        assertEquals(project.files.values.map { it.toList() }.distinct().size, looseRequests.size)
        assertEquals(project.bundles.keys.sorted(), bundledRequests.sorted(), "each bundle once: $bundledRequests")
    }

    @Test
    fun withNoCacheEachMemberStillReadsWithoutFetchingItsBundleAgain() = runTest {
        val requests = mutableListOf<String>()
        val serve = project.server()
        val source = IndexedAssetSource(project.index, { url -> requests += url; serve(url) })

        project.files.keys.forEach { path -> assertContentEquals(project.files.getValue(path), source.read(AssetPath(path)).getOrThrow(), path) }

        assertEquals(project.bundles.size, requests.size, "the unread members wait in memory: $requests")
    }

    @Test
    fun prefetchDownloadsWholeBundlesOnceAndThenReadsComeFromTheCache() = runTest {
        val requests = mutableListOf<String>()
        val serve = project.server()
        val cache = MemoryContentCache()
        val source = IndexedAssetSource(project.index, { url -> requests += url; serve(url) }, cache)
        val progress = mutableListOf<Long>()

        source.prefetch(project.files.keys.toList()) { done, _ -> progress += done }
        project.files.keys.forEach { source.read(AssetPath(it)).getOrThrow() }

        assertEquals(project.bundles.keys.sorted(), requests.sorted())
        assertEquals(project.files.values.sumOf { it.size }.toLong(), progress.last())
        assertEquals(project.bundles.size, progress.size, "progress once per bundle")
        assertEquals(project.files.values.map { com.awakekt.awake.core.io.Sha256.digestHex(it) }.toSet(), cache.stored.keys)
    }

    @Test
    fun prefetchSkipsABundleWhoseMembersAreAllCached() = runTest {
        val cache = MemoryContentCache()
        IndexedAssetSource(project.index, project.server(), cache).prefetch(project.files.keys.toList())

        IndexedAssetSource(project.index, { url -> error("nothing should be fetched: $url") }, cache).prefetch(project.files.keys.toList())
    }

    @Test
    fun readsOfOneBundleRunningAtOnceShareOneFetch() = runTest {
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val serve = project.server()
        val source = IndexedAssetSource(project.index, { url -> requests++; release.await(); serve(url) }, MemoryContentCache())
        val assets = project.files.keys.filter { it.startsWith("assets/") }

        val reads = assets.map { path -> async { source.read(AssetPath(path)).getOrThrow() } }
        yield()
        release.complete(Unit)

        assertEquals(assets.map { project.files.getValue(it).toList() }, reads.awaitAll().map { it.toList() })
        assertEquals(1, requests, "the assets bundle is fetched once for ${assets.size} reads")
    }

    @Test
    fun aRangeReadFetchesOnlyItsMember() = runTest {
        val ranges = mutableListOf<Triple<String, Long, Long>>()
        val serve = project.server()
        val source = IndexedAssetSource(
            project.index,
            { url -> error("a range read must not fetch the whole bundle: $url") },
            rangeFetch = { url, offset, length ->
                ranges += Triple(url, offset, length)
                serve(url).copyOfRange(offset.toInt(), (offset + length).toInt())
            },
        )

        assertContentEquals(project.files.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        val entry = project.index.files.single { it.path == crate }
        assertEquals(listOf(Triple(bundleUrlOf(crate), entry.offset!!, entry.sizeBytes)), ranges)
    }

    @Test
    fun aDamagedRangeFailsNamingThePath() = runTest {
        val source = IndexedAssetSource(project.index, { error("no whole fetch") }, rangeFetch = { _, _, length -> ByteArray(length.toInt()) })

        val failure = source.read(AssetPath(crate)).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains(crate), "$failure")
    }

    @Test
    fun prefetchStillDownloadsWholeBundlesWhenRangeReadsAreOn() = runTest {
        val requests = mutableListOf<String>()
        val serve = project.server()
        val source = IndexedAssetSource(
            project.index,
            { url -> requests += url; serve(url) },
            MemoryContentCache(),
            rangeFetch = { url, _, _ -> error("prefetch must not range-read $url") },
        )

        source.prefetch(project.files.keys.toList())

        assertEquals(project.bundles.size, requests.size)
    }

    @Test
    fun bundlesLoadThroughATokenAuthorizedFetchWithTheTokenOnEachRequest() = runTest {
        val serve = project.server()
        val requests = mutableListOf<ContentRequest>()
        val transport = ContentTransport { request ->
            requests += request
            ContentResponse(200, serve(request.url))
        }
        val fetch = TokenAuthorizedFetch(transport) { ReadToken("t0ken", kotlin.time.Duration.INFINITE) }

        val source = IndexedAssetSource(project.index, fetch::fetch, MemoryContentCache())
        project.files.keys.forEach { path -> assertContentEquals(project.files.getValue(path), source.read(AssetPath(path)).getOrThrow()) }

        assertEquals(project.bundles.keys.sorted(), requests.map { it.url }.sorted())
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer t0ken" })
    }

    @Test
    fun aTokenRangeReadSendsARangeHeaderAndTheToken() = runTest {
        val serve = project.server()
        val requests = mutableListOf<ContentRequest>()
        val transport = ContentTransport { request ->
            requests += request
            val (start, end) = request.headers.getValue("Range").removePrefix("bytes=").split('-').map(String::toInt)
            ContentResponse(206, serve(request.url).copyOfRange(start, end + 1))
        }
        val fetch = TokenAuthorizedFetch(transport) { ReadToken("t0ken", kotlin.time.Duration.INFINITE) }
        val source = IndexedAssetSource(project.index, fetch::fetch, rangeFetch = fetch::fetchRange)

        assertContentEquals(project.files.getValue(crate), source.read(AssetPath(crate)).getOrThrow())

        val entry = project.index.files.single { it.path == crate }
        val request = requests.single()
        assertEquals("bytes=${entry.offset}-${entry.offset!! + entry.sizeBytes - 1}", request.headers["Range"])
        assertEquals("Bearer t0ken", request.headers["Authorization"])
    }

    @Test
    fun aServerThatIgnoresTheRangeStillServesTheMember() = runTest {
        val serve = project.server()
        val fetch = TokenAuthorizedFetch({ request -> ContentResponse(200, serve(request.url.substringBefore('?'))) }, ReadTokenPlacement.QueryParameter()) {
            ReadToken("t0ken", kotlin.time.Duration.INFINITE)
        }

        val bytes = fetch.fetchRange(bundleUrlOf(crate), project.index.files.single { it.path == crate }.offset!!, 300)

        assertContentEquals(project.files.getValue(crate), bytes)
    }

    @Test
    fun aRangeAnswerOfTheWrongSizeFails() = runTest {
        val fetch = TokenAuthorizedFetch({ ContentResponse(206, ByteArray(3)) }) { ReadToken("t0ken", kotlin.time.Duration.INFINITE) }

        val failure = assertFailsWith<ContentFetchException> { fetch.fetchRange("bundles/b.awkb", 10, 5) }

        assertEquals(206, failure.status)
    }

    @Test
    fun theOpenFactoryReadsABundledIndexAndRangeReadsWhenAsked() = runTest {
        val serve = project.server()
        val indexJson = kotlinx.serialization.json.Json.encodeToString(ProjectIndex.serializer(), project.index).encodeToByteArray()
        val ranges = mutableListOf<String>()

        val source = IndexedAssetSource.open(
            fetch = { path -> if (path.value == IndexedAssetSource.DEFAULT_INDEX_PATH) indexJson else serve(path.value) },
            rangeFetch = { path, offset, length -> ranges += path.value; serve(path.value).copyOfRange(offset.toInt(), (offset + length).toInt()) },
        )

        assertContentEquals(project.files.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        assertEquals(listOf(bundleUrlOf(crate)), ranges)
    }

    @Test
    fun aBundledIndexThatPointsOutsideItsBundleIsRefused() {
        val entry = project.index.files.first()
        val bundle = project.index.bundles.single { it.sha256 == entry.bundle }
        val outside = project.index.copy(files = project.index.files.map { if (it == entry) it.copy(offset = bundle.sizeBytes) else it })
        val unknown = project.index.copy(files = project.index.files.map { if (it == entry) it.copy(bundle = "f".repeat(64)) else it })
        val oldVersion = project.index.copy(formatVersion = ProjectIndex.LOOSE_FORMAT_VERSION)

        assertTrue(ProjectContentValidator.indexIssues(outside).any { "inside its bundle" in it })
        assertTrue(ProjectContentValidator.indexIssues(unknown).any { "names no listed bundle" in it })
        assertTrue(ProjectContentValidator.indexIssues(oldVersion).any { "formatVersion 3" in it })
        assertFailsWith<IllegalArgumentException> { IndexedAssetSource(outside, project.server()) }
    }

    @Test
    fun anIndexFromANewerEngineIsRefused() {
        val issues = ProjectContentValidator.indexIssues(project.index.copy(formatVersion = 4))

        assertTrue(issues.any { "formatVersion must be 2 or 3" in it }, "$issues")
    }

    private fun bundleUrlOf(path: String): String {
        val sha = project.index.files.single { it.path == path }.bundle
        return project.index.bundles.single { it.sha256 == sha }.url
    }

    /** The bundle holding [path], with one byte of [path]'s member changed. */
    private fun damaged(path: String): Map<String, ByteArray> {
        val entry = project.index.files.single { it.path == path }
        val url = bundleUrlOf(path)
        val bytes = project.bundles.getValue(url).bytes.copyOf()
        val at = (entry.offset!! + 10).toInt()
        bytes[at] = (bytes[at] + 1).toByte()
        return mapOf(url to bytes)
    }
}
