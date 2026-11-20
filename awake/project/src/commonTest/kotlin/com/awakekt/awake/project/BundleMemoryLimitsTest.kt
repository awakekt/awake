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
    }

    private fun members(folder: Int): List<ProjectIndexEntry> = project.index.files.filter { it.key.startsWith("f$folder/") }

    private fun bundleBytes(folder: Int): ByteArray = project.bundles.getValue(members(folder).first().bundleUrl()).bytes

    private fun fetched(folder: Int) = FetchedBundle(bundleBytes(folder), project.index, seal = null)

    private fun ProjectIndexEntry.bundleUrl(): String = project.index.bundles.single { it.sha256 == bundle }.url

    private companion object {
        const val FOLDERS = 3
        const val MEMBERS = 4
        const val MEMBER_BYTES = 1_000
    }
}
