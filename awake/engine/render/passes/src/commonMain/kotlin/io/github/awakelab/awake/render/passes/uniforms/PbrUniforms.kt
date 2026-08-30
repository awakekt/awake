/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes.uniforms

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.UniformFields
import io.github.awakelab.awake.render.renderer.UniformWriter

/** No metal, half-rough -- a plain lit surface when a `DrawCall` supplies no PBR factors. */
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
fun pbrMaterialFloats(drawCall: DrawCall): FloatArray {
    val supplied = drawCall.extraUniformFloats
    if (supplied.size >= PBR_MATERIAL_FLOATS) return supplied.copyOf(PBR_MATERIAL_FLOATS)
    return floatArrayOf(DEFAULT_METALLIC, DEFAULT_ROUGHNESS, 0f, 0f)
}

/**
 * Packs `[metallic, roughness, pad, pad] + baseColorFactor.rgba + emissiveFactor.rgba` for the
 * textured glTF PBR path.
 *
 * @param drawCall Supplies the factors through `extraUniformFloats`, or nothing for the defaults.
 * @return Exactly [PBR_TEXTURED_MATERIAL_FLOATS] floats.
 */
fun pbrTexturedMaterialFloats(drawCall: DrawCall): FloatArray {
    val supplied = drawCall.extraUniformFloats
    if (supplied.size >= PBR_TEXTURED_MATERIAL_FLOATS) return supplied.copyOf(PBR_TEXTURED_MATERIAL_FLOATS)
    return floatArrayOf(DEFAULT_METALLIC_FACTOR, DEFAULT_ROUGHNESS_FACTOR, 0f, 0f) +
        DEFAULT_BASE_COLOR_FACTOR.toFloatArray() +
        DEFAULT_EMISSIVE_FACTOR.toFloatArray()
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
    drawCall: DrawCall,
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
 * @param lightMvp This draw's model combined with the light's own view-projection -- what the
 * fragment shader projects into to sample the depth target. The light supplies that matrix; see
 * [SceneLight.viewProjection].
 * @param frame This frame's lights, eye position and fog.
 * @return The complete uniform block, sized exactly [MaterialUniformLayouts.LitShadow].
 */
fun litShadowUniforms(
    drawCall: DrawCall,
    mvp: Mat4,
    lightMvp: Mat4,
    frame: SceneFrameUniforms,
): FloatArray = UniformWriter(MaterialUniformLayouts.LitShadow)
    .put(mvp.data, UniformFields.Mvp)
    .let(frame.light::writeTo)
    .put(lightMvp.data, UniformFields.LightMvp)
    .put(UniformFields.VertexAnimation, drawCall.vertexAnimation, drawCall.timeSeconds)
    .put(drawCall.model.data, UniformFields.Model)
    .put(cameraPositionFloats(frame.cameraEye), UniformFields.CameraPosition)
    .put(pbrMaterialFloats(drawCall), UniformFields.Material)
    .put(frame.fog, UniformFields.FogColor)
    .build()
