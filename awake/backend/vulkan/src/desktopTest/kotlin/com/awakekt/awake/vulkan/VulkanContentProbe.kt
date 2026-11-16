/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking

/** The side of the square target [frame] draws into. */
internal const val PROBE_SIZE = 64

/** Frames in flight of [withProbeRenderer]'s renderer. */
internal const val PROBE_FRAMES_IN_FLIGHT = 2
private const val TRIANGLE_VERTICES = 3

/** Draws one frame of the probe into a [PROBE_SIZE]-square target and reads it back. */
internal fun Renderer.frame(): ByteArray {
    val target = createRenderTarget(PROBE_SIZE, PROBE_SIZE)
    return try {
        renderToTexture(target, Lens(eye = Vec3f(0f, 0f, 3f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f), emptyList())
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
    }
}

private class ProbeFeature(private val pipeline: RenderPipeline) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    override fun recordCommands(context: RenderFrameContext) {
        val block = checkNotNull(pipeline.uniformBlock)
        block.write(context.frameIndex) { put(TINT_FIELD, Color.White) }
        context.recorder.bindPipeline(pipeline)
        context.recorder.bindMaterial(BindingSemantic.Material, block.binding(context.frameIndex))
        context.recorder.draw(TRIANGLE_VERTICES, 1)
    }

    override fun destroy() = pipeline.destroy()
}

/**
 * A headless renderer drawing one content pipeline: a full-screen triangle sampling the texture the
 * test writes to its binding 1 at `uv * scale + offset`, through `textureSample` with implicit
 * derivatives. The device fails on any validation error when it is destroyed.
 */
internal fun withProbeRenderer(scale: Pair<Float, Float>, offset: Pair<Float, Float> = 0f to 0f, block: (Renderer, RenderPipeline) -> Unit) {
    val probeShader = probeShader(scale, offset)
    val device = GraphicsDevice().apply { createHeadless() }
    try {
        val swapchain = SwapchainManager(device, PROBE_FRAMES_IN_FLIGHT).apply { createHeadless(PROBE_SIZE, PROBE_SIZE) }
        val layoutMaterial = Material(device)
        val renderPass = createSceneRenderPass(device, swapchain)
        val transfer = TransferContext(device)
        val primary = RenderPipeline(
            device,
            swapchain,
            renderPass,
            layoutMaterial.descriptorSetLayout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionColorUv,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val spirv = NagaShaderCompiler.wgslToSpirv(probeShader.emitWgsl())
        val probe = RenderPipeline(
            device, swapchain, renderPass, layoutMaterial.descriptorSetLayout, ShaderPair(spirv, spirv), VertexFormat.None,
            vertexEntryPoint = "vertexMain", fragmentEntryPoint = "fragmentMain", variant = PipelineVariant.Background,
            uniforms = PROBE_LAYOUT, framesInFlight = PROBE_FRAMES_IN_FLIGHT, materialBindings = checkNotNull(probeShader.bindingsForGroup(MATERIAL_GROUP)),
        )
        val cleanup = headlessCleanup(device, transfer, renderPass, layoutMaterial.descriptorSetLayout, primary)
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
            uiShaderPairs = runBlocking { defaultUiShaderPairs() },
            transferContext = transfer,
            renderFeatures = listOf(ProbeFeature(probe)),
            maxFramesInFlight = PROBE_FRAMES_IN_FLIGHT,
        )
        try {
            block(renderer, probe)
        } finally {
            renderer.destroy()
            cleanup()
        }
    } finally {
        device.destroy()
    }
}

private val MATERIAL_GROUP = BindingLayout.Standard.slot(BindingSemantic.Material)
private val TINT_FIELD = UniformField("tint", GpuDataShape.Vec4)
private val PROBE_LAYOUT = UniformLayout(TINT_FIELD)

/** Samples binding 1 at `uv * scale + offset` across the frame. */
private fun probeShader(scale: Pair<Float, Float>, offset: Pair<Float, Float>): AslShaderDefinition = shader("content_probe") {
    val tint = uniformBlock("Uniforms", group = MATERIAL_GROUP, binding = 0).fieldsFrom(PROBE_LAYOUT).value("tint")
    val probeTexture by texture2d(group = MATERIAL_GROUP, binding = 1)
    val probeSampler by sampler(group = MATERIAL_GROUP, binding = 2)
    val out = varyings("VertexOutput")
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    vertex {
        val corner = fullScreenTriangleCorner()
        out.position set vec4(corner, 0f.lit, 1f.lit)
        uv set vec2((corner.x + 1f.lit) * 0.5f.lit, (corner.y + 1f.lit) * 0.5f.lit)
    }
    fragment {
        colorOutput(textureSample(probeTexture, probeSampler, vec2(uv.x * scale.first.lit + offset.first.lit, uv.y * scale.second.lit + offset.second.lit)) * tint)
    }
}
