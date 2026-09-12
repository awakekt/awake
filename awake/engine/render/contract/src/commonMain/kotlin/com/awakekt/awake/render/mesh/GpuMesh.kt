/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.mesh

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb

/**
 * An allocated GPU vertex and optional index buffer handle residing in VRAM.
 *
 * This represents the pure hardware resource handle used by GPU command recording.
 * Holds zero scene or game-specific asset data.
 */
interface GpuMesh {
    /** The vertex layout this mesh's GPU buffer was built with. */
    val format: VertexFormat

    /** The local-space bounding box of this mesh's vertices, or null if uncomputed. */
    val bounds: Aabb? get() = null
}
