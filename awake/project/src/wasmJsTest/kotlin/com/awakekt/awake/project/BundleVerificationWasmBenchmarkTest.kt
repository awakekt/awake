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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Measures whole-bundle verification cost on WebAssembly before and after trusting the bundle's
 * table of contents once the bundle's own SHA-256 matches.
 *
 * Kept comfortably within Karma/Mocha's 2000 ms per-test budget on CI runners while exercising
 * multi-megabyte 64-member bundles in headless Chrome WebAssembly.
 */
class BundleVerificationWasmBenchmarkTest {
    @Test
    fun oneMebibyteBundleVerificationBenchmark() = runTest {
        warmUpWasmTierUp()
        val report = measureCase(memberCount = 64, memberBytes = 16 * 1024, rounds = 3)

        println("Wasm bundle verification (1 MiB, 64 x 16 KiB): before=${report.beforeMs} ms, after=${report.afterMs} ms, speedup=${report.speedup}x")
        assertTrue(report.verifiedBytes == 1024L * 1024)
    }

    @Test
    fun fourMebibyteBundleVerificationBenchmark() = runTest {
        warmUpWasmTierUp()
        val report = measureCase(memberCount = 64, memberBytes = 64 * 1024, rounds = 2)

        println("Wasm bundle verification (4 MiB, 64 x 64 KiB): before=${report.beforeMs} ms, after=${report.afterMs} ms, speedup=${report.speedup}x")
        assertTrue(report.verifiedBytes == 4L * 1024 * 1024)
    }

    private suspend fun warmUpWasmTierUp() {
        measureCase(memberCount = 8, memberBytes = 16 * 1024, rounds = 1)
    }

    private suspend fun measureCase(memberCount: Int, memberBytes: Int, rounds: Int): CaseReport {
        val files = LinkedHashMap<String, ByteArray>(memberCount)
        for (index in 0 until memberCount) {
            val name = index.toString().padStart(3, '0')
            files["assets/chunk-$name.bin"] = ByteArray(memberBytes) { ((index * 17) + it).toByte() }
        }
        val bundled = BundledProject(
            files = files,
            writer = ContentBundleWriter(
                grouping = ContentBundleGrouping.oneGroup(),
                targetBundleBytes = (memberCount.toLong() * memberBytes) + (1024L * 1024),
            ),
        )
        assertEquals(1, bundled.bundles.size, "packed into one bundle for whole-bundle measurement")
        val bundle = bundled.index.bundles.single()
        val rawBundleBytes = bundled.bundles.getValue(bundle.url).bytes
        val members = bundled.index.files

        val beforeTotal = measureTotal(rounds) {
            verifyWithMemberRehashing(rawBundleBytes, bundle, members)
        }
        val afterTotal = measureTotal(rounds) {
            loadThroughAssetSource(bundled.index, rawBundleBytes)
        }
        val beforeMs = beforeTotal.inWholeMicroseconds / 1000.0 / rounds
        val afterMs = afterTotal.inWholeMicroseconds / 1000.0 / rounds
        val speedup = if (afterMs > 0.0) ((beforeMs / afterMs) * 100.0).toInt() / 100.0 else 0.0
        return CaseReport(
            beforeMs = ((beforeMs * 100.0).toInt() / 100.0),
            afterMs = ((afterMs * 100.0).toInt() / 100.0),
            speedup = speedup,
            verifiedBytes = members.sumOf { it.sizeBytes },
        )
    }

    private suspend fun measureTotal(rounds: Int, block: suspend () -> Unit): Duration {
        val mark = TimeSource.Monotonic.markNow()
        repeat(rounds) { block() }
        return mark.elapsedNow()
    }

    /** The pre-#1038 check: hashes every member slice and also hashes the whole bundle. */
    private fun verifyWithMemberRehashing(
        bytes: ByteArray,
        bundle: ProjectIndexBundle,
        members: List<ProjectIndexEntry>,
    ) {
        val verified = HashMap<String, ByteArray>(members.size)
        for (member in members) {
            val start = member.offset!!.toInt()
            val slice = bytes.copyOfRange(start, (start + member.sizeBytes).toInt())
            check(Sha256.digestHex(slice) == member.sha256)
            verified[member.sha256] = slice
        }
        check(bytes.size.toLong() == bundle.sizeBytes && Sha256.digestHex(bytes) == bundle.sha256)
        assertEquals(members.size, verified.size)
    }

    /** The post-#1038 check through [IndexedAssetSource]: hashes the whole bundle once and trusts its TOC. */
    private suspend fun loadThroughAssetSource(index: ProjectIndex, rawBundleBytes: ByteArray) {
        val source = IndexedAssetSource(index, fetch = { rawBundleBytes })
        for (entry in index.files) {
            val loaded = source.read(AssetPath(entry.path)).getOrThrow()
            assertEquals(entry.sizeBytes.toInt(), loaded.size)
        }
    }

    private class CaseReport(
        val beforeMs: Double,
        val afterMs: Double,
        val speedup: Double,
        val verifiedBytes: Long,
    )
}
