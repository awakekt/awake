/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.AlphaMode
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

/**
 * How a textured material's texture moves: a frame sheet played in reading order (left to right,
 * then top to bottom of the image), and a UV scroll. [None] is a still texture.
 *
 * @property columns Frame-sheet columns.
 * @property rows Frame-sheet rows.
 * @property frameCount Frames played, from the first; at most [columns] x [rows].
 * @property framesPerSecond Playback rate; 0 holds the first frame.
 * @property scrollU UV units per second along U.
 * @property scrollV UV units per second along V, toward the bottom of the image.
 */
data class TextureAnimation(
    val columns: Int = 1,
    val rows: Int = 1,
    val frameCount: Int = columns * rows,
    val framesPerSecond: Float = 0f,
    val scrollU: Float = 0f,
    val scrollV: Float = 0f,
) {
    init {
        require(columns >= 1 && rows >= 1) { "A frame sheet needs at least one column and row: ${columns}x$rows." }
        require(frameCount in 1..columns * rows) { "$frameCount frames do not fit a ${columns}x$rows sheet." }
        require(framesPerSecond >= 0f && framesPerSecond.isFinite()) { "framesPerSecond must be finite and >= 0." }
        require(scrollU.isFinite() && scrollV.isFinite()) { "The UV scroll must be finite." }
    }

    companion object {
        /** A still texture. */
        val None: TextureAnimation = TextureAnimation()
    }
}

/** Packs authored PBR factors without making scene extraction know the GPU lane order. */
fun pbrMaterialFloats(
    metallic: Float,
    roughness: Float,
    baseColorFactor: Color,
    emissiveFactor: Color,
    textureAnimation: TextureAnimation = TextureAnimation.None,
): FloatArray = UniformWriter(MaterialUniformLayouts.PbrTexturedMaterial)
    .put(UniformFields.PbrFactors, metallic, roughness, 0f, 0f)
    .put(UniformFields.BaseColorFactor, baseColorFactor)
    .put(UniformFields.EmissiveFactor, emissiveFactor.r, emissiveFactor.g, emissiveFactor.b, 0f)
    .putTextureAnimation(textureAnimation)
    .build()

private fun UniformWriter.putTextureAnimation(animation: TextureAnimation): UniformWriter = this
    .put(
        UniformFields.TextureFrames,
        animation.columns.toFloat(),
        animation.rows.toFloat(),
        animation.framesPerSecond,
        animation.frameCount.toFloat(),
    )
    .put(UniformFields.TextureScroll, animation.scrollU, animation.scrollV, 0f, 0f)

/** Same typed defaulting for backend source adapters that carry only the raw extra payload. */
internal fun pbrMaterialFloats(values: FloatArray): FloatArray = pbrMaterialPayload(values)

/**
 * Packs `[metallic, roughness, alphaCutoff, pad] + baseColorFactor.rgba + emissiveFactor.rgba` and
 * the texture animation for the textured glTF PBR path.
 *
 * @param drawCall Supplies the factors through `extraUniformFloats`, or nothing for the defaults.
 * The third float in the metallic/roughness vec4 is the draw's alpha cutoff, which masked depth
 * shaders read; the draw's time goes in the texture scroll's `z`, which the animation reads.
 *
 * @return Exactly [PBR_TEXTURED_MATERIAL_FLOATS] floats.
 */
fun pbrTexturedMaterialFloats(drawCall: RenderDrawCommand): FloatArray =
    texturedMaterialPayload(drawCall.extraUniformFloats, drawCall.coverageCutoff, drawCall.timeSeconds)

/**
 * The alpha textured shaders discard below: the draw's cutoff when its material is masked, and 0
 * otherwise, so an opaque material's texture alpha, which often means nothing, cuts nothing.
 */
val RenderDrawCommand.coverageCutoff: Float
    get() = if (alphaMode == AlphaMode.Masked) alphaCutoff else 0f

/**
 * The textured material block for [values], stamped with the draw's [alphaCutoff] and
 * [timeSeconds]. A payload of only the three factor fields (from before texture animation) keeps
 * its factors and plays no animation.
 */
private fun texturedMaterialPayload(values: FloatArray, alphaCutoff: Float, timeSeconds: Float): FloatArray {
    val layout = MaterialUniformLayouts.PbrTexturedMaterial
    val output = when {
        values.size >= layout.total -> values.copyOf(layout.total)
        values.size >= FACTOR_FLOATS -> UniformWriter(layout)
            .put(values, layout.offsetOf(UniformFields.PbrFactors), UniformFields.PbrFactors)
            .put(values, layout.offsetOf(UniformFields.BaseColorFactor), UniformFields.BaseColorFactor)
            .put(values, layout.offsetOf(UniformFields.EmissiveFactor), UniformFields.EmissiveFactor)
            .putTextureAnimation(TextureAnimation.None)
            .build()
        else -> UniformWriter(layout)
            .put(UniformFields.PbrFactors, DEFAULT_METALLIC_FACTOR, DEFAULT_ROUGHNESS_FACTOR, 0f, 0f)
            .put(UniformFields.BaseColorFactor, DEFAULT_BASE_COLOR_FACTOR)
            .put(UniformFields.EmissiveFactor, DEFAULT_EMISSIVE_FACTOR)
            .putTextureAnimation(TextureAnimation.None)
            .build()
    }
    val factors = layout.readVec4(output, UniformFields.PbrFactors)
    layout.writeVec4(output, UniformFields.PbrFactors, factors.x, factors.y, alphaCutoff, factors.w)
    val scroll = layout.readVec4(output, UniformFields.TextureScroll)
    layout.writeVec4(output, UniformFields.TextureScroll, scroll.x, scroll.y, timeSeconds, scroll.w)
    return output
}

/** The factor fields a textured payload carried before texture animation. */
private val FACTOR_FLOATS =
    UniformFields.PbrFactors.floats + UniformFields.BaseColorFactor.floats + UniformFields.EmissiveFactor.floats

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
    val cameraForward: Vec3f,
) {
    /** Retains the original constructor for callers without explicit camera-forward metadata. */
    constructor(light: SceneLightUniforms, cameraEye: Vec3f, fog: FloatArray) :
        this(light, cameraEye, fog, Vec3f(0f, 0f, -1f))
}

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
    .putCascades(GpuShadowCascadeData.UNSHADOWED)
    .putStillModel(drawCall.model)
    .put(cameraPositionFloats(frame.cameraEye), UniformFields.CameraPosition)
    .put(
        pbrTexturedMaterialFloats(drawCall),
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
        UniformFields.TextureFrames,
        UniformFields.TextureScroll,
    )
    .put(frame.fog, UniformFields.FogColor)
    .putDebugView(GpuDebugView.Off, frame.cameraForward)
    .putCameraForward(frame.cameraForward, GpuShadowCascadeData.UNSHADOWED)
    .build()

/** Packs the unshadowed textured PBR block from the backend-neutral draw payload. The light
 * payload is the frame block produced by [sceneLightUniforms]: directional lanes followed by
 * point-light position and colour slots. Keeping this here prevents Vulkan and WebGPU from
 * independently assembling a shorter, invalid prefix of the textured layout. */
@Suppress("LongParameterList") // One argument per draw input the block holds; DrawUniformPacking names each.
fun texturedUniforms(
    mvp: Mat4,
    model: Mat4,
    lightPayload: FloatArray,
    extraUniformFloats: FloatArray,
    cameraEye: Vec3f,
    fogColor: Color,
    fogDensity: Float,
    alphaCutoff: Float = 0f,
    debugView: GpuDebugView = GpuDebugView.Off,
    cameraForward: Vec3f = Vec3f(0f, 0f, -1f),
    timeSeconds: Float = 0f,
    shadowCascades: GpuShadowCascadeData = GpuShadowCascadeData.UNSHADOWED,
): FloatArray = UniformWriter(MaterialUniformLayouts.PbrTextured)
    .put(mvp.data, UniformFields.Mvp)
    .put(
        lightPayload,
        UniformFields.LightDirection,
        UniformFields.LightColor,
        UniformFields.PointLightPositions,
        UniformFields.PointLightColors,
    )
    .putCascades(shadowCascades)
    .putStillModel(model)
    .put(UniformFields.CameraPosition, cameraEye)
    .put(
        texturedMaterialPayload(extraUniformFloats, alphaCutoff, timeSeconds),
        UniformFields.PbrFactors,
        UniformFields.BaseColorFactor,
        UniformFields.EmissiveFactor,
        UniformFields.TextureFrames,
        UniformFields.TextureScroll,
    )
    .put(UniformFields.FogColor, fogColor.r, fogColor.g, fogColor.b, fogDensity)
    .putDebugView(debugView, cameraForward)
    .putCameraForward(cameraForward, shadowCascades)
    .build()

/** The sun's cascades. */
private fun UniformWriter.putCascades(cascades: GpuShadowCascadeData): UniformWriter = this
    .put(cascades.matrixFloats(), UniformFields.CascadeViewProjections)
    .put(cascades.depthScaleFloats(), UniformFields.CascadeDepthScales)

/** [model] with no vertex animation: the textured shader draws the mesh still, so its shadow is still too. */
private fun UniformWriter.putStillModel(model: Mat4): UniformWriter = this
    .put(model.data, UniformFields.Model)
    .put(UniformFields.VertexAnimation, Vec3f.ZERO, 0f)

/** `cameraForward.w` is the cascade count, 0 when nothing casts. */
private fun UniformWriter.putCameraForward(cameraForward: Vec3f, cascades: GpuShadowCascadeData): UniformWriter =
    put(UniformFields.CameraForward, cameraForward, cascades.count.toFloat())

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
    .put(UniformFields.CameraForward, frame.cameraForward, cascades.count.toFloat())
    .put(pbrMaterialFloats(drawCall), UniformFields.Material)
    .put(frame.fog, UniformFields.FogColor)
    .putDebugView(GpuDebugView.Off, frame.cameraForward)
    .build()
