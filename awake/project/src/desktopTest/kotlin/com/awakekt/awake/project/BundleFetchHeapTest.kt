/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a bundle fetch keeps on the heap while it fills a [ContentCache] (#1619): a phone ran out of
 * heap on entering its world, with an 85 MB bundle body held twice in its dump.
 *
 * The host's fetch hands back a new array, as a network does, and suspends after it, so the bodies of
 * concurrent downloads exist at once. The cache keeps nothing, as a disk cache keeps nothing on the
 * heap, and reads the heap retained over the test's own data at some of its writes.
 *
 * Desktop-only: it reads the JVM's heap.
 */
class BundleFetchHeapTest {
    @Test
    fun anEightyMegabyteBundleIsHeldOnceWhileItsMembersAreCached() = runTest {
        val project = project(folders = 1, membersEach = 64)
        val peak = peakRetained(project)
        val bundle = project.bundleBytes.values.single().size
        println("BundleFetchHeap: one ${mib(bundle)} MiB bundle of 64 members, peak retained ${mib(peak)} MiB")
        assertTrue(peak <= bundle * 5 / 4, "a ${mib(bundle)} MiB bundle kept ${mib(peak)} MiB while its members were cached")
    }

    @Test
    fun aSealedEightyMegabyteBundleIsHeldOnceAndOpenedOnlyAsRead() = runTest {
        val project = project(folders = 1, membersEach = 64, sealed = true)
        val peak = peakRetained(project)
        val bundle = project.bundleBytes.values.single().size
        println("BundleFetchHeap: one sealed ${mib(bundle)} MiB bundle of 64 members, peak retained ${mib(peak)} MiB")
        assertTrue(peak <= bundle * 5 / 4, "a sealed ${mib(bundle)} MiB bundle kept ${mib(peak)} MiB while its members were cached")
    }

    @Test
    fun fourBundlesPrefetchedTogetherAreFetchedTwoAtATime() = runTest {
        val project = project(folders = 4, membersEach = 16)
        val peak = peakRetained(project)
        val bundle = project.bundleBytes.values.first().size
        println(
            "BundleFetchHeap: four ${mib(bundle)} MiB bundles, peak retained ${mib(peak)} MiB, " +
                "${project.maxInFlight} fetched at once",
        )
        assertTrue(project.maxInFlight == BundleMemoryLimits.DEFAULT_CONCURRENT_BUNDLE_FETCHES, "${project.maxInFlight} bundles were fetched at once")
        assertTrue(peak <= bundle * 5 / 2, "four ${mib(bundle)} MiB bundles kept ${mib(peak)} MiB at once")
    }

    /**
     * A published game's shape: many small bundles and a few large (200 of them, 0.1 to 0.5 MiB and five of
     * 12 to 15.5 MiB), every file read at once with no cache, as a world does on entry. Each read fetched
     * its own bundle with nothing to limit them, so every bundle was held at once: 139 MiB here, the
     * jump a phone showed on entering its world. The bundles fetched at once are now limited (#1619).
     */
    @Test
    fun manySmallBundlesReadAtOnceAreFetchedAFewAtATime() = runTest {
        val project = project(sizes = MANY_BUNDLES)
        val baseline = retained()
        var peak = 0L
        val source = IndexedAssetSource(project.index, { url -> project.fetch(url).also { peak = maxOf(peak, retained() - baseline) } })
        coroutineScope { project.index.files.map { file -> async { source.read(AssetPath(file.path)).getOrThrow().size } }.awaitAll() }
        source.close()
        val largest = project.bundleBytes.values.maxOf { it.size }
        println("BundleFetchHeap: ${project.bundleBytes.size} bundles read at once, largest ${mib(largest)} MiB, peak retained ${mib(peak)} MiB")
        assertTrue(peak <= MANY_BUNDLES_PEAK_BYTES, "${project.bundleBytes.size} bundles read at once kept ${mib(peak)} MiB")
    }

    private class Project(val index: ProjectIndex, val bundleBytes: Map<String, ByteArray>, val keys: BundleKeyProvider?) {
        var inFlight = 0
        var maxInFlight = 0

        suspend fun fetch(url: String): ByteArray {
            val body = bundleBytes.getValue(url).copyOf()
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(DOWNLOAD_MILLIS)
            inFlight--
            return body
        }
    }

    private fun project(folders: Int, membersEach: Int, sealed: Boolean = false): Project =
        project(List(folders) { List(membersEach) { MEMBER_BYTES } }, sealed)

    /** A bundle per entry of [sizes], each a list of its members' sizes. */
    private fun project(sizes: List<List<Int>>, sealed: Boolean = false): Project {
        val random = Random(SEED)
        val files = LinkedHashMap<String, ByteArray>()
        sizes.forEachIndexed { folder, members ->
            members.forEachIndexed { member, size -> files["region$folder/part$member.bin"] = random.nextBytes(size) }
        }
        val bundles = LinkedHashMap<String, ByteArray>()
        val index = runSuspend {
            ContentBundleWriter(targetBundleBytes = Long.MAX_VALUE, encryption = BundleEncryptionKey(KEY_ID, KEY).takeIf { sealed })
                .write(looseIndexOf(files), { files.getValue(it.path) }) { bundles[it.url] = it.bytes }
        }
        files.clear()
        return Project(index, bundles, BundleKeyProvider { KEY }.takeIf { sealed })
    }

    /** The most heap retained over the test's own data at the cache's first, middle and last writes. */
    private suspend fun peakRetained(project: Project): Long {
        val baseline = retained()
        var peak = 0L
        var writes = 0
        val members = project.index.files.size
        val cache = object : ContentCache {
            override suspend fun get(sha256: String): ByteArray? = null

            override suspend fun put(sha256: String, bytes: ByteArray) {
                writes++
                if (writes == 1 || writes == members / 2 || writes == members) peak = maxOf(peak, retained() - baseline)
            }
        }
        val source = IndexedAssetSource(project.index, project::fetch, cache, keys = project.keys)
        source.prefetch(project.index.files.map { it.path })
        source.close()
        return peak
    }

    private fun retained(): Long {
        val runtime = Runtime.getRuntime()
        repeat(GC_PASSES) { System.gc() }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun mib(bytes: Number): Long = bytes.toLong() / (1024 * 1024)

    private companion object {
        const val SEED = 1619
        const val MEMBER_BYTES = 1_310_720 // 1.25 MiB: 64 make an 80 MiB bundle
        const val DOWNLOAD_MILLIS = 10L
        const val GC_PASSES = 3
        const val KEY_ID = "region-pack"
        const val MANY_BUNDLES_PEAK_BYTES = 40L * 1024 * 1024

        /** 195 bundles of 0.1 to 0.5 MiB in 4 to 23 members, and five of 12 to 15.5 MiB in 16. */
        val MANY_BUNDLES: List<List<Int>> = Random(805).let { random ->
            List(200) { bundle ->
                val large = bundle % 40 == 7
                val bytes = if (large) 12 * 1024 * 1024 + random.nextInt(3_600_000) else 100_000 + random.nextInt(400_000)
                val members = if (large) 16 else 4 + random.nextInt(20)
                List(members) { bytes / members }
            }
        }
        val KEY = ByteArray(BundleEncryptionKey.KEY_BYTES) { (it * 13 + 5).toByte() }
    }
}
