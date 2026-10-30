/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetSource

/**
 * An [AssetSource] that can fetch files ahead of their reads and count the bytes as they arrive, so a
 * loading screen can say how far a load has come. [IndexedAssetSource] is one, and a source that wraps
 * another, such as [LockVerifiedAssetSource], is one too and passes both members on to the source it
 * wraps, so wrapping a source keeps its progress.
 *
 * A wrapper that reads a file from another path than the one it is asked for maps
 * [prefetchWithProgress]'s paths the same way before passing them on; one that makes up a file's bytes
 * itself, rather than reading them, leaves that file out of what it passes on.
 */
interface PrefetchableAssetSource : AssetSource {
    /**
     * Whether [prefetchWithProgress] pays off: what it fetches is kept for the reads that follow it.
     * False when it would only be fetched again, as for an [IndexedAssetSource] with no [ContentCache],
     * or for a wrapper whose source can't prefetch; a caller then skips the prefetch and reports no
     * bytes.
     */
    val canPrefetch: Boolean

    /**
     * Fetches every one of [paths] ahead of play, so their reads find them already fetched, telling
     * [onProgress] the bytes done of the bytes [paths] add up to as each download finishes, on the
     * calling coroutine. Throws for a path this source doesn't have, and for a file that fails its
     * check. A source that can't prefetch, whose [canPrefetch] is false, may fetch nothing and report
     * nothing.
     *
     * It is not named `prefetch` so that [IndexedAssetSource.prefetch], released before this contract
     * with a default [onProgress], keeps its signature for code already compiled against it: an
     * override can't declare a default. [IndexedAssetSource] answers this by calling that method.
     */
    suspend fun prefetchWithProgress(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit)
}
