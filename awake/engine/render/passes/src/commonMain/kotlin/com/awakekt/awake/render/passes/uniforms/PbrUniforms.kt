/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ShadowCascadeUniforms
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/** No metal, half-rough -- a plain lit surface when a `RenderDrawCommand` supplies no PBR factors. */
const val DEFAULT_METALLIC = 0f
const val DEFAULT_ROUGHNESS = 0.5f

/** glTF's own defaults for `pbrMetallicRoughness`: both factors fully applied. */
const val DEFAULT_METALLIC_FACTOR = 1f
const val DEFAULT_ROUGHNESS_FACTOR = 1f

/** glTF's `baseColorFactor` default -- opaque white, i.e. the texture passes through untinted. */
val DEFAULT_BASE_COLOR_FACTOR: Color = Color.White

/** glTF's `emissiveFactor` default -- no self-illumination. Alpha is an unread pad slot. */
val DEFAULT_EMISSIVE_FACTOR: Color = Color(r = 0f, g = 0f, b = 0f, a = 0f)

/**
 * Packs `[metallic, roughness, pad, pad]` for the untextured lit path.
 *
 * @param drawCall Supplies the factors through `extraUniformFloats`, or nothing for the defaults.
 * @return Exactly [PBR_MATERIAL_FLOATS] floats.
 */
fun pbrMaterialFloats(drawCall: RenderDrawCommand): FloatArray = pbrMaterialPayload(drawCall.extraUniformFloats)

/** Packs authored PBR factors without making scene extraction know the GPU lane order. */
fun pbrMaterialFloats(
    metallic: Float,
    roughness: Float,
    baseColorFactor: Color,
    emissiveFactor: Color,
): FloatArray = UniformWriter(MaterialUniformLayouts.PbrTexturedMaterial)
    .put(UniformFields.PbrFactors, metallic, roughness, 0f, 0f)
    .put(UniformFields.BaseColorFactor, baseColorFactor)
    .put(UniformFields.EmissiveFactor, emissiveFactor.r, emissiveFactor.g, emissiveFactor.b, 0f)
    .build()

/** Same typed defaulting for backend source adapters that carry only the raw extra payload. */
internal fun pbrMaterialFloats(values: FloatArray): FloatArray = pbrMaterialPayload(values)

/**
 * Packs `[metallic, roughness, pad, pad] + baseColorFactor.rgba + emissiveFactor.rgba` for the
 * textured glTF PBR path.
 *
 * @param drawCall Supplies the factors through `extraUniformFloats`, or nothing for the defaults.
 * The third float in the metallic/roughness vec4 is reserved for the draw's alpha cutoff. The
 * lit textured shader ignores that slot today; keyed masked depth shaders can consume it without
 * changing the uniform block size or introducing a backend-specific buffer.
 *
 * @return Exactly [PBR_TEXTURED_MATERIAL_FLOATS] floats.
 */
fun pbrTexturedMaterialFloats(drawCall: RenderDrawCommand): FloatArray {
    val supplied = drawCall.extraUniformFloats
    val layout = MaterialUniformLayouts.PbrTexturedMaterial
    if (supplied.size >= layout.total) {
        val output = supplied.copyOf(layout.total)
        val factors = layout.readVec4(output, UniformFields.PbrFactors)
        layout.writeVec4(
            destination = output,
            field = UniformFields.PbrFactors,
            x = factors.x,
            y = factors.y,
            z = drawCall.alphaCutoff,
            w = factors.w,
        )
        return output
    }
    return UniformWriter(layout)
        .put(
            UniformFields.PbrFactors,
            DEFAULT_METALLIC_FACTOR,
            DEFAULT_ROUGHNESS_FACTOR,
            drawCall.alphaCutoff,
            0f,
        )
        .put(UniformFields.BaseColorFactor, DEFAULT_BASE_COLOR_FACTOR)
        .put(UniformFields.EmissiveFactor, DEFAULT_EMISSIVE_FACTOR)
        .build()
}

private fun pbrMaterialPayload(values: FloatArray): FloatArray {
    val layout = MaterialUniformLayouts.PbrMaterial
    if (values.size >= layout.total) return values.copyOf(layout.total)
    return UniformWriter(layout)
        .put(UniformFields.PbrFactors, DEFAULT_METALLIC, DEFAULT_ROUGHNESS, 0f, 0f)
        .build()
}

/**
 * The scene state every draw in a frame shares: lighting, where the eye is, and the fog.
 *
 * Grouped because they always travel together and never vary per draw -- the uniform writers
 * below took them as three flat parameters, and each new shared field made every writer's
 * signature one longer. [fog] is a `FloatArray` rather than a colour because it is renderer-wide
 * state living on each backend's own `Renderer`, already packed by the time it reaches here.
 */
class SceneFrameUniforms(
    val light: SceneLightUniforms,
    val cameraEye: Vec3f,
    val fog: FloatArray,
)

/**
 * The complete `textured.wgsl` uniform block for one draw.
 *
 * Both backends assembled this identically, field for field, in their own draw-preparation
 * files -- the exact duplicated decision the RHI boundary exists to remove. A field reordered on
 * one side and not the other produces a correctly-sized buffer full of the wrong numbers, which
 * renders without erroring.
 *
 * Field order is checked against the layout by [UniformWriter] rather than trusted to a comment.
 *
 * @param drawCall The draw whose model matrix and glTF material factors this writes.
 * @param mvp This draw's model-view-projection, already combined by the caller.
 * @param frame This frame's lights, eye position and fog -- `textured.wgsl`'s PBR specular needs
 * the view vector.
 * @return The complete uniform block, sized exactly [MaterialUniformLayouts.PbrTextured].
 */
fun texturedUniforms(
    drawCall: RenderDrawCommand,
    mvp: Mat4,
    frame: SceneFrameUniforms,
): FloatArray = UniformWriter(MaterialUniformLayouts.PbrTextured)
    .put(mvp.data, UniformFields.Mvp)
    .let(frame.light::writeTo)
    .put(drawCall.model.data, UniformFields.Model)
    .put(cameraPositionFloats(frame.cameraEye), UniformFields.CameraPosition)
    .put(
        pbrTexturedMaterialFloats(drawCall),
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
    )
    .put(frame.fog, UniformFields.FogColor)
    .build()

/** Packs the unshadowed textured PBR block from the backend-neutral draw payload. The light
 * payload is the frame block produced by [sceneLightUniforms]: directional lanes followed by
 * point-light position and colour slots. Keeping this here prevents Vulkan and WebGPU from
 * independently assembling a shorter, invalid prefix of the textured layout. */
fun texturedUniforms(
    mvp: Mat4,
    model: Mat4,
    lightPayload: FloatArray,
    extraUniformFloats: FloatArray,
    cameraEye: Vec3f,
    fogColor: Color,
    fogDensity: Float,
    alphaCutoff: Float = 0.5f,
): FloatArray = UniformWriter(MaterialUniformLayouts.PbrTextured)
    .put(mvp.data, UniformFields.Mvp)
    .put(
        lightPayload,
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
    )
    .put(model.data, UniformFields.Model)
    .put(UniformFields.CameraPosition, cameraEye)
    .putPbrTexturedFactors(extraUniformFloats, alphaCutoff)
    .put(UniformFields.FogColor, fogColor.r, fogColor.g, fogColor.b, fogDensity)
    .build()

/** Packs the ordinary untextured lit block. A draw with PBR factors uses [Lit]; a plain draw
 * uses [Primary]. The source payload may contain the textured superset, so only the four
 * directional lanes and the four PBR lanes are consumed for the smaller block. */
fun litUniforms(
    mvp: Mat4,
    lightPayload: FloatArray,
    extraUniformFloats: FloatArray,
    usePbrFactors: Boolean,
): FloatArray {
    val writer = UniformWriter(if (usePbrFactors) MaterialUniformLayouts.Lit else MaterialUniformLayouts.Primary)
        .put(mvp.data, UniformFields.Mvp)
        .put(lightPayload, UniformFields.LightDirection, UniformFields.LightColor)
    if (usePbrFactors) {
        writer.putPbrFactors(extraUniformFloats)
    }
    return writer.build()
}

private fun UniformWriter.putPbrFactors(values: FloatArray): UniformWriter {
    val layout = MaterialUniformLayouts.PbrMaterial
    if (values.size >= layout.total) {
        return put(values, layout.offsetOf(UniformFields.PbrFactors), UniformFields.PbrFactors)
    }
    return put(UniformFields.PbrFactors, DEFAULT_METALLIC, DEFAULT_ROUGHNESS, 0f, 0f)
}

private fun UniformWriter.putPbrTexturedFactors(values: FloatArray, alphaCutoff: Float): UniformWriter {
    val layout = MaterialUniformLayouts.PbrTexturedMaterial
    if (values.size >= layout.total) {
        // The packet's third lane is the authoritative alpha cutoff for masked depth passes.
        val copy = values.copyOf(layout.total)
        val factors = layout.readVec4(copy, UniformFields.PbrFactors)
        layout.writeVec4(
            destination = copy,
            field = UniformFields.PbrFactors,
            x = factors.x,
            y = factors.y,
            z = alphaCutoff,
            w = factors.w,
        )
        put(copy, layout.offsetOf(UniformFields.PbrFactors), UniformFields.PbrFactors)
        put(copy, layout.offsetOf(UniformFields.BaseColorFactor), UniformFields.BaseColorFactor)
        put(copy, layout.offsetOf(UniformFields.EmissiveFactor), UniformFields.EmissiveFactor)
        return this
    }
    put(UniformFields.PbrFactors, DEFAULT_METALLIC_FACTOR, DEFAULT_ROUGHNESS_FACTOR, alphaCutoff, 0f)
    put(UniformFields.BaseColorFactor, DEFAULT_BASE_COLOR_FACTOR)
    put(UniformFields.EmissiveFactor, DEFAULT_EMISSIVE_FACTOR)
    return this
}

/**
 * The complete `lit_shadow.wgsl` uniform block for one draw.
 *
 * The lit path's counterpart to [texturedUniforms], and here for the same reason: it was written
 * inline in Vulkan's own draw preparation, so the backend knew an authored shader's block field
 * by field. A field reordered in the shader and not here produces a correctly-sized buffer full
 * of the wrong numbers, which renders without erroring -- that is exactly how this shader's
 * shadows broke once already.
 *
 * Field order is checked against the layout by [UniformWriter] rather than trusted to a comment.
 *
 * @param drawCall The draw whose model matrix and material factors this writes.
 * @param mvp This draw's model-view-projection, already combined by the caller.
 * @param cascades This frame's shadow cascades -- the matrices a fragment projects into to
 * sample the shadow map, and the distances that decide which one it uses. Per frame, not per
 * draw: the same set goes into every draw's block.
 * @param frame This frame's lights, eye position and fog.
 * @return The complete uniform block, sized exactly [MaterialUniformLayouts.LitShadow].
 */
fun litShadowUniforms(
    drawCall: RenderDrawCommand,
    mvp: Mat4,
    cascades: ShadowCascadeUniforms,
    frame: SceneFrameUniforms,
): FloatArray = UniformWriter(MaterialUniformLayouts.LitShadow)
    .put(mvp.data, UniformFields.Mvp)
    .let(frame.light::writeTo)
    .put(cascades.matrixFloats(), UniformFields.CascadeViewProjections)
    .put(cascades.depthScaleFloats(), UniformFields.CascadeDepthScales)
    .put(drawCall.model.data, UniformFields.Model)
    .put(UniformFields.VertexAnimation, drawCall.vertexAnimation, drawCall.timeSeconds)
    .put(cameraPositionFloats(frame.cameraEye), UniformFields.CameraPosition)
    .put(pbrMaterialFloats(drawCall), UniformFields.Material)
    .put(frame.fog, UniformFields.FogColor)
    .build()
