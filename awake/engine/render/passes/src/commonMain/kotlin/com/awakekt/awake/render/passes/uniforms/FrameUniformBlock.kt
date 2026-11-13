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
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
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
            .put(UniformFields.Exposure, exposure, 0f, 0f, 0f)
            .build()
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
 * A frame block whose view-projection is the identity and whose every other field is zero.
 *
 * What the shared camera-depth shader reads beside a draw whose single block still starts with
 * its MVP, as [com.awakekt.awake.render.renderer.UniformFields.DefaultMaterial] does: that shader
 * multiplies the frame's view-projection by the matrix at the start of the draw's block, which is
 * a split draw's model and such a draw's whole MVP.
 */
fun identityFrameBlock(): FloatArray = FloatArray(MaterialUniformLayouts.Frame.total).also {
    Mat4().data.copyInto(it, MaterialUniformLayouts.Frame.offsetOf(UniformFields.ViewProjection))
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
 * [drawBlockFloats]: [MaterialUniformLayouts.PbrTexturedDraw], or [MaterialUniformLayouts.LitShadowDraw]
 * or its leading fields.
 *
 * Chosen by what the pipeline's shader declares, not by the material's size: the block is the
 * shader's. Nothing here reads the camera, the light or the cascades, so a draw that holds still
 * packs the same block while they move. An [instanced] draw's copies carry their own transforms,
 * so its block's model is the identity.
 */
fun RenderDrawCommand.drawBlockFloats(drawBlockFloats: Int, alphaToCoverageEnabled: Boolean, instanced: Boolean = false): FloatArray {
    val transform = if (instanced) IDENTITY else model
    val fade = if (fadeOpacity < 1f) 1f - fadeOpacity.coerceIn(0f, 1f) else 0f
    return when (drawBlockFloats) {
        MaterialUniformLayouts.PbrTexturedDraw.total -> UniformWriter(MaterialUniformLayouts.PbrTexturedDraw)
            .put(transform.data, UniformFields.Model)
            .put(UniformFields.VertexAnimation, 0f, 0f, 0f, 0f)
            .put(
                texturedMaterialPayload(extraUniformFloats, sceneCoverageCutoff(alphaToCoverageEnabled), blendsAdditively, timeSeconds),
                UniformFields.PbrFactors,
                UniformFields.BaseColorFactor,
                UniformFields.EmissiveFactor,
                UniformFields.TextureFrames,
                UniformFields.TextureScroll,
            )
            .put(UniformFields.DrawFade, fade, 0f, 0f, 0f)
            .build()
        // A shader that declares only the leading fields, as a depth shader does, reads them from this one.
        in 1..MaterialUniformLayouts.LitShadowDraw.total -> UniformWriter(MaterialUniformLayouts.LitShadowDraw)
            .put(transform.data, UniformFields.Model)
            .put(UniformFields.VertexAnimation, vertexAnimation, vertexAnimationClock(vertexAnimation, timeSeconds))
            .put(pbrMaterialFloats(extraUniformFloats), UniformFields.Material)
            .put(UniformFields.DrawFade, fade, 0f, 0f, 0f)
            .build()
        else -> error("No draw block holds $drawBlockFloats floats beside the frame block.")
    }
}

/**
 * The draw block this draw's plan implies, for a pipeline that does not carry what its shader
 * binds: [MaterialUniformLayouts.LitShadowDraw]'s or `PbrTexturedDraw`'s floats, or 0 when the
 * plan's shader keeps one whole block. A pipeline that knows its shader's bindings goes by those.
 * [instancedKind] is null for a single draw.
 */
fun RenderDrawCommand.planDrawBlockFloats(
    materialUniformFloatCount: Int,
    instancedKind: InstancedDrawKind? = null,
    hasShadowCascades: Boolean = false,
): Int {
    if (instancedKind == InstancedDrawKind.Particle) return 0
    val plan = drawUniformPlan(mesh.format, materialUniformFloatCount, hasShadowCascades)
    return when {
        plan == DrawUniformPlan.TexturedPbr && instancedKind != InstancedDrawKind.Skinned -> MaterialUniformLayouts.PbrTexturedDraw.total
        instancedKind != null ->
            if (hasShadowCascades || materialUniformFloatCount >= MaterialUniformLayouts.LitShadow.total) MaterialUniformLayouts.LitShadowDraw.total else 0
        plan == DrawUniformPlan.LitShadow -> MaterialUniformLayouts.LitShadowDraw.total
        else -> 0
    }
}

private val IDENTITY = Mat4()
private val SUN_FLOATS = UniformFields.LightDirection.floats + UniformFields.LightColor.floats
private val POINTS_AT = MaterialUniformLayouts.SceneLight.offsetOf(UniformFields.PointLightPositions)
private val NO_LIGHTS = FloatArray(0)
