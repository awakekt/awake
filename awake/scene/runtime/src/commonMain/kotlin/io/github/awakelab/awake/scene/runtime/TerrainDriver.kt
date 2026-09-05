/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.core.math.Vec3f

/**
 * Pluggable contract for driving terrain geometry, LOD clipmaps, and texture streaming.
 *
 * Awake supports two complementary "cousin" paradigms for rendering large terrains:
 *
 * 1. **Geometry Clipmap**:
 *    A camera-centered, multi-ring mesh with constant memory ($O(1)$) that smoothly snaps to
 *    discrete grid increments without mesh regeneration or chunk boundary seams. Best for
 *    continuous landscapes, islands, and heightmap-driven regions.
 *
 * 2. **Cell-Based Worldstream**:
 *    A spatial partitioning grid where chunks and collision meshes are streamed in and out
 *    asynchronously across $(x, z)$ cell coordinates. Best for massive multi-kilometer MMOs,
 *    multi-zone open worlds, and background asset streaming.
 *
 * Consuming game plugins or scenes accept an injected [TerrainDriver] at the composition root
 * (`Main.kt`), allowing seamless switching between open-source and commercial implementations
 * with zero runtime reflection or compile errors.
 */
interface TerrainDriver {
    /** True if the terrain meshes and scene entities are currently active and mounted. */
    val isMounted: Boolean

    /** Mounts the terrain geometry, materials, and entities into [runtime]. */
    fun attach(runtime: SceneAppLifecycleRuntime)

    /** Updates LOD rings, streaming cells, or clipmap centers around [cameraPosition]. */
    fun update(runtime: SceneAppLifecycleRuntime, cameraPosition: Vec3f)

    /** Disposes all GPU resources, meshes, and scene entities. */
    fun dispose(runtime: SceneAppLifecycleRuntime? = null)
}
