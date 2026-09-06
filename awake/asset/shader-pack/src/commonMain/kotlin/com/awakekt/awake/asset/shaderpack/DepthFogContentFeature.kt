/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.source
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.DepthFogFields
import com.awakekt.awake.render.renderer.DepthFogUniformLayout

/** A full-screen triangle covers the viewport in three vertices; see `fullScreenTriangleCorner`. */
private const val FULLSCREEN_TRIANGLE_VERTICES = 3

/**
 * Distance fog over the whole frame, as a feature an app opts into.
 *
 * Requires `RenderPlan.sceneDepthShaderSet`: this reads the camera-space depth that pass writes,
 * and a plan that omits it leaves this feature blending nothing (the depth group is an empty
 * layout, and the backend narrows the pass away with a report). The pair is the point -- fog is
 * the cheapest thing that actually consumes scene depth, and water and soft particles read the
 * same group the same way.
 *
 * Drawn [ContentPaint.AfterGeometry], unlike [skyboxContentFeature]: the sky is a backdrop and
 * this covers what has already been painted.
 *
 * The shader is emitted per backend rather than shared, because sampling a rendered depth target
 * by screen position is the one place the two clip-space conventions differ -- see
 * [ndcToUv], which decides it from the clip space rather than from a flag someone remembered.
 *
 * @param color Fog colour. Its alpha is the density per world unit, packed the way `lit_shadow`'s
 * own `fogColor` is, so the two fogs are tuned in the same units.
 * @param isVisible Read every frame, so a consumer can toggle fog without rebuilding the plan.
 */
fun depthFogContentFeature(
    color: Color,
    isVisible: () -> Boolean = { true },
): ContentFeatureSource = ContentFeatureSource { backend ->
    val shaders = aslShaderSet(::depthFogShader)
    val stages = shaders.stagesFor(backend)
    ContentFeature(
        name = "depth_fog",
        spec = PipelineSpec(
            vertexFormat = VertexFormat.None,
            vertexShader = stages.source(ShaderStage.VERTEX),
            fragmentShader = stages.source(ShaderStage.FRAGMENT),
            variant = PipelineVariant.Overlay,
            uniforms = DepthFogUniformLayout,
        ),
        paint = ContentPaint.AfterGeometry,
        samplesSceneDepth = true,
    ) { pipeline, uniforms, _ -> DepthFogRenderFeature(pipeline, uniforms, color, isVisible) }
}

/**
 * Records the fog's full-screen triangle, having written this frame's camera.
 *
 * Public for the same reason [TerrainRenderFeature] is: a test that builds the pipeline itself
 * drives the real recording path rather than restating it.
 */
class DepthFogRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val color: Color,
    private val isVisible: () -> Boolean = { true },
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    override fun recordCommands(context: RenderFrameContext) {
        if (!isVisible()) return
        // Null when this frame's viewProjection is singular -- no world position to unproject a
        // fog distance from, the same guard the sky makes for the same reason.
        val inverse = context.viewProjection.inverse() ?: return
        uniforms.write(context.frameIndex) {
            put(DepthFogFields.InverseViewProjection, inverse)
            put(DepthFogFields.CameraEye, context.cameraEye)
            put(DepthFogFields.FogColor, color)
        }
        val recorder: CommandRecorder = context.recorder
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
        // Null on Vulkan, which binds its scene-depth set with the pipeline -- see
        // RenderFrameContext.sceneDepthBinding.
        context.sceneDepthBinding(pipeline)?.let {
            recorder.bindMaterial(BindingSemantic.SceneDepth, it)
        }
        recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
    }

    /** Nothing: the registry owns the pipeline it built, and the block lives on it. */
    override fun destroy() = Unit
}
