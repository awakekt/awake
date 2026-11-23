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
private const val DEFAULT_WATER_AMBIENT = 0.35f

/**
 * Procedural stylized water drawn after opaque geometry on any [VertexFormat.PositionNormalColorUv] mesh.
 *
 * Uses zero textures and switches between [StylizedWaterQuality.Fast] and [StylizedWaterQuality.Standard]
 * uniformly via [look] with zero pipeline rebuilds or per-frame allocations.
 */
fun stylizedWaterContentFeature(
    geometry: MeshGeometry,
    look: StylizedWaterLook = StylizedWaterLook(),
    name: String = "stylized_water",
    shaders: ShaderSet = PackShaderSets.StylizedWater,
): ContentFeatureSource = ContentFeatureSource { backend ->
    ContentFeature(
        name = name,
        spec = shaders.stagesFor(backend).spec(
            vertexFormat = VertexFormat.PositionNormalColorUv,
            variant = PipelineVariant.AlphaBlended,
            uniforms = StylizedWaterUniformLayout,
        ),
        geometry = geometry,
        paint = ContentPaint.AfterGeometry,
    ) { pipeline, uniforms, uploaded ->
        StylizedWaterRenderFeature(
            pipeline = pipeline,
            uniforms = uniforms,
            geometry = requireNotNull(uploaded) { "Stylized water declares geometry, so the engine must have uploaded it." },
            look = look,
        )
    }
}

/** Records a stylized water surface pass over uploaded [ContentGeometry]. */
class StylizedWaterRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val geometry: ContentGeometry,
    private val look: StylizedWaterLook,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val sun = Vec3f()

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!look.enabled || environment.debugView != GpuDebugView.Off) return
        writeSunDirection(sun)
        val lightOverride = look.lightColorOverride
        val lightR = lightOverride?.r ?: light.color.x
        val lightG = lightOverride?.g ?: light.color.y
        val lightB = lightOverride?.b ?: light.color.z
        val ambient = look.ambientOverride ?: light.ambient ?: DEFAULT_WATER_AMBIENT
        val env = environment

        uniforms.write(frameIndex) {
            put(StylizedWaterFields.ViewProjection, viewProjection)
            put(StylizedWaterFields.CameraEye, cameraEye, look.timeSeconds)
            put(StylizedWaterFields.LightDirection, sun, look.quality.code.toFloat())
            put(StylizedWaterFields.LightColor, lightR, lightG, lightB, ambient)
            put(StylizedWaterFields.HorizonColor, env.horizonColor.r, env.horizonColor.g, env.horizonColor.b, env.exposure)
            put(StylizedWaterFields.ZenithColor, env.zenithColor.r, env.zenithColor.g, env.zenithColor.b, look.opacity)
            put(StylizedWaterFields.FogColor, env.fogColor.r, env.fogColor.g, env.fogColor.b, env.fogDensity)
            put(StylizedWaterFields.FogRange, env.fogStart, env.fogEnd, look.origin.y, 0f)
            put(StylizedWaterFields.ShallowColor, look.shallowColor.r, look.shallowColor.g, look.shallowColor.b, look.waveScale)
            put(StylizedWaterFields.DeepColor, look.deepColor.r, look.deepColor.g, look.deepColor.b, look.waveSpeed)
            put(StylizedWaterFields.FoamColor, look.foamColor.r, look.foamColor.g, look.foamColor.b, look.waveStrength)
            put(StylizedWaterFields.HighlightParams, look.sunGlintStrength, look.crestHighlightStrength, look.origin.x, look.origin.z)
        }

        recorder.bindPipeline(pipeline)
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
