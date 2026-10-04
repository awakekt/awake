/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Aabb
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
    var mesh: GpuMesh,
    var material: GpuMaterial,
    var model: Mat4 = Mat4(),
    /** Format-specific extra lanes; the selected pipeline's declared layout owns them. */
    var extraUniformFloats: FloatArray = EMPTY_UNIFORM_FLOATS,
    var vertexAnimation: Vec3f = Vec3f.ZERO,
    var timeSeconds: Float = 0f,
    var instanceModels: List<Mat4>? = null,
    var instanceJointPalettes: List<FloatArray>? = null,
    var instanceColors: List<Vec4>? = null,
    var instanceFrames: List<Float>? = null,
    var cullMode: CullMode = CullMode.None,
    var alphaMode: AlphaMode = AlphaMode.Opaque,
    var alphaCutoff: Float = DEFAULT_ALPHA_CUTOFF,
    var transparent: Boolean = false,
    /** Drawn into shadow maps only, never into the scene: a stand-in caster for something the
     * scene draws another way, such as GPU-displaced terrain. */
    var shadowsOnly: Boolean = false,
    /** World-space bounds, when known, so a shadow pass can skip a caster it cannot see. Null
     * casts into every shadow pass. */
    var worldBounds: Aabb? = null,
    /** With [transparent], adds its colour to what is behind it rather than covering it: glows,
     * fire, light shafts. */
    var additive: Boolean = false,
) {
    /** Mutates this request in place so a pool can reuse the instance without allocating. */
    @Suppress("LongParameterList")
    fun set(
        mesh: GpuMesh,
        material: GpuMaterial,
        model: Mat4 = Mat4(),
        extraUniformFloats: FloatArray = EMPTY_UNIFORM_FLOATS,
        vertexAnimation: Vec3f = Vec3f.ZERO,
        timeSeconds: Float = 0f,
        instanceModels: List<Mat4>? = null,
        instanceJointPalettes: List<FloatArray>? = null,
        instanceColors: List<Vec4>? = null,
        instanceFrames: List<Float>? = null,
        cullMode: CullMode = CullMode.None,
        alphaMode: AlphaMode = AlphaMode.Opaque,
        alphaCutoff: Float = DEFAULT_ALPHA_CUTOFF,
        transparent: Boolean = false,
        shadowsOnly: Boolean = false,
        worldBounds: Aabb? = null,
        additive: Boolean = false,
    ): GpuDrawRequest {
        this.mesh = mesh
        this.material = material
        this.model = model
        this.extraUniformFloats = extraUniformFloats
        this.vertexAnimation = vertexAnimation
        this.timeSeconds = timeSeconds
        this.instanceModels = instanceModels
        this.instanceJointPalettes = instanceJointPalettes
        this.instanceColors = instanceColors
        this.instanceFrames = instanceFrames
        this.cullMode = cullMode
        this.alphaMode = alphaMode
        this.alphaCutoff = alphaCutoff
        this.transparent = transparent
        this.shadowsOnly = shadowsOnly
        this.worldBounds = worldBounds
        this.additive = additive
        return this
    }
}

private val EMPTY_UNIFORM_FLOATS = FloatArray(0)
private const val DEFAULT_ALPHA_CUTOFF = 0.5f
