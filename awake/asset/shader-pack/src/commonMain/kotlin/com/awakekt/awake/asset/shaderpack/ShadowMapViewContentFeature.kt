/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.samplerComparison
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.textureDepth2dArray
import com.awakekt.awake.asset.shaderdsl.textureDimensions
import com.awakekt.awake.asset.shaderdsl.textureSampleCompareLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** `x` = the shadow-map array layer to show; negative shows an empty map. */
val ShadowMapViewLayer = UniformField("layer", GpuDataShape.Vec4)

/** Uniform layout specification for the debug shadow map visualization pass. */
val ShadowMapViewUniformLayout = UniformLayout(ShadowMapViewLayer)

/** Halvings of 0..1 per pixel: 2^-16 of depth, finer than any shadow map stores. */
private const val DEPTH_SEARCH_STEPS = 16

private const val FULLSCREEN_TRIANGLE_VERTICES = 3

/**
 * One layer of the engine's shadow depth array, full screen: black at the light, white where
 * nothing was drawn. Screen position is the layer's own NDC, so a caster sits where the lookup
 * finds it.
 *
 * Reads through the same comparison binding the scene shaders sample, so it shows exactly what
 * they compare against, on both backends, with nothing new bound. A comparison returns only
 * "stored depth >= reference" (the samplers compare less-or-equal), so the depth is recovered by
 * bisection; sampling at the texel centre gives a linear comparison sampler one texel to weigh,
 * which makes every comparison exact.
 *
 * @param clipSpace The backend's clip space; decides how NDC maps to texture UV, as in the lookup.
 */
fun shadowMapViewShader(clipSpace: ClipSpace): AslShaderDefinition = shader("shadow_map_view") {
    val u = uniformBlock("Uniforms", group = BindingLayout.Standard.slot(BindingSemantic.Material), binding = 0)
    val layer = u.fieldsFrom(ShadowMapViewUniformLayout).value("layer")
    val shadowGroup = BindingLayout.Standard.slot(BindingSemantic.ShadowDepth)
    val shadowMap by textureDepth2dArray(group = shadowGroup, binding = 0)
    val shadowMapSampler by samplerComparison(group = shadowGroup, binding = 1)

    val out = varyings("VertexOutput")
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)

    vertex {
        val corner = fullScreenTriangleCorner()
        out.position set vec4(corner, 0f.lit, 1f.lit)
        ndc set corner
    }

    fragment {
        val size = let("mapSize", vec2(textureDimensions(shadowMap)))
        val uv = let("uv", (floor(ndcToUv(ndc, clipSpace) * size) + vec2(0.5f.lit)) / size)
        val index = let("layerIndex", toU32(layer.x + 0.5f.lit))
        val low = variable("low", 0f.lit)
        val high = variable("high", 1f.lit)
        loopI32("step", 0.lit, (DEPTH_SEARCH_STEPS - 1).lit) {
            val mid = let("mid", (low + high) * 0.5f.lit)
            val stored = let("storedAtLeastMid", textureSampleCompareLevel(shadowMap, shadowMapSampler, uv, index, mid) gt 0.5f.lit)
            assign(low, select(low, mid, stored))
            assign(high, select(mid, high, stored))
        }
        val depth = let("depth", select((low + high) * 0.5f.lit, 1f.lit, layer.x lt 0f.lit))
        colorOutput(vec4(vec3(depth), 1f.lit))
    }
}

/**
 * The shadow map viewer, as a feature an app opts into. Draws only while the pass's debug view is
 * [RenderDebugView.ShadowMap], over everything, at the layer the pass names. A negative layer
 * draws an empty map, for a caller asking about a layer nothing wrote.
 */
fun shadowMapViewContentFeature(): ContentFeatureSource = ContentFeatureSource { backend ->
    val stages = aslShaderSet(::shadowMapViewShader).stagesFor(backend)
    ContentFeature(
        name = "shadow_map_view",
        spec = stages.spec(
            vertexFormat = VertexFormat.None,
            variant = PipelineVariant.Overlay,
            uniforms = ShadowMapViewUniformLayout,
        ),
        paint = ContentPaint.AfterGeometry,
    ) { pipeline, uniforms, _ -> ShadowMapViewRenderFeature(pipeline, uniforms) }
}

/** Records [shadowMapViewShader]'s triangle. Public so a test can drive the real recording path. */
class ShadowMapViewRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    override fun recordCommands(context: RenderFrameContext) {
        val view = context.environment.debugView
        if (view.code != RenderDebugView.ShadowMap.code) return
        uniforms.write(context.frameIndex) { put(ShadowMapViewLayer, view.layer.toFloat(), 0f, 0f, 0f) }
        val recorder = context.recorder
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
        context.engineBinding(pipeline, BindingSemantic.ShadowDepth)?.let { recorder.bindMaterial(BindingSemantic.ShadowDepth, it) }
        recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
    }

    override fun destroy() = Unit
}
