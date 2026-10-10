/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.project.AssetMismatch
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.LockVerifiedAssetSource

/** The asset lock a project keeps beside its manifest, pinning each asset's SHA-256 and size. */
const val PROJECT_ASSETS_LOCK = "assets.lock.json"

private val log = Logger("project.assets")

/**
 * These project files, with each asset the project's [PROJECT_ASSETS_LOCK] pins checked against its
 * pin as it loads; see [LockVerifiedAssetSource]. A mismatch is logged as a warning unless
 * [onMismatch] says otherwise, and the asset still loads unless [refuseMismatches] is true. Opt in by
 * loading through it: `loadProject(files.verifiedByAssetsLock())`.
 *
 * A project with no lock has nothing to check against, so its files come back unchecked, with a
 * warning. The lock is read from these same files, which catches corruption and casual edits; a game
 * that must catch deliberate ones passes a [LockVerifiedAssetSource] a lock it ships in its code.
 *
 * @throws IllegalStateException When the lock is there but isn't a valid lock.
 */
suspend fun AssetSource.verifiedByAssetsLock(
    refuseMismatches: Boolean = false,
    onMismatch: (AssetMismatch) -> Unit = ::warnOfMismatch,
): AssetSource {
    val text = read(AssetPath(PROJECT_ASSETS_LOCK)).getOrElse { error ->
        log.warn { "Can't read the project's $PROJECT_ASSETS_LOCK (${error.message ?: error}), so its assets load unchecked." }
        return this
    }.decodeToString()
    val lock = try {
        AwakeProjectValidator.decodeAssetsLock(text)
    } catch (error: IllegalArgumentException) {
        // What the decoder throws for text that isn't a lock, its SerializationException included.
        throw IllegalStateException("$PROJECT_ASSETS_LOCK isn't a valid asset lock: ${error.message}", error)
    }
    return LockVerifiedAssetSource(this, lock, refuseMismatches, onMismatch)
}

private fun warnOfMismatch(mismatch: AssetMismatch) {
    log.warn {
        val actual = mismatch.actualSha256?.let { "SHA-256 $it" } ?: "${mismatch.actualSizeBytes} bytes, not ${mismatch.expectedSizeBytes}"
        "${mismatch.path} doesn't match $PROJECT_ASSETS_LOCK: expected SHA-256 ${mismatch.expectedSha256}, found $actual."
    }
}
