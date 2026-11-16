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
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.renderer.uploadTexture
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Renderer.maxAnisotropy` on Vulkan (#1660), through a content pipeline whose texture is uploaded
 * and registered as a real one is. Its probe samples 8-texel stripes with a footprint 8 times longer
 * along one axis than the other, through `textureSample` with implicit derivatives: lavapipe filters
 * that anisotropically, and ignores anisotropy for `textureSampleGrad` with explicit ones (checked on
 * this lavapipe with a standalone Vulkan probe), so the PBR textured shader's change shows only on a
 * real GPU -- recorded on #1660.
 *
 * The device fails on any validation error when it is destroyed: a sampler destroyed while bound,
 * destroyed twice, or never destroyed among them.
 */
class RendererAnisotropyTest {
    @Test
    fun aChangedAnisotropyRemakesTheSamplerAndTheContentSetTakesItBeforeDrawing() = withRenderer { renderer, probe ->
        val texture = renderer.uploadTexture(stripes()).also { probe.writeContentTextures(mapOf(1 to it)) }
        val off = renderer.frame()
        assertEquals(1, texture.samplerAnisotropy)

        renderer.maxAnisotropy = SAMPLES
        assertEquals(SAMPLES, texture.samplerAnisotropy, "the texture's sampler was made again at $SAMPLES")
        val on = renderer.frame()
        assertTrue(probe.contentSamplerRewrites >= 1, "the content set took the new sampler before it drew")
        val changed = off.indices.step(4).count { kotlin.math.abs((off[it].toInt() and 0xFF) - (on[it].toInt() and 0xFF)) > 2 }
        println("RendererAnisotropy: $changed of ${SIZE * SIZE} pixels changed between 1 and $SAMPLES samples")
        assertTrue(changed > SIZE * SIZE / 2, "stripes resolve along the long axis at $SAMPLES samples: $changed changed")

        renderer.maxAnisotropy = 1
        assertContentEquals(off, renderer.frame(), "back at 1, the frame is the first one again")
    }

    @Test
    fun theSameValueAgainCreatesNothingAndAPendingSamplerOutlivesItsTexture() = withRenderer { renderer, probe ->
        val texture = renderer.uploadTexture(stripes()).also { probe.writeContentTextures(mapOf(1 to it)) }
        renderer.maxAnisotropy = SAMPLES
        val sampler = texture.sampler.handle
        val pending = renderer.retiredSamplers.pending
        renderer.maxAnisotropy = SAMPLES
        renderer.maxAnisotropy = SAMPLES * 4
        renderer.maxAnisotropy = renderer.maxSupportedAnisotropy

        assertEquals(1, pending, "the replaced 1x sampler waits for the frames in flight")
        if (renderer.maxSupportedAnisotropy == SAMPLES) assertEquals(sampler, texture.sampler.handle, "the same value made nothing")
        // The texture goes while its replaced sampler is still waiting: each is destroyed once, by
        // its owner, and the device reports nothing left behind when it is destroyed.
        renderer.textureResources.release(texture)
        assertTrue(renderer.retiredSamplers.pending >= 1, "the retired sampler is still the queue's to destroy")
    }

    /**
     * What a frame with no change pays for the check: a material slot and a content slot are looked
     * up, as each draw binds them, without allocating -- one handle compared per slot.
     */
    @Test
    fun anUnchangedSettingAllocatesNothingWhereASlotIsBound() = withRenderer { renderer, probe ->
        val texture = renderer.uploadTexture(stripes()).also { probe.writeContentTextures(mapOf(1 to it)) }
        renderer.maxAnisotropy = SAMPLES
        val material = renderer.createMaterial(texture = stripes()) as Material
        val block = checkNotNull(probe.uniformBlock)
        renderer.frame()
        // The offscreen frame's own slot, as renderToTexture binds them.
        fun bindBoth() {
            material.uniformSlot(FRAMES_IN_FLIGHT, 0)
            block.binding(FRAMES_IN_FLIGHT)
        }
        repeat(WARM_UP) { bindBoth() }
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        // The quietest of several windows, as the other allocation gates read theirs: a JIT event can
        // land a few hundred bytes in one window, a per-bind allocation fills every window.
        val windows = LongArray(WINDOWS) {
            val before = threads.currentThreadAllocatedBytes
            repeat(BINDS) { bindBoth() }
            threads.currentThreadAllocatedBytes - before
        }
        println("RendererAnisotropy: ${windows.joinToString()} bytes per $BINDS unchanged binds of a material and a content slot")
        assertEquals(0L, windows.min(), "an unchanged sampler is one comparison per bind")
        assertEquals(SAMPLES, texture.samplerAnisotropy)
        material.destroy()
    }

    private fun Renderer.frame(): ByteArray {
        val target = createRenderTarget(SIZE, SIZE)
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

    private fun withRenderer(block: (Renderer, RenderPipeline) -> Unit) {
        val device = GraphicsDevice().apply { createHeadless() }
        try {
            val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT).apply { createHeadless(SIZE, SIZE) }
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
            val spirv = NagaShaderCompiler.wgslToSpirv(ProbeShader.emitWgsl())
            val probe = RenderPipeline(
                device, swapchain, renderPass, layoutMaterial.descriptorSetLayout, ShaderPair(spirv, spirv), VertexFormat.None,
                vertexEntryPoint = "vertexMain", fragmentEntryPoint = "fragmentMain", variant = PipelineVariant.Background,
                uniforms = PROBE_LAYOUT, framesInFlight = FRAMES_IN_FLIGHT, materialBindings = checkNotNull(ProbeShader.bindingsForGroup(MATERIAL_GROUP)),
            )
            val cleanup = headlessCleanup(device, transfer, renderPass, layoutMaterial.descriptorSetLayout, primary)
            val renderer = Renderer(
                graphicsDevice = device,
                swapchainManager = swapchain,
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
                uiShaderPairs = runBlocking { defaultUiShaderPairs() },
                transferContext = transfer,
                renderFeatures = listOf(ProbeFeature(probe)),
                maxFramesInFlight = FRAMES_IN_FLIGHT,
            )
            try {
                assertTrue(renderer.maxSupportedAnisotropy >= SAMPLES, "this device offers samplerAnisotropy up to ${renderer.maxSupportedAnisotropy}")
                block(renderer, probe)
            } finally {
                renderer.destroy()
                cleanup()
            }
        } finally {
            device.destroy()
        }
    }

    private companion object {
        const val SIZE = 64
        const val SAMPLES = 8
        const val FRAMES_IN_FLIGHT = 2
        const val TRIANGLE_VERTICES = 3
        const val TEXELS = 256
        const val STRIPE = 8
        const val WARM_UP = 2_000
        const val BINDS = 10_000
        const val WINDOWS = 5

        val MATERIAL_GROUP = BindingLayout.Standard.slot(BindingSemantic.Material)
        val TINT_FIELD = UniformField("tint", GpuDataShape.Vec4)
        val PROBE_LAYOUT = UniformLayout(TINT_FIELD)

        /** Stripes across u, sampled once across and 8 times along v over the frame: an anisotropic footprint. */
        val ProbeShader: AslShaderDefinition = shader("anisotropy_probe") {
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
                colorOutput(textureSample(probeTexture, probeSampler, vec2(uv.x * 1f.lit, uv.y * 8f.lit)) * tint)
            }
        }

        fun stripes() = TextureAsset(
            data = ByteArray(TEXELS * TEXELS * 4) { index -> if (index % 4 == 3 || ((index / 4) % TEXELS / STRIPE) % 2 == 0) -1 else 0 },
            width = TEXELS,
            height = TEXELS,
        )
    }
}
