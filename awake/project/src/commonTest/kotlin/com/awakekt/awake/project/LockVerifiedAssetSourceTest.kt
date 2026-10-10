/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Each asset the lock pins is checked as it's read; the game decides what a mismatch means. */
class LockVerifiedAssetSourceTest {
    private val model = ByteArray(64) { it.toByte() }
    private val lock = AwakeAssetsLock(assets = mapOf("assets/crate.glb" to AwakeAssetLockEntry(Sha256.digestHex(model), model.size.toLong())))
    private val mismatches = mutableListOf<AssetMismatch>()

    @Test
    fun anAssetThatMatchesItsPinReadsWithNoMismatch() = runTest {
        val bytes = verified(files("assets/crate.glb" to model)).read(AssetPath("assets/crate.glb")).getOrThrow()

        assertContentEquals(model, bytes)
        assertTrue(mismatches.isEmpty(), "got $mismatches")
    }

    @Test
    fun aTamperedByteIsReportedWithItsHashButStillLoadsByDefault() = runTest {
        val tampered = model.copyOf().also { it[10] = 99 }

        val bytes = verified(files("assets/crate.glb" to tampered)).read(AssetPath("assets/crate.glb")).getOrThrow()

        assertContentEquals(tampered, bytes, "a mismatch is reported, not refused, unless asked")
        assertEquals(
            listOf(AssetMismatch("assets/crate.glb", Sha256.digestHex(model), Sha256.digestHex(tampered), 64, 64)),
            mismatches,
        )
    }

    @Test
    fun refusingMismatchesFailsTheReadAndNamesThePath() = runTest {
        val tampered = model.copyOf().also { it[10] = 99 }

        val failure = verified(files("assets/crate.glb" to tampered), refuse = true).read(AssetPath("assets/crate.glb")).exceptionOrNull()

        assertTrue(failure is AssetMismatchException, "got $failure")
        assertTrue(failure.message.orEmpty().contains("assets/crate.glb"), "the failure names the file: ${failure.message}")
        assertEquals(1, mismatches.size, "a refused asset is still reported")
    }

    @Test
    fun aDifferentSizeIsAMismatchWithoutHashing() = runTest {
        verified(files("assets/crate.glb" to model.copyOf(80))).read(AssetPath("assets/crate.glb")).getOrThrow()

        val mismatch = mismatches.single()
        assertNull(mismatch.actualSha256, "a size that differs isn't hashed")
        assertEquals(64, mismatch.expectedSizeBytes)
        assertEquals(80, mismatch.actualSizeBytes)
    }

    @Test
    fun aPinWithNoSizeIsCheckedByItsHash() = runTest {
        val unsized = AwakeAssetsLock(assets = mapOf("assets/crate.glb" to AwakeAssetLockEntry(Sha256.digestHex(model).uppercase())))
        val source = LockVerifiedAssetSource(files("assets/crate.glb" to model.copyOf(80)), unsized) { mismatches += it }

        source.read(AssetPath("assets/crate.glb")).getOrThrow()
        LockVerifiedAssetSource(files("assets/crate.glb" to model), unsized) { mismatches += it }.read(AssetPath("assets/crate.glb"))

        assertEquals(1, mismatches.size, "the longer file mismatches and the original, against an uppercase pin, matches")
        assertEquals(Sha256.digestHex(model.copyOf(80)), mismatches.single().actualSha256)
    }

    @Test
    fun anAssetTheLockDoesNotPinReadsUnchecked() = runTest {
        val scene = "{\"version\": 1}".encodeToByteArray()

        val bytes = verified(files("scenes/main.scene.json" to scene)).read(AssetPath("scenes/main.scene.json")).getOrThrow()

        assertContentEquals(scene, bytes)
        assertTrue(mismatches.isEmpty())
    }

    @Test
    fun aLeadingSlashNamesTheSameAssetOnEitherSide() = runTest {
        val tampered = model.copyOf().also { it[0] = 99 }
        val slashed = AwakeAssetsLock(assets = lock.assets.mapKeys { (path, _) -> "/$path" })

        LockVerifiedAssetSource(files("/assets/crate.glb" to tampered), slashed) { mismatches += it }.read(AssetPath("/assets/crate.glb"))

        assertEquals(listOf("assets/crate.glb"), mismatches.map(AssetMismatch::path))
    }

    @Test
    fun aFailedReadPassesThroughUnchecked() = runTest {
        val failure = verified(files()).read(AssetPath("assets/crate.glb")).exceptionOrNull()

        assertTrue(failure is NoSuchElementException, "got $failure")
        assertTrue(mismatches.isEmpty())
    }

    private fun verified(files: AssetSource, refuse: Boolean = false) =
        LockVerifiedAssetSource(files, lock, refuseMismatches = refuse) { mismatches += it }

    private fun files(vararg entries: Pair<String, ByteArray>): AssetSource {
        val stored = entries.toMap()
        return AssetSource { path ->
            stored[path.value]?.let { Result.success(it) } ?: Result.failure(NoSuchElementException(path.value))
        }
    }
}
