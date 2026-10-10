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
 *
 * **Lifetime.** A producer may pool and rewrite requests (see [set]): a request, and the [model]
 * and [worldBounds] it references, are valid only until the producer's next collection. Read
 * what you need within the pass that received the request, and copy anything that must outlive
 * it. Because the properties are mutable, [equals] and [hashCode] can change after construction,
 * so do not use a request as a map key or set element.
 */
data class GpuDrawRequest(
    /** The GPU mesh geometry to draw. */
    var mesh: GpuMesh,
    /** The GPU material to bind for shading. */
    var material: GpuMaterial,
    /** The world transform model matrix for this draw. */
    var model: Mat4 = Mat4(),
    /** Format-specific extra lanes; the selected pipeline's declared layout owns them. */
    var extraUniformFloats: FloatArray = EMPTY_UNIFORM_FLOATS,
    /** Parameter vector passed to vertex animation shaders (e.g. wind/sway offset). */
    var vertexAnimation: Vec3f = Vec3f.ZERO,
    /** Current elapsed simulation time in seconds for shader animations. */
    var timeSeconds: Float = 0f,
    /** List of per-instance model transform matrices for instanced rendering, or `null`. */
    var instanceModels: List<Mat4>? = null,
    /** List of per-instance skeletal joint matrix palettes, or `null`. */
    var instanceJointPalettes: List<FloatArray>? = null,
    /** List of per-instance tint colors, or `null`. */
    var instanceColors: List<Vec4>? = null,
    /** List of per-instance animation playback frames, or `null`. */
    var instanceFrames: List<Float>? = null,
    /** Face culling mode to apply when rasterizing this draw. */
    var cullMode: CullMode = CullMode.None,
    /** Alpha blending and transparency discard mode. */
    var alphaMode: AlphaMode = AlphaMode.Opaque,
    /** Alpha threshold for mask discard testing when [alphaMode] is [AlphaMode.Mask]. */
    var alphaCutoff: Float = DEFAULT_ALPHA_CUTOFF,
    /** Whether this draw uses alpha blending and should be ordered during the transparent pass. */
    var transparent: Boolean = false,
    /** Drawn into shadow maps only, never into the scene: a stand-in caster for something the
     * scene draws another way. */
    var shadowsOnly: Boolean = false,
    /** World-space bounds, when known, so a shadow pass can skip a caster it cannot see. Null
     * casts into every shadow pass. */
    var worldBounds: Aabb? = null,
    /** With [transparent], adds its colour to what is behind it rather than covering it: glows,
     * fire, light shafts. */
    var additive: Boolean = false,
    /** Transparent draws with higher orders blend after lower orders; ties use camera depth. */
    var sortOrder: Int = 0,
    /**
     * The layer of the frame's mask pass this draw is also drawn into, depth only and from the
     * camera, or [NO_MASK_LAYER]. What a mask is for, such as an outline drawn around it, is the
     * business of whatever samples it.
     */
    var maskLayer: Int = NO_MASK_LAYER,
) {
    /**
     * Mutates this request in place so a pool can reuse the instance without allocating.
     * [model] and [vertexAnimation] have no default: `Mat4()` and `Vec3f.ZERO` each build a new
     * object on every read, so a default would allocate per call.
     */
    @Suppress("LongParameterList")
    fun set(
        mesh: GpuMesh,
        material: GpuMaterial,
        model: Mat4,
        extraUniformFloats: FloatArray = EMPTY_UNIFORM_FLOATS,
        vertexAnimation: Vec3f,
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
        sortOrder: Int = 0,
        maskLayer: Int = NO_MASK_LAYER,
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
        this.sortOrder = sortOrder
        this.maskLayer = maskLayer
        return this
    }
}

/** A [GpuDrawRequest.maskLayer] that draws into no mask. */
const val NO_MASK_LAYER = -1

/** How many layers the engine's mask target has: [GpuDrawRequest.maskLayer] is below this. */
const val MASK_LAYER_COUNT = 2

private val EMPTY_UNIFORM_FLOATS = FloatArray(0)
private const val DEFAULT_ALPHA_CUTOFF = 0.5f
