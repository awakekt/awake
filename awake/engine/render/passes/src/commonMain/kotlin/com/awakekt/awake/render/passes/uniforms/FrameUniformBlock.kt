/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.SkinnedDrawLayout
import com.awakekt.awake.render.renderer.SkinnedFields
import com.awakekt.awake.render.renderer.SkinnedMaterialLayout
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * Packs [MaterialUniformLayouts.Frame], the block a pass writes once for every lit draw in it.
 *
 * One per renderer, kept: [pack] refills the same array, so a frame allocates nothing here. The
 * array is the caller's until the next [pack].
 */
class FrameUniformBlock {
    private val writer = UniformWriter(MaterialUniformLayouts.Frame)
    private val cascadeMatrices = FloatArray(UniformFields.CascadeViewProjections.floats)
    private val cascadeScales = FloatArray(UniformFields.CascadeDepthScales.floats)

    /**
     * The frame block for a pass seen through [viewProjection] from [cameraEye].
     *
     * [lightPayload] is the directional light's eight floats, or the complete
     * [MaterialUniformLayouts.SceneLight] payload; point-light slots a short payload leaves out are
     * off. [shadowCascades] is null when nothing in the pass receives a shadow.
     */
    @Suppress("LongParameterList") // One argument per input of the block.
    fun pack(
        viewProjection: Mat4,
        cameraEye: Vec3f,
        cameraForward: Vec3f,
        lightPayload: FloatArray,
        shadowCascades: GpuShadowCascadeData?,
        fogColor: Color,
        fogDensity: Float,
        debugView: GpuDebugView,
        exposure: Float,
        fogStart: Float = 0f,
        fogEnd: Float = 0f,
    ): FloatArray {
        val cascades = shadowCascades ?: GpuShadowCascadeData.UNSHADOWED
        fillCascades(cascades)
        return writer.reset()
            .put(viewProjection.data, UniformFields.ViewProjection)
            .putLights(lightPayload)
            .put(cascadeMatrices, UniformFields.CascadeViewProjections)
            .put(cascadeScales, UniformFields.CascadeDepthScales)
            .put(UniformFields.CameraPosition, cameraEye)
            .put(UniformFields.CameraForward, cameraForward, cascades.countAndFade)
            .put(UniformFields.FogColor, fogColor.r, fogColor.g, fogColor.b, fogDensity)
            .putDebugView(debugView, cameraForward)
            .put(UniformFields.Exposure, exposure, debugView.layer.toFloat(), 0f, 0f)
            .putFogRange(fogStart, fogEnd)
            .build()
    }

    /** [pack] as released before `fogStart` and `fogEnd`, kept so compiled callers still link. */
    @Deprecated("Use pack with fogStart and fogEnd.", level = DeprecationLevel.HIDDEN)
    @Suppress("LongParameterList")
    fun pack(
        viewProjection: Mat4,
        cameraEye: Vec3f,
        cameraForward: Vec3f,
        lightPayload: FloatArray,
        shadowCascades: GpuShadowCascadeData?,
        fogColor: Color,
        fogDensity: Float,
        debugView: GpuDebugView,
        exposure: Float,
    ): FloatArray = pack(viewProjection, cameraEye, cameraForward, lightPayload, shadowCascades, fogColor, fogDensity, debugView, exposure, 0f, 0f)

    // Zeros for exponential fog, which the shaders then fog by density as before; z = 1 is linear.
    private fun UniformWriter.putFogRange(start: Float, end: Float): UniformWriter =
        if (end > start) put(UniformFields.FogRange, start, end, 1f, 0f) else put(UniformFields.FogRange, 0f, 0f, 0f, 0f)

    /** Packs [input]'s frame block, supplying default forward/light values for pass-only or offscreen inputs. */
    fun pack(input: GpuPassInput): FloatArray {
        val environment = input.environment
        // The engine's own (resolved) input says which cascades it drew, or none. Only an input
        // built by hand is read by layer, the cascades first: on the resolved path a casting point
        // light's faces follow the cascades drawn, from layer 0 with none (#1709), so a layer says
        // nothing there about what a pass is.
        val cascades = input.shadowCascadeData ?: input.prePasses
            .takeUnless { input.resolvedPath }
            .orEmpty()
            .filter { it.targetLayer < MAX_SHADOW_CASCADES }
            .takeIf { it.isNotEmpty() }
            ?.let { subs -> GpuShadowCascadeData(subs.map { it.viewProjection }, FloatArray(subs.size) { Float.MAX_VALUE }) }
        return pack(
            viewProjection = input.viewProjection,
            cameraEye = input.cameraEye,
            cameraForward = input.cameraForward ?: DEFAULT_FORWARD,
            lightPayload = if (input.passUniforms.isEmpty()) DEFAULT_LIGHT_PAYLOAD else input.passUniforms,
            shadowCascades = cascades,
            fogColor = environment.fogColor,
            fogDensity = environment.fogDensity,
            debugView = environment.debugView,
            exposure = environment.exposure,
            fogStart = environment.fogStart,
            fogEnd = environment.fogEnd,
        )
    }

    /** As [GpuShadowCascadeData.matrixFloats] and `depthScaleFloats` fill them, into kept arrays. */
    private fun fillCascades(cascades: GpuShadowCascadeData) {
        for (index in 0 until MAX_SHADOW_CASCADES) {
            val cascade = minOf(index, cascades.count - 1)
            UniformFields.CascadeViewProjections.writeMat4Element(cascadeMatrices, index, cascades.viewProjections[cascade])
            UniformFields.CascadeDepthScales.writeVec4Element(
                destination = cascadeScales,
                index = index,
                x = cascades.depthScales[cascade],
                y = cascades.worldExtents[cascade],
                z = cascades.splitDistances[cascade],
                w = cascades.blendStartDistances[cascade],
            )
        }
    }
}

/**
 * A frame block whose view-projection is the identity, whose directional light is the default sun,
 * and whose every other field is zero.
 *
 * What the shared camera-depth shader reads beside a draw whose single block still starts with
 * its MVP, as [com.awakekt.awake.render.renderer.UniformFields.DefaultMaterial] does: that shader
 * multiplies the frame's view-projection by the matrix at the start of the draw's block, which is
 * a split draw's model and such a draw's whole MVP.
 */
fun identityFrameBlock(): FloatArray = FloatArray(MaterialUniformLayouts.Frame.total).also {
    Mat4().data.copyInto(it, MaterialUniformLayouts.Frame.offsetOf(UniformFields.ViewProjection))
    DEFAULT_LIGHT_PAYLOAD.copyInto(
        destination = it,
        destinationOffset = MaterialUniformLayouts.Frame.offsetOf(UniformFields.LightDirection),
        startIndex = 0,
        endIndex = SUN_FLOATS,
    )
}

/** The sun, then the point-light slots; a payload without either leaves it dark. */
private fun UniformWriter.putLights(lightPayload: FloatArray): UniformWriter {
    val sun = if (lightPayload.size >= SUN_FLOATS) {
        put(lightPayload, UniformFields.LightDirection, UniformFields.LightColor)
    } else {
        put(UniformFields.LightDirection, 0f, 0f, 0f, 0f).put(UniformFields.LightColor, 0f, 0f, 0f, 0f)
    }
    return if (lightPayload.size >= MaterialUniformLayouts.SceneLight.total) {
        sun.put(lightPayload, POINTS_AT, UniformFields.PointLightPositions, UniformFields.PointLightColors)
    } else {
        sun.putPadded(UniformFields.PointLightPositions, NO_LIGHTS).putPadded(UniformFields.PointLightColors, NO_LIGHTS)
    }
}

/**
 * This draw's own block beside the frame's, for a pipeline whose draw block holds
 * [drawBlockFloats].
 *
 * Chosen by what the pipeline's shader declares, not by the material's size: the block is the
 * shader's. Nothing here reads the camera, the light or the cascades, so a draw that holds still
 * packs the same block while they move. An [instanced] draw's copies carry their own transforms,
 * so its block's model is the identity.
 */
fun RenderDrawCommand.drawBlockFloats(drawBlockFloats: Int, alphaToCoverageEnabled: Boolean, instanced: Boolean = false): FloatArray =
    drawBlockFloats(
        drawBlockFloats = drawBlockFloats,
        alphaToCoverageEnabled = alphaToCoverageEnabled,
        instancedKind = if (instanced) InstancedDrawKind.Plain else null,
    )

/**
 * This draw's own block beside the frame's, disambiguating particle and skinned instanced batches
 * via [instancedKind].
 */
fun RenderDrawCommand.drawBlockFloats(
    drawBlockFloats: Int,
    alphaToCoverageEnabled: Boolean,
    instancedKind: InstancedDrawKind?,
): FloatArray = packDrawBlock(drawBlockFloats, alphaToCoverageEnabled, instancedKind, writers = null)

/**
 * This draw's own block beside the frame's, packed into [writers]' arrays rather than new ones: the
 * same floats as the overload without them, allocating nothing per draw. The array returned is
 * [writers]' and is overwritten by the next pack with the same layout; see [DrawBlockWriters].
 */
fun RenderDrawCommand.drawBlockFloats(
    drawBlockFloats: Int,
    alphaToCoverageEnabled: Boolean,
    instancedKind: InstancedDrawKind?,
    writers: DrawBlockWriters,
): FloatArray = packDrawBlock(drawBlockFloats, alphaToCoverageEnabled, instancedKind, writers)

private fun RenderDrawCommand.packDrawBlock(
    drawBlockFloats: Int,
    alphaToCoverageEnabled: Boolean,
    instancedKind: InstancedDrawKind?,
    writers: DrawBlockWriters?,
): FloatArray {
    if (instancedKind == InstancedDrawKind.Particle && drawBlockFloats in 1..ParticleDrawLayout.total) {
        return (writers?.particle?.reset() ?: UniformWriter(ParticleDrawLayout))
            .put(extraUniformFloats, *ParticleDrawLayout.fields)
            .build()
    }
    val fade = if (fadeOpacity < 1f) 1f - fadeOpacity.coerceIn(0f, 1f) else 0f
    return (if (instancedKind == null) singlePlanDrawBlockFloats(drawBlockFloats, alphaToCoverageEnabled, fade, writers) else null)
        ?: sharedLayoutDrawBlockFloats(drawBlockFloats, alphaToCoverageEnabled, instancedKind, fade, writers)
}

private fun RenderDrawCommand.sharedLayoutDrawBlockFloats(
    drawBlockFloats: Int,
    alphaToCoverageEnabled: Boolean,
    instancedKind: InstancedDrawKind?,
    fade: Float,
    writers: DrawBlockWriters?,
): FloatArray {
    val transform = if (instancedKind != null) IDENTITY else model
    return when {
        drawBlockFloats == MaterialUniformLayouts.PbrTexturedDraw.total ->
            (writers?.pbrTextured?.reset() ?: UniformWriter(MaterialUniformLayouts.PbrTexturedDraw))
                .put(transform.data, UniformFields.Model)
                .put(UniformFields.VertexAnimation, 0f, 0f, 0f, 0f)
                .put(
                    texturedMaterialPayload(
                        extraUniformFloats,
                        sceneCoverageCutoff(alphaToCoverageEnabled),
                        blendsAdditively,
                        timeSeconds,
                        into = writers?.texturedPayload,
                    ),
                    UniformFields.PbrFactors,
                    UniformFields.BaseColorFactor,
                    UniformFields.EmissiveFactor,
                    UniformFields.TextureFrames,
                    UniformFields.TextureScroll,
                )
                .put(UniformFields.DrawFade, fade, 0f, 0f, 0f)
                .build()
        drawBlockFloats == MaterialUniformLayouts.LitDraw.total ->
            (writers?.lit?.reset() ?: UniformWriter(MaterialUniformLayouts.LitDraw))
                .put(transform.data, UniformFields.Model)
                .put(UniformFields.VertexAnimation, vertexAnimation, vertexAnimationClock(vertexAnimation, timeSeconds))
                .put(UniformFields.DrawFade, fade, 0f, 0f, 0f)
                .build()
        // A shader that declares only the leading fields, as a depth shader does, reads them from this one.
        drawBlockFloats in 1..MaterialUniformLayouts.LitShadowDraw.total ->
            (writers?.litShadow?.reset() ?: UniformWriter(MaterialUniformLayouts.LitShadowDraw))
                .put(transform.data, UniformFields.Model)
                .put(UniformFields.VertexAnimation, vertexAnimation, vertexAnimationClock(vertexAnimation, timeSeconds))
                .put(pbrMaterialFloats(extraUniformFloats, into = writers?.pbrPayload), UniformFields.Material)
                .put(UniformFields.DrawFade, fade, 0f, 0f, 0f)
                .build()
        else -> error("No draw block holds $drawBlockFloats floats beside the frame block.")
    }
}

private fun RenderDrawCommand.singlePlanDrawBlockFloats(
    drawBlockFloats: Int,
    alphaToCoverageEnabled: Boolean,
    fade: Float,
    writers: DrawBlockWriters?,
): FloatArray? = when (drawUniformPlan(mesh.format, materialUniformFloatCount = drawBlockFloats, hasShadowCascades = false)) {
    DrawUniformPlan.Skinned -> if (drawBlockFloats in 1..SkinnedDrawLayout.total) {
        skinnedDrawBlockFloats(sceneCoverageCutoff(alphaToCoverageEnabled), fade, writers)
    } else {
        null
    }
    DrawUniformPlan.Sprite -> if (drawBlockFloats == SpriteDrawLayout.total) {
        (writers?.sprite?.reset() ?: UniformWriter(SpriteDrawLayout)).put(model.data, UniformFields.Model).put(extraUniformFloats, SpriteFields.UvTransform, SpriteFields.Tint).build()
    } else {
        null
    }
    DrawUniformPlan.WorldText -> if (drawBlockFloats == WorldTextDrawLayout.total) {
        (writers?.worldText?.reset() ?: UniformWriter(WorldTextDrawLayout)).put(model.data, UniformFields.Model).put(extraUniformFloats, WorldTextFields.Tint, WorldTextFields.Field).build()
    } else {
        null
    }
    DrawUniformPlan.Trail -> if (drawBlockFloats == TrailDrawLayout.total) {
        (writers?.trail?.reset() ?: UniformWriter(TrailDrawLayout)).put(model.data, UniformFields.Model).put(extraUniformFloats, TrailFields.Tint).build()
    } else {
        null
    }
    else -> null
}

private fun RenderDrawCommand.skinnedDrawBlockFloats(alphaCutoff: Float, fade: Float, writers: DrawBlockWriters?): FloatArray {
    val writer = writers?.skinned?.reset() ?: UniformWriter(SkinnedDrawLayout)
    val metallic: Float
    val roughness: Float
    val unlit: Float
    if (extraUniformFloats.size == SkinnedMaterialLayout.total) {
        val factors = SkinnedMaterialLayout.readVec4(extraUniformFloats, UniformFields.PbrFactors)
        writer
            .put(extraUniformFloats, 0, SkinnedFields.JointPalette)
            .put(model.data, UniformFields.Model)
            .put(extraUniformFloats, SkinnedMaterialLayout.offsetOf(UniformFields.BaseColorFactor), UniformFields.BaseColorFactor)
            .put(extraUniformFloats, SkinnedMaterialLayout.offsetOf(UniformFields.EmissiveFactor), UniformFields.EmissiveFactor)
        metallic = factors.x
        roughness = factors.y
        unlit = factors.w
    } else {
        writer
            .putPadded(SkinnedFields.JointPalette, extraUniformFloats)
            .put(model.data, UniformFields.Model)
            .put(UniformFields.BaseColorFactor, DEFAULT_BASE_COLOR_FACTOR)
            .put(UniformFields.EmissiveFactor, DEFAULT_EMISSIVE_FACTOR)
        metallic = DEFAULT_METALLIC_FACTOR
        roughness = 1f
        unlit = 0f
    }
    return writer
        .put(UniformFields.PbrFactors, metallic, roughness, alphaCutoff, unlit)
        .put(UniformFields.DrawFade, fade, 0f, 0f, 0f)
        .build()
}

/**
 * The draw block this draw's plan implies, for a pipeline that does not carry what its shader
 * binds, or 0 when the shader keeps one whole block. A pipeline that knows its shader's bindings
 * goes by those. [instancedKind] is null for a single draw.
 */
fun RenderDrawCommand.planDrawBlockFloats(
    materialUniformFloatCount: Int,
    instancedKind: InstancedDrawKind? = null,
    hasShadowCascades: Boolean = false,
): Int {
    if (instancedKind == InstancedDrawKind.Particle) return ParticleDrawLayout.total
    val plan = drawUniformPlan(mesh.format, materialUniformFloatCount, hasShadowCascades)
    return when {
        plan == DrawUniformPlan.TexturedPbr && instancedKind != InstancedDrawKind.Skinned -> MaterialUniformLayouts.PbrTexturedDraw.total
        instancedKind != null ->
            if (hasShadowCascades || materialUniformFloatCount >= MaterialUniformLayouts.LitShadow.total) {
                MaterialUniformLayouts.LitShadowDraw.total
            } else {
                MaterialUniformLayouts.InstancedDraw.total
            }
        else -> plan.singleDrawBlockFloats(mesh.format)
    }
}

private fun DrawUniformPlan.singleDrawBlockFloats(format: VertexFormat): Int = when (this) {
    DrawUniformPlan.Skinned -> SkinnedDrawLayout.total
    DrawUniformPlan.Sprite -> SpriteDrawLayout.total
    DrawUniformPlan.WorldText -> WorldTextDrawLayout.total
    DrawUniformPlan.Trail -> TrailDrawLayout.total
    DrawUniformPlan.LitShadow -> MaterialUniformLayouts.LitShadowDraw.total
    DrawUniformPlan.Lit -> if (format == VertexFormat.PositionNormalColor) MaterialUniformLayouts.LitDraw.total else 0
    DrawUniformPlan.TexturedPbr -> MaterialUniformLayouts.PbrTexturedDraw.total
}

private val IDENTITY = Mat4()
private val DEFAULT_FORWARD = Vec3f(0f, 0f, -1f)
private val DEFAULT_LIGHT_PAYLOAD = sceneLightFloats(DEFAULT_SCENE_LIGHT)
private val SUN_FLOATS = UniformFields.LightDirection.floats + UniformFields.LightColor.floats
private val POINTS_AT = MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightPositions)
private val NO_LIGHTS = FloatArray(0)
