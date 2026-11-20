/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

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

    private fun project(folders: Int, membersEach: Int, sealed: Boolean = false): Project {
        val random = Random(SEED)
        val files = LinkedHashMap<String, ByteArray>()
        repeat(folders) { folder ->
            repeat(membersEach) { member -> files["region$folder/part$member.bin"] = random.nextBytes(MEMBER_BYTES) }
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
        val KEY = ByteArray(BundleEncryptionKey.KEY_BYTES) { (it * 13 + 5).toByte() }
    }
}
