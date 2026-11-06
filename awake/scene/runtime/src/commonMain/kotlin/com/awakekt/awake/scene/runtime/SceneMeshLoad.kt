/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

/**
 * What a [SceneAssetResolver] loads before it can build a mesh, such as a model file it reads and decodes
 * off the frame thread, as [SceneAssetResolver.loadMesh] starts it.
 *
 * Made, [poll]ed and [close]d on the frame thread. Loads of one file share one read and decode, however
 * many callers wait for it, so closing one stops it only once nothing else waits.
 */
interface SceneMeshLoad : AutoCloseable {
    /**
     * Whether the load has finished, loaded or [failure]d. The poll that first sees it loaded makes the
     * mesh resolvable, so the resolver's [SceneAssetResolver.createMesh] builds it from then on.
     */
    fun poll(): Boolean

    /** Why the load failed, once [poll] says it finished; null while it runs, or when it loaded. */
    val failure: Throwable?

    /**
     * Stops waiting for the load. It is cancelled once nothing else waits for it; one that finished is
     * kept. Closing twice is harmless.
     */
    override fun close()
}
