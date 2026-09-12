/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode

/**
 * Backend-neutral request for one GPU draw.
 *
 * This is a preparation input, not an RHI submission packet: it contains opaque resource handles
 * and generic draw state, while [GpuResolvedDraw] is the only type accepted by the renderer's
 * submission path. Scene modules may alias this as their authored draw command, but the request
 * itself contains no ECS, camera, material-authoring, or backend type.
 */
data class GpuDrawRequest(
    val mesh: GpuMesh,
    val material: GpuMaterial,
    val model: Mat4 = Mat4(),
    /** Format-specific extra lanes; the selected pipeline's declared layout owns them. */
    val extraUniformFloats: FloatArray = EMPTY_UNIFORM_FLOATS,
    val vertexAnimation: Vec3f = Vec3f.ZERO,
    val timeSeconds: Float = 0f,
    val instanceModels: List<Mat4>? = null,
    val instanceJointPalettes: List<FloatArray>? = null,
    val instanceColors: List<Vec4>? = null,
    val instanceFrames: List<Float>? = null,
    val cullMode: CullMode = CullMode.None,
    val alphaMode: AlphaMode = AlphaMode.Opaque,
    val alphaCutoff: Float = DEFAULT_ALPHA_CUTOFF,
    val transparent: Boolean = false,
)

private val EMPTY_UNIFORM_FLOATS = FloatArray(0)
private const val DEFAULT_ALPHA_CUTOFF = 0.5f
