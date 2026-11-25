/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What [BundleMemoryLimits] lets an [IndexedAssetSource] hold, and how [StagedMembers] keeps to it (#1619). */
class BundleMemoryLimitsTest {
    private val files = linkedMapOf<String, ByteArray>().apply {
        repeat(FOLDERS) { folder -> repeat(MEMBERS) { member -> put("f$folder/m$member.bin", ByteArray(MEMBER_BYTES) { (folder * 31 + member + it).toByte() }) } }
    }
    private val project = BundledProject(files)

    @Test
    fun aPrefetchHoldsAsManyBundlesAtOnceAsTheHostAllows() = runTest {
        for (allowed in listOf(1, 2, FOLDERS)) {
            var inFlight = 0
            var most = 0
            val server = project.server()
            val fetch: suspend (String) -> ByteArray = { url ->
                inFlight++
                most = maxOf(most, inFlight)
                delay(1)
                inFlight--
                server(url)
            }
            IndexedAssetSource(project.index, fetch, MemoryContentCache(), limits = BundleMemoryLimits(concurrentBundleFetches = allowed))
                .prefetch(files.keys.toList())
            assertEquals(allowed, most, "bundles fetched at once with $allowed allowed")
        }
    }

    @Test
    fun withNoStagingBudgetEachReadOfABundleFetchesItAgain() = runTest {
        var fetches = 0
        val server = project.server()
        val source = IndexedAssetSource(project.index, { fetches++; server(it) }, limits = BundleMemoryLimits(stagedMemberBytes = 0))
        files.keys.filter { it.startsWith("f0/") }.forEach { path ->
            assertContentEquals(files.getValue(path), source.read(AssetPath(path)).getOrThrow())
        }
        assertEquals(MEMBERS, fetches)
    }

    @Test
    fun aBundleThatFitsIsKeptOnceAndEachMemberCopiedOutWhenTaken() {
        val staged = StagedMembers(budgetBytes = bundleBytes(0).size.toLong())
        val first = fetched(0)
        staged.stage(first, members(0))
        assertEquals(first.sizeBytes.toLong(), staged.heldBytes)
        members(0).forEach { member ->
            assertContentEquals(files.getValue(member.key), runSuspend { staged.take(member)!!.bytes() })
        }
        assertEquals(0L, staged.heldBytes)
        assertNull(staged.take(members(0).first()))
    }

    @Test
    fun stagingANewBundleDropsTheOldestWhole() {
        val staged = StagedMembers(budgetBytes = bundleBytes(0).size.toLong())
        staged.stage(fetched(0), members(0))
        staged.stage(fetched(1), members(1))
        assertEquals(bundleBytes(1).size.toLong(), staged.heldBytes)
        members(0).forEach { assertNull(staged.take(it)) }
        assertContentEquals(files.getValue(members(1).last().key), runSuspend { staged.take(members(1).last())!!.bytes() })
    }

    @Test
    fun aBundleLargerThanTheBudgetHasMembersCopiedOutUpToIt() {
        val budget = (MEMBER_BYTES * 2).toLong()
        val staged = StagedMembers(budget)
        staged.stage(fetched(0), members(0))
        assertTrue(staged.heldBytes <= budget, "held ${staged.heldBytes} of a $budget budget")
        val kept = members(0).mapNotNull { member -> staged.take(member)?.let { member to runSuspend { it.bytes() } } }
        assertEquals(2, kept.size)
        kept.forEach { (member, bytes) -> assertContentEquals(files.getValue(member.key), bytes) }
    }

    @Test
    fun limitsRefuseNonsense() {
        assertFailsWith<IllegalArgumentException> { BundleMemoryLimits(concurrentBundleFetches = 0) }
        assertFailsWith<IllegalArgumentException> { BundleMemoryLimits(stagedMemberBytes = -1) }
        assertFailsWith<IllegalArgumentException> { BundleMemoryLimits(rangedMemberAboveBytes = -1) }
    }

    @Test
    fun rangedThresholdSplitsPrefetchBetweenWholeAndRange() = runTest {
        val mixedFiles = mixedSizeFiles()
        val mixed = BundledProject(mixedFiles)
        val smallBundle = mixed.index.bundles.single { b -> mixed.index.files.any { it.path.startsWith("small/") && it.bundle == b.sha256 } }
        val largeBundle = mixed.index.bundles.single { b -> mixed.index.files.any { it.path.startsWith("large/") && it.bundle == b.sha256 } }
        val threshold = smallBundle.sizeBytes
        assertTrue(largeBundle.sizeBytes > threshold)

        val wholeUrls = mutableListOf<String>()
        val rangeUrls = mutableListOf<String>()
        val server = mixed.server()
        val source = IndexedAssetSource(
            index = mixed.index,
            fetch = { url -> server(url).also { wholeUrls += url } },
            cache = MemoryContentCache(),
            rangeFetch = { url, offset, length ->
                rangeUrls += url
                mixed.bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
            },
            limits = BundleMemoryLimits(rangedMemberAboveBytes = threshold),
        )

        val progress = mutableListOf<Pair<Long, Long>>()
        source.prefetch(mixedFiles.keys.toList()) { done, total -> progress += done to total }
        assertEquals(listOf(smallBundle.url), wholeUrls, "small bundle should be fetched whole once")
        assertEquals(MEMBERS, rangeUrls.size, "large bundle should be fetched member by member")
        assertTrue(rangeUrls.all { it == largeBundle.url })
        assertEquals(mixedFiles.values.sumOf { it.size.toLong() }, progress.last().first)

        // Repeat prefetch and reads hit cache with zero extra network calls.
        wholeUrls.clear()
        rangeUrls.clear()
        source.prefetch(mixedFiles.keys.toList())
        mixedFiles.forEach { (path, expected) -> assertContentEquals(expected, source.read(AssetPath(path)).getOrThrow()) }
        assertTrue(wholeUrls.isEmpty() && rangeUrls.isEmpty(), "cached files must not re-fetch")
        source.close()
    }

    @Test
    fun rangedThresholdSplitsUncachedReadsBetweenWholeAndRange() = runTest {
        val mixedFiles = mixedSizeFiles()
        val mixed = BundledProject(mixedFiles)
        val smallBundle = mixed.index.bundles.single { b -> mixed.index.files.any { it.path.startsWith("small/") && it.bundle == b.sha256 } }
        val largeBundle = mixed.index.bundles.single { b -> mixed.index.files.any { it.path.startsWith("large/") && it.bundle == b.sha256 } }
        val wholeUrls = mutableListOf<String>()
        val rangeUrls = mutableListOf<String>()
        val server = mixed.server()
        val uncached = IndexedAssetSource(
            index = mixed.index,
            fetch = { url -> server(url).also { wholeUrls += url } },
            rangeFetch = { url, offset, length ->
                rangeUrls += url
                mixed.bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
            },
            limits = BundleMemoryLimits(rangedMemberAboveBytes = smallBundle.sizeBytes),
        )
        mixedFiles.forEach { (path, expected) -> assertContentEquals(expected, uncached.read(AssetPath(path)).getOrThrow()) }
        assertEquals(listOf(smallBundle.url), wholeUrls, "uncached small reads fetch whole once and stage")
        assertEquals(MEMBERS, rangeUrls.size, "uncached large reads fetch each member by range")
        assertTrue(rangeUrls.all { it == largeBundle.url })
        uncached.close()
    }

    private fun mixedSizeFiles(): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        repeat(MEMBERS) { m -> put("small/s$m.bin", ByteArray(SMALL_MEMBER_BYTES) { (m + it).toByte() }) }
        repeat(MEMBERS) { m -> put("large/l$m.bin", ByteArray(LARGE_MEMBER_BYTES) { (m * 7 + it).toByte() }) }
    }

    @Test
    fun rangedThresholdWorksWithSealedBundlesAndFallsBackWithoutRangeFetch() = runTest {
        val mixedFiles = mixedSizeFiles()
        val key = ByteArray(BundleEncryptionKey.KEY_BYTES) { (it * 5 + 3).toByte() }
        val bundles = LinkedHashMap<String, WrittenContentBundle>()
        val index = ContentBundleWriter(encryption = BundleEncryptionKey("k1", key))
            .write(looseIndexOf(mixedFiles), { mixedFiles.getValue(it.path) }) { bundles[it.url] = it }
        val smallSize = index.bundles.minOf { it.sizeBytes }
        var wholeCount = 0
        var rangeCount = 0
        val source = IndexedAssetSource(
            index = index,
            fetch = { url ->
                wholeCount++
                bundles.getValue(url).bytes
            },
            cache = MemoryContentCache(),
            rangeFetch = { url, offset, length ->
                rangeCount++
                bundles.getValue(url).bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
            },
            keys = { key },
            limits = BundleMemoryLimits(rangedMemberAboveBytes = smallSize),
        )
        source.prefetch(mixedFiles.keys.toList())
        assertEquals(1, wholeCount)
        assertEquals(MEMBERS, rangeCount)
        mixedFiles.forEach { (path, expected) ->
            assertContentEquals(expected, source.read(AssetPath(path)).getOrThrow())
        }
        source.close()

        // Without rangeFetch, threshold is ignored and both bundles fetch whole.
        var fallbackWhole = 0
        val noRange = IndexedAssetSource(
            index = index,
            fetch = { url ->
                fallbackWhole++
                bundles.getValue(url).bytes
            },
            cache = MemoryContentCache(),
            keys = { key },
            limits = BundleMemoryLimits(rangedMemberAboveBytes = 0L),
        )
        noRange.prefetch(mixedFiles.keys.toList())
        assertEquals(2, fallbackWhole)
        noRange.close()
    }

    private fun members(folder: Int): List<ProjectIndexEntry> = project.index.files.filter { it.key.startsWith("f$folder/") }

    private fun bundleBytes(folder: Int): ByteArray = project.bundles.getValue(members(folder).first().bundleUrl()).bytes

    private fun fetched(folder: Int) = FetchedBundle(bundleBytes(folder), project.index, seal = null)

    private fun ProjectIndexEntry.bundleUrl(): String = project.index.bundles.single { it.sha256 == bundle }.url

    private companion object {
        const val FOLDERS = 3
        const val MEMBERS = 4
        const val MEMBER_BYTES = 1_000
        const val SMALL_MEMBER_BYTES = 200
        const val LARGE_MEMBER_BYTES = 4_000
    }
}
