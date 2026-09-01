/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.texture.PbrTextureSet
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.render.texture.TextureAsset

/** One named field inside a shader's `Uniforms` struct, in the order it's concatenated --
 * [type] is the field's ACTUAL WGSL type (e.g. `material : vec4f`), not a hand-picked float
 * count: [floats] is derived from it, so a field can't be sized wrong for what the shader
 * struct actually declares. */
data class UniformField(val name: String, val type: GpuDataShape, val count: Int = 1) {
    init {
        require(count >= 1) { "$name declares count=$count; a field holds at least one value." }
    }

    /** [count] elements of [type], std140-padded. An array field is [count] > 1 -- the shader
     * declares `array<vec4f, N>` and the writer expects one contiguous block of that size. */
    val floats: Int get() = type.uniformFloats * count
}

/** std140/WGSL-aligned float count for a uniform-buffer field -- [GpuDataShape.Vec3] pads to 4,
 * matching the alignment rule both backends' shaders already follow by hand today (e.g.
 * `lightDirection.w` carrying an extra scalar in its normally-unused pad slot). [GpuDataShape
 * .UInt4] has no meaningful value here -- not called for it (uniform fields are declared with
 * the other 5 cases only). See [io.github.awakelab.awake.core.geometry.vertexByteSize] for
 * the unpadded vertex-buffer counterpart of the SAME [GpuDataShape]. */
val GpuDataShape.uniformFloats: Int
    get() = when (this) {
        GpuDataShape.Vec3 -> 4
        GpuDataShape.UInt4 -> error("$this has no uniform-buffer representation.")
        else -> componentCount
    }

/** Common fields every lit shader's `Uniforms` struct draws from -- a new shader composes
 * from these instead of hand-summing a new literal; a genuinely new field (not yet covered
 * here) is added once, to this list, not to N per-shader consts. */
/**
 * How many cascades a shadow map can hold.
 *
 * Four because [UniformFields.CascadeSplits] is a `vec4` and a fragment compares against all of
 * them branchlessly; a scene using three pays four comparisons and three depth passes. Raising
 * it means widening that field and the shader's comparison chain together.
 */
const val MAX_SHADOW_CASCADES = 4

object UniformFields {
    val Mvp = UniformField("mvp", GpuDataShape.Mat4)
    val LightDirection = UniformField("lightDirection", GpuDataShape.Vec4)
    val LightColor = UniformField("lightColor", GpuDataShape.Vec4)

    /** `xyz` = world position, `w` = range. A slot with `w <= 0` is off, which is how a scene
     * with fewer lights than slots costs nothing but the loop iteration. */
    val PointLightPositions =
        UniformField("pointLightPositions", GpuDataShape.Vec4, MAX_POINT_LIGHTS)

    /** `xyz` = colour already multiplied by intensity, `w` unused. Paired positionally with
     * [PointLightPositions]; the shader reads slot i from both. */
    val PointLightColors =
        UniformField("pointLightColors", GpuDataShape.Vec4, MAX_POINT_LIGHTS)
    /**
     * World space to each cascade's light clip space -- what a fragment projects into to sample
     * that cascade's layer of the shadow map.
     *
     * The world matrix, not this draw's model combined with it, which is what the single
     * `lightMvp` it replaces held. A cascade set is per FRAME, identical for every draw, and
     * folding a per-draw model into each of them would mean recomputing four matrix products per
     * draw to say the same thing.
     */
    val CascadeViewProjections =
        UniformField("cascadeViewProjections", GpuDataShape.Mat4, MAX_SHADOW_CASCADES)

    /**
     * Per cascade, how much NDC depth one world unit spans -- `1 / (far - near)` of that
     * cascade's own orthographic box.
     *
     * The shadow bias needs it. A bias expressed in NDC depth means a different WORLD offset in
     * every cascade, because each one maps a different depth range into the same 0..1: the same
     * 0.009 is a centimetre in a tight near cascade and over a metre in a loose far one, and a
     * metre of bias is a shadow visibly detached from the thing casting it.
     *
     * One `vec4` per cascade with the scale in `x`, not a single packed `vec4`: a shader picks
     * its cascade with a loop variable, and std140 pads an array to `vec4` strides regardless --
     * so the packed form would cost the same and need dynamic component indexing, which the
     * shader DSL does not express.
     */
    val CascadeDepthScales =
        UniformField("cascadeDepthScales", GpuDataShape.Vec4, MAX_SHADOW_CASCADES)

    /**
     * The ONE cascade a depth pass is currently rendering, in its own pass-scoped block.
     *
     * Not part of any material: the depth pass draws the same meshes once per cascade, so the
     * matrix has to change between draws of the same material. Per-draw uniforms are written
     * once a frame and cannot express that.
     */
    val CascadeViewProjection = UniformField("cascadeViewProjection", GpuDataShape.Mat4)
    /** xyz = backend-neutral vertex-effect parameters; w is the current frame time in seconds. */
    val VertexAnimation = UniformField("vertexAnimation", GpuDataShape.Vec4)
    val Model = UniformField("model", GpuDataShape.Mat4)
    val CameraPosition = UniformField("cameraPosition", GpuDataShape.Vec4)
    val FogColor = UniformField("fogColor", GpuDataShape.Vec4)
    val PbrFactors = UniformField("pbrFactors", GpuDataShape.Vec4)

    /** `x` = metallic, `y` = roughness, packed as a `vec4f` for std140 alignment. Distinct from
     * [PbrFactors] only in the name the shader declares -- `lit_shadow.wgsl` calls this block
     * `material` and `textured.wgsl` calls its own `pbrFactors`. Two fields rather than one
     * because [UniformWriter] matches a layout's fields by identity, so a shared instance would
     * make one of the two shaders' declared names a lie. */
    val Material = UniformField("material", GpuDataShape.Vec4)
    val BaseColorFactor = UniformField("baseColorFactor", GpuDataShape.Vec4)
    val EmissiveFactor = UniformField("emissiveFactor", GpuDataShape.Vec4)
}

/** [fields], concatenated in order, is exactly the float array each shader's uniform buffer
 * must receive -- [total] is what `createMaterial(uniformFloatCount = ...)` and each
 * backend's own uniform-buffer size must agree on. [offsetOf] is for debugging/assertions,
 * not required to build the array (concatenation order already encodes it). */
class UniformLayout(vararg val fields: UniformField) {
    val total: Int = fields.sumOf { it.floats }
    fun offsetOf(field: UniformField): Int =
        fields.takeWhile { it !== field }.sumOf { it.floats }
}

/** [Renderer.createMaterial] sized from [layout] instead of a bare `Int` -- the size a caller
 * actually has to get right (matching whatever pipeline/format this material will draw
 * through) is derived, not hand-typed. Prefer this over the raw `uniformFloatCount` parameter
 * whenever a [UniformLayout] for the target format already exists; both backends' `Material
 * .updateUniformBuffer` still assert the write fits regardless, so a wrong layout choice fails
 * loudly at the write site rather than as a native buffer-overrun. */
fun Renderer.createMaterial(
    layout: UniformLayout,
    texture: TextureAsset? = null,
    renderTarget: RenderTarget? = null,
    pbrTextures: PbrTextureSet? = null,
): Material = createMaterial(
    texture = texture,
    renderTarget = renderTarget,
    uniformFloatCount = layout.total,
    pbrTextures = pbrTextures,
)
