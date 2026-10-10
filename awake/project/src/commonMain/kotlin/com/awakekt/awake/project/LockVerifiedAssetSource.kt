/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256

/**
 * An asset whose bytes don't match its pin in the project's asset lock: modified, replaced or
 * corrupted since the lock was written.
 *
 * @property path The asset's path, as the lock names it.
 * @property expectedSha256 The SHA-256 the lock pins, in lowercase hex.
 * @property actualSha256 The SHA-256 of the bytes read, or null when their size already differed
 * from [expectedSizeBytes] and they weren't hashed.
 * @property expectedSizeBytes The size the lock pins, or null when it pins none.
 * @property actualSizeBytes The size of the bytes read.
 */
data class AssetMismatch(
    val path: String,
    val expectedSha256: String,
    val actualSha256: String?,
    val expectedSizeBytes: Long?,
    val actualSizeBytes: Long,
)

/**
 * A read [LockVerifiedAssetSource] refused because the asset didn't match its pin.
 *
 * @property mismatch How the asset differs from its pin.
 */
class AssetMismatchException(val mismatch: AssetMismatch) : IllegalStateException("${mismatch.path} doesn't match the asset lock: expected SHA-256 ${mismatch.expectedSha256}")

/**
 * [files], with each asset [lock] pins checked against its pin as it's read: its size, then its
 * SHA-256. A mismatch goes to [onMismatch], and the game decides what it means, such as logging it
 * or telling its server. The bytes are still returned unless [refuseMismatches] is true, when the
 * read fails with an [AssetMismatchException]. An asset the lock doesn't pin is read unchecked.
 *
 * The check costs one hash of each pinned asset as it loads. The lock itself is only as trustworthy
 * as where it comes from: one read from the same files catches corruption and casual edits, and one
 * built into the game's code, or signed, catches deliberate ones too.
 *
 * @param files Where the assets are read from.
 * @param lock The pins: the project's `assets.lock.json`, decoded.
 * @param refuseMismatches Whether a mismatched asset fails to load, rather than loading as it is.
 * @param onMismatch Told of each mismatched asset, each time it's read.
 */
class LockVerifiedAssetSource(
    private val files: AssetSource,
    lock: AwakeAssetsLock,
    private val refuseMismatches: Boolean = false,
    private val onMismatch: (AssetMismatch) -> Unit,
) : AssetSource {
    private val pins: Map<String, AwakeAssetLockEntry> = lock.assets.mapKeys { (path, _) -> path.removePrefix("/") }

    override suspend fun read(path: AssetPath): Result<ByteArray> {
        val read = files.read(path)
        val name = path.value.removePrefix("/")
        // A failed read, or an asset the lock doesn't pin, passes through unchecked.
        val mismatch = read.getOrNull()?.let { bytes -> pins[name]?.let { pin -> mismatchOf(name, bytes, pin) } } ?: return read
        onMismatch(mismatch)
        return if (refuseMismatches) Result.failure(AssetMismatchException(mismatch)) else read
    }

    private fun mismatchOf(path: String, bytes: ByteArray, pin: AwakeAssetLockEntry): AssetMismatch? {
        val expected = pin.sha256.lowercase()
        // A size that differs is a mismatch already; hashing it would only cost time.
        val actual = if (pin.sizeBytes != null && pin.sizeBytes != bytes.size.toLong()) null else Sha256.digestHex(bytes)
        if (actual == expected) return null
        return AssetMismatch(path, expected, actual, pin.sizeBytes, bytes.size.toLong())
    }
}
