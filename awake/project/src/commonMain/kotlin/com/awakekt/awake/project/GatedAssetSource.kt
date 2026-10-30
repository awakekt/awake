/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource

/**
 * An [AssetSource] some of whose files can't be read until the app does something, such as attach a
 * signed-in player's protected content to a [LayeredAssetSource]. A scene switch asks [isGated] before
 * it loads a scene, and while it is true it reports that it is waiting and suspends in [awaitOpen]
 * rather than failing.
 *
 * A source that wraps another, such as [LockVerifiedAssetSource], passes both members on to the source
 * it wraps, so wrapping a source keeps its gate.
 */
interface GatedAssetSource : AssetSource {
    /** Whether a read of [path] can't succeed yet, and would once the gate opens. */
    suspend fun isGated(path: AssetPath): Boolean

    /** Suspends until the gate on [path] opens; at once when it is open. Cancelling the caller cancels the wait. */
    suspend fun awaitOpen(path: AssetPath)
}
