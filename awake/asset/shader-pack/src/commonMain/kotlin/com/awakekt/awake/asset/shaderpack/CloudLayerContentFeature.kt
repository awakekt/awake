/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant

private const val VERTEX_BINDING = 0
private const val DEFAULT_CLOUD_AMBIENT = 0.40f

/**
 * Single-pass procedural cloud layer drawn after opaque geometry on any [VertexFormat.PositionNormalColorUv] mesh.
 *
 * Replaces stacked multi-mesh cloud sheets with a single draw call (halving transparent overdraw), uses zero
 * textures, and switches between [CloudLayerQuality.Fast] and [CloudLayerQuality.Standard] uniformly via [look].
 */
fun cloudLayerContentFeature(
    geometry: MeshGeometry,
    look: CloudLayerLook = CloudLayerLook(),
    name: String = "cloud_layer",
    shaders: ShaderSet = PackShaderSets.CloudLayer,
): ContentFeatureSource = ContentFeatureSource { backend ->
    val fastSpec = shaders.solidOpaque?.stagesFor(backend)?.spec(
        vertexFormat = VertexFormat.PositionNormalColorUv,
        variant = PipelineVariant.AlphaBlended,
        uniforms = CloudLayerUniformLayout,
    )
    ContentFeature(
        name = name,
        spec = shaders.stagesFor(backend).spec(
            vertexFormat = VertexFormat.PositionNormalColorUv,
            variant = PipelineVariant.AlphaBlended,
            uniforms = CloudLayerUniformLayout,
        ),
        geometry = geometry,
        paint = ContentPaint.BeforeTransparent,
        images = emptyMap(),
        alternateSpec = fastSpec,
        buildWithAlternate = { pipeline, fastPipeline, uniforms, uploaded ->
            CloudLayerRenderFeature(
                pipeline = pipeline,
                uniforms = uniforms,
                geometry = requireNotNull(uploaded) { "Cloud layer declares geometry, so the engine must have uploaded it." },
                look = look,
                fastPipeline = fastPipeline,
            )
        },
    ) { pipeline, uniforms, uploaded ->
        CloudLayerRenderFeature(
            pipeline = pipeline,
            uniforms = uniforms,
            geometry = requireNotNull(uploaded) { "Cloud layer declares geometry, so the engine must have uploaded it." },
            look = look,
        )
    }
}

/** Records a single-pass procedural cloud layer over uploaded [ContentGeometry]. */
class CloudLayerRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val geometry: ContentGeometry,
    private val look: CloudLayerLook,
    private val fastPipeline: PipelineHandle? = null,
) : RenderFeature<RenderFrameContext> {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        pipeline: PipelineHandle,
        uniforms: UniformBlock,
        geometry: ContentGeometry,
        look: CloudLayerLook,
    ) : this(pipeline, uniforms, geometry, look, fastPipeline = null)

    override val pass = RenderPassSlot.Scene

    private val sun = Vec3f()

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!look.enabled || environment.debugView != GpuDebugView.Off) return
        writeSunDirection(sun)
        val lightOverride = look.lightColorOverride
        val lightR = lightOverride?.r ?: light.color.x
        val lightG = lightOverride?.g ?: light.color.y
        val lightB = lightOverride?.b ?: light.color.z
        val ambient = look.ambientOverride ?: light.ambient ?: DEFAULT_CLOUD_AMBIENT
        val env = environment

        uniforms.write(frameIndex) {
            put(CloudLayerFields.ViewProjection, viewProjection)
            put(CloudLayerFields.CameraEye, cameraEye, look.timeSeconds)
            put(CloudLayerFields.LightDirection, sun, look.quality.code.toFloat())
            put(CloudLayerFields.LightColor, lightR, lightG, lightB, ambient)
            put(CloudLayerFields.HorizonColor, env.horizonColor.r, env.horizonColor.g, env.horizonColor.b, env.exposure)
            put(CloudLayerFields.FogColor, env.fogColor.r, env.fogColor.g, env.fogColor.b, env.fogDensity)
            put(CloudLayerFields.FogAndFade, env.fogStart, env.fogEnd, look.fadeStart, look.fadeEnd)
            put(CloudLayerFields.CloudColor, look.cloudColor.r, look.cloudColor.g, look.cloudColor.b, look.scale)
            put(CloudLayerFields.ShadowColor, look.shadowColor.r, look.shadowColor.g, look.shadowColor.b, look.opacity)
            put(CloudLayerFields.CloudParams, look.driftSpeedX, look.driftSpeedZ, look.coverage, look.softness)
            put(CloudLayerFields.Origin, look.origin, 0f)
        }

        val activePipeline = if (look.quality == CloudLayerQuality.Fast && fastPipeline != null) fastPipeline else pipeline
        recorder.bindPipeline(activePipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(frameIndex))
        recorder.bindVertexBuffer(VERTEX_BINDING, geometry.vertexBuffer)
        val indices = geometry.indexBuffer
        if (indices != null) {
            recorder.bindIndexBuffer(indices)
            recorder.drawIndexed(geometry.elementCount)
        } else {
            recorder.draw(geometry.elementCount, 1)
        }
    }

    override fun destroy() = Unit
}
