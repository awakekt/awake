/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

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
import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.application.WebGpuContentFeatureGpu
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineFactory
import com.awakekt.awake.webgpu.pipeline.WebGpuShaderResolver
import com.awakekt.awake.webgpu.renderer.Renderer
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue

/** The side of the square target [withContentProbe] draws into. */
internal const val PROBE_SIZE = 64

private class ProbeFeature(private val pipeline: PipelineHandle, private val uniforms: UniformBlock) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    override fun recordCommands(context: RenderFrameContext) {
        uniforms.write(context.frameIndex) { put(Tint, 1f, 1f, 1f, 1f) }
        context.recorder.bindPipeline(pipeline)
        context.recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
        context.recorder.draw(3, 1)
    }

    override fun destroy() = Unit
}

/**
 * A renderer with one content feature attached as a terrain layer is: a full-screen triangle sampling
 * [texture] at `uv * scale + offset` through `textureSample`, drawn into a [PROBE_SIZE]-square target.
 * [beforeAttach] runs before the feature's texture uploads, for a setting that applies to later uploads.
 * Fails on any WebGPU validation error.
 */
internal fun withContentProbe(
    texture: TextureAsset,
    scale: Pair<Float, Float>,
    offset: Pair<Float, Float> = 0f to 0f,
    beforeAttach: (Renderer) -> Unit = {},
    block: (Renderer, GraphicsDevice, () -> ByteArray) -> Unit,
) = runBlocking {
    val errors = mutableListOf<String>()
    val context = glfwContextRenderer(width = 1, height = 1, title = "awake-content-probe", onUncapturedError = { errors += it.toString() })
    val device = GraphicsDevice().apply { create(context.wgpuContext) }
    val swapchain = SwapchainManager(device, 1).apply { create() }
    val registry = PipelineRegistry(WebGpuPipelineFactory(device, swapchain, WebGpuShaderResolver()))
    val attacher = ContentFeatureAttacher(WebGpuContentFeatureGpu(device, registry))
    val linePipeline = LineRenderPipeline(device, swapchain, wgsl(EngineShaderSets.DebugLine))
    val primary = RenderPipeline(
        device,
        swapchain,
        DescriptorSetLayoutHandle(0),
        readResourceBytes("assets/shader/webgpu/pixel_probe.wgsl"),
        ByteArray(0),
        VertexFormat.PositionColorUv,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = mapOf(0 to GroupBindings.UniformOnlyMaterial),
        bindingsMetadataAvailable = true,
    )
    val renderer = Renderer(
        graphicsDevice = device,
        swapchainManager = swapchain,
        pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
        lineRenderPipeline = linePipeline,
        uiShaderSources = UiShaderSources(
            quad = wgsl(EngineShaderSets.UiQuad),
            glyph = wgsl(EngineShaderSets.UiGlyph),
            texture = wgsl(EngineShaderSets.UiTexture),
            roundedQuad = wgsl(EngineShaderSets.UiRoundedQuad),
            targetComposite = wgsl(EngineShaderSets.UiTargetComposite),
        ),
        maxFramesInFlight = 1,
        renderFeatures = listOf(attacher.beforeGeometry, attacher.opaqueGeometry, attacher.afterGeometry),
    ).also { it.contentFeatureHost = attacher }
    beforeAttach(renderer)
    attacher.attachContentFeature(probe(texture, scale, offset))
    val target = renderer.createRenderTarget(PROBE_SIZE, PROBE_SIZE)
    try {
        block(renderer, device) {
            renderer.renderSceneToTexture(target, Lens(eye = Vec3f(0f, 0f, 3f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f), emptyList())
            runBlocking { renderer.readPixels(target) }.data
        }
        assertTrue(errors.isEmpty(), "WebGPU validation errors: $errors")
    } finally {
        target.destroy()
        renderer.destroy()
        registry.destroyAll { it.destroy() }
        attacher.releaseAll()
        primary.destroy()
        linePipeline.destroy()
        swapchain.destroy()
        device.destroy()
    }
}

private val Tint = UniformField("tint", GpuDataShape.Vec4)
private val Layout = UniformLayout(Tint)

private suspend fun wgsl(set: ShaderSet): ByteArray = checkNotNull(set.webGpu[ShaderStage.VERTEX]).resolveBytes()

private fun probe(texture: TextureAsset, scale: Pair<Float, Float>, offset: Pair<Float, Float>) = ContentFeatureSource {
    val stages = aslShaderSet(
        shader("content_probe") {
            val group = BindingLayout.Standard.slot(BindingSemantic.Material)
            val tint = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(Layout).value("tint")
            val probeTexture by texture2d(group = group, binding = 1)
            val probeSampler by sampler(group = group, binding = 2)
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
        },
    ).webGpu
    ContentFeature(
        name = "content-probe",
        spec = stages.spec(vertexFormat = VertexFormat.None, variant = PipelineVariant.Overlay, uniforms = Layout),
        textures = mapOf(1 to texture),
    ) { pipeline, uniforms, _ -> ProbeFeature(pipeline, uniforms) }
}
