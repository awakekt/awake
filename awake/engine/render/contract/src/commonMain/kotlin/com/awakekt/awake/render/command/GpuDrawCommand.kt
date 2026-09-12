/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh

/**
 * A single draw command resolved to hardware primitives.
 *
 * References only hardware resource handles ([mesh], [material]), transform matrices,
 * and low-level instance buffer bindings. Holds zero scene or game-specific vocabulary.
 */
data class GpuDrawCommand(
    val mesh: Mesh,
    val material: Material,
    val transform: Mat4,
    val instances: Int = 1,
    val instanceVertexBuffer: Any? = null,
    val jointPaletteBinding: Any? = null,
    val instanceColorBuffer: Any? = null,
    val instanceFrameBuffer: Any? = null,
)
