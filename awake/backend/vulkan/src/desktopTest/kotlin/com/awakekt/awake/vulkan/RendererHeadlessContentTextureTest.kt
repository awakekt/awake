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
import com.awakekt.awake.asset.shaderdsl.minus
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
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The capability D28 item 2 exists for, proven on real pixels: a content pipeline sampling a
 * texture it owns, through bindings its own shader declared.
 *
 * Until this test, every phase of the declared-binding work was a refactor. The layout, the
 * pool, the descriptor writes and the WebGPU entries all changed, and the only evidence was that
 * nothing regressed -- nothing had ever bound a texture through a declaration.
 *
 * It closes the whole chain in one run: ASL declares the bindings, `bindingsForGroup` turns them
 * into a `GroupBindings`, `PerFrameUniformSlots` derives a descriptor set layout and pool from
 * that, `writeContentTextures` fills it, and the sampled result reaches the framebuffer. No step
 * is restated by hand, so no step can drift from another.
 *
 * Failure modes here are silent -- a descriptor in the wrong slot, a pool that undercounts, a
 * sampler never bound all compile and draw *something* -- so it asserts pixels. A 2x2 texture of
 * four distinct colours makes each of those a different wrong picture rather than one uniform
 * wrong colour:
 *
 * - each quadrant matches its own texel, so a texture bound at the wrong index fails;
 * - the four differ, so a flat fill of any single colour fails;
 * - none is the clear colour, so a pipeline that never drew fails rather than reading the
 *   cleared attachment;
 * - the tint is non-uniform per channel, so a uniform block displaced by the image descriptors
 *   fails too.
 */
class RendererHeadlessContentTextureTest {

    /** The chain's first link, asserted on its own so a failure says which half broke. */
    @Test
    fun theShaderItselfDeclaresTheUniformImageAndSamplerTriple() {
        val derived = assertNotNull(ProbeShader.bindingsForGroup(MATERIAL_GROUP))

        assertEquals(listOf(0, 1, 2), derived.entries.map { it.binding })
        assertEquals(PROBE_BINDINGS, derived)
    }

    @Test
    fun aContentPipelineSamplesATextureItDeclaredForItself() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            val pixels = renderer.renderProbe(target)

            val topLeft = pixels.pixelAt(QUARTER, QUARTER)
            val topRight = pixels.pixelAt(THREE_QUARTER, QUARTER)
            val bottomLeft = pixels.pixelAt(QUARTER, THREE_QUARTER)
            val bottomRight = pixels.pixelAt(THREE_QUARTER, THREE_QUARTER)

            assertTrue(
                topLeft.isNear(EXPECTED_TOP_LEFT),
                "Top-left should be the texture's first texel tinted, $EXPECTED_TOP_LEFT, but " +
                    "was $topLeft. The pipeline drew; the texture descriptor is bound to the " +
                    "wrong slot, or was never written.",
            )
            assertTrue(
                topRight.isNear(EXPECTED_TOP_RIGHT),
                "Top-right should be $EXPECTED_TOP_RIGHT but was $topRight.",
            )
            assertTrue(
                bottomLeft.isNear(EXPECTED_BOTTOM_LEFT),
                "Bottom-left should be $EXPECTED_BOTTOM_LEFT but was $bottomLeft.",
            )
            assertTrue(
                bottomRight.isNear(EXPECTED_BOTTOM_RIGHT),
                "Bottom-right should be $EXPECTED_BOTTOM_RIGHT but was $bottomRight.",
            )
            assertTrue(
                !topLeft.isNear(topRight) && !topLeft.isNear(bottomLeft),
                "All four quadrants read alike ($topLeft) -- a flat fill, so the sampler or the " +
                    "UVs are wrong even though each quadrant's own tolerance was met.",
            )
            assertTrue(
                !topLeft.isNear(CLEAR_PIXEL),
                "Top-left is the clear colour, so the content pipeline never drew at all.",
            )
        } finally {
            target.destroy()
        }
    }

    /** No draw calls -- whatever lands is the content pipeline's own output. */
    private fun Renderer.renderProbe(target: RenderTarget): ByteArray {
        renderToTexture(
            target,
            Lens(
                eye = Vec3f(0f, 0f, EYE_Z),
                center = Vec3f(0f, 0f, 0f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 10f,
            ),
            emptyList(),
        )
        return runBlocking { readPixels(target) }.data
    }

    private fun ByteArray.pixelAt(x: Int, y: Int): Pixel {
        val offset = (y * TARGET_SIZE + x) * BYTES_PER_PIXEL
        return Pixel(
            this[offset].toInt() and 0xFF,
            this[offset + 1].toInt() and 0xFF,
            this[offset + 2].toInt() and 0xFF,
        )
    }

    private data class Pixel(val red: Int, val green: Int, val blue: Int) {
        fun isNear(expected: Pixel) =
            kotlin.math.abs(red - expected.red) <= CHANNEL_TOLERANCE &&
                kotlin.math.abs(green - expected.green) <= CHANNEL_TOLERANCE &&
                kotlin.math.abs(blue - expected.blue) <= CHANNEL_TOLERANCE
    }

    /** Binds the probe pipeline and draws the generated triangle -- the same three calls a real
     * content feature makes, with a texture riding in the block's own descriptor set. */
    private class ContentTextureProbeFeature(
        private val pipeline: RenderPipeline,
    ) : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) {
            val block: UniformBlock = checkNotNull(pipeline.uniformBlock) {
                "The probe pipeline was built with non-null uniforms, so a block must exist."
            }
            block.write(context.frameIndex) { put(TINT_FIELD, TINT) }
            val recorder: CommandRecorder = context.recorder
            recorder.bindPipeline(pipeline)
            // One bind for the whole group: the texture and sampler live in the same descriptor
            // set as the uniform buffer, which is why ContentFeature needed no new API.
            recorder.bindMaterial(BindingSemantic.Material, block.binding(context.frameIndex))
            recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
        }

        override fun destroy() = pipeline.destroy()
    }

    private companion object {
        private var cachedRenderer: Renderer? = null

        /** Content-feature uploads this fixture makes by hand, exactly as `VulkanEngine` does
         * for a real one -- and destroyed for the same reason it destroys its own: a pipeline's
         * descriptor set references a texture without owning it. */
        private val cachedUploads = mutableListOf<Texture>()

        /** The device the cached renderer was built on. `Renderer.destroy` frees what the
         * renderer built, not the device underneath it, so without this the headless device and
         * its instance outlive this class and add to the GPU contention every later suite in the
         * same JVM fights. */
        private var cachedDevice: GraphicsDevice? = null

        /** Frees what neither the renderer nor the device frees itself -- the render pass,
         * pipelines, descriptor set layout and transfer context this fixture built by hand.
         * Captured as a lambda where those locals are still in scope, which is smaller than
         * threading each one back out through a holder. */
        private var cachedCleanup: (() -> Unit)? = null

        @AfterClass
        @JvmStatic
        fun releaseSharedRenderer() {
            cachedRenderer?.destroy()
            cachedRenderer = null
            cachedCleanup?.invoke()
            cachedCleanup = null
            cachedUploads.forEach { it.destroy() }
            cachedUploads.clear()
            cachedDevice?.destroy()
            cachedDevice = null
        }

        val MATERIAL_GROUP = BindingLayout.Standard.slot(BindingSemantic.Material)

        val TINT_FIELD = UniformField("tint", GpuDataShape.Vec4)
        val PROBE_LAYOUT = UniformLayout(TINT_FIELD)

        /**
         * Authored in ASL, not hand-written WGSL, for the reason every shipped shader is: the
         * bindings below are the same declaration `bindingsForGroup` reads back, so the layout
         * this pipeline is built from cannot disagree with the shader it is built for. A
         * hand-written `.wgsl` plus a hand-written `GroupBindings` would be two statements of one
         * fact -- exactly the drift class D28 item 1's phase 5 exists to remove.
         *
         * Vertex-less: one oversized triangle from `vertex_index`, the same trick the skybox
         * uses. Vulkan's clip space is Y-down, so y = -1 is the read-back image's TOP row and
         * maps to v = 0, the texture's first row.
         */
        val ProbeShader: AslShaderDefinition = shader("content_texture_probe") {
            val u = uniformBlock("Uniforms", group = MATERIAL_GROUP, binding = 0)
            // Derived from PROBE_LAYOUT rather than restated: the same one-field-list rule the
            // shipped shaders follow, so the block the pipeline allocates and the struct the
            // shader reads cannot disagree.
            val tint = u.fieldsFrom(PROBE_LAYOUT).value("tint")
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
                colorOutput(textureSample(probeTexture, probeSampler, uv) * tint)
            }
        }

        /** Read back from the shader rather than restated -- the point of the test. */
        val PROBE_BINDINGS = checkNotNull(ProbeShader.bindingsForGroup(MATERIAL_GROUP)) {
            "The probe shader declares bindings in group $MATERIAL_GROUP."
        }

        /** Non-uniform per channel so a displaced uniform block cannot pass: green is halved,
         * and every expected pixel below is its texel times this. */
        val TINT = Color(r = 1f, g = 0.5f, b = 1f, a = 1f)

        /**
         * Four solid quadrants rather than a 2x2 image. At 2x2 every texel centre sits a quarter
         * of the image away from every sample point, so the default linear filter blends all four
         * colours everywhere and no quadrant ever reads its own texel -- the first version of this
         * test read (94, 31, 193) where it expected pure red. At 64x64 the blend is confined to a
         * few texels either side of each seam, far from where this samples.
         */
        const val TEXTURE_SIZE = 64
        const val OPAQUE = 255.toByte()

        val TEXEL_RED = byteArrayOf(OPAQUE, 0, 0, OPAQUE)
        val TEXEL_GREEN = byteArrayOf(0, OPAQUE, 0, OPAQUE)
        val TEXEL_BLUE = byteArrayOf(0, 0, OPAQUE, OPAQUE)
        val TEXEL_WHITE = byteArrayOf(OPAQUE, OPAQUE, OPAQUE, OPAQUE)

        /** Row-major RGBA8: red, green across the top; blue, white across the bottom. */
        val PROBE_TEXELS = ByteArray(TEXTURE_SIZE * TEXTURE_SIZE * BYTES_PER_PIXEL).also { pixels ->
            for (y in 0 until TEXTURE_SIZE) {
                for (x in 0 until TEXTURE_SIZE) {
                    val quadrant = when {
                        y < TEXTURE_SIZE / 2 && x < TEXTURE_SIZE / 2 -> TEXEL_RED
                        y < TEXTURE_SIZE / 2 -> TEXEL_GREEN
                        x < TEXTURE_SIZE / 2 -> TEXEL_BLUE
                        else -> TEXEL_WHITE
                    }
                    quadrant.copyInto(pixels, (y * TEXTURE_SIZE + x) * BYTES_PER_PIXEL)
                }
            }
        }

        // Texel times TINT. Green is halved wherever it appears.
        val EXPECTED_TOP_LEFT = Pixel(255, 0, 0)
        val EXPECTED_TOP_RIGHT = Pixel(0, 128, 0)
        val EXPECTED_BOTTOM_LEFT = Pixel(0, 0, 255)
        val EXPECTED_BOTTOM_RIGHT = Pixel(255, 128, 255)

        /** `Renderer`'s default clear -- distinct from every expected pixel. */
        val CLEAR_PIXEL = Pixel(0, 0, 0)

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val CHANNEL_TOLERANCE = 6
        const val FULLSCREEN_TRIANGLE_VERTICES = 3
        const val EYE_Z = 3f

        /** Sampled well inside each quadrant: a 2x2 texture's bilinear filter ramps across the
         * middle, so the exact centre would read a blend of all four texels. */
        const val QUARTER = TARGET_SIZE / 8
        const val THREE_QUARTER = TARGET_SIZE - TARGET_SIZE / 8

        fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val transferContext = TransferContext(graphicsDevice)

            // Required by Renderer; nothing draws through it, since the test issues no draw calls.
            val primary = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val probe = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                probeShaderPair(),
                VertexFormat.None,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                variant = PipelineVariant.Background,
                uniforms = PROBE_LAYOUT,
                framesInFlight = MAX_FRAMES_IN_FLIGHT,
                materialBindings = PROBE_BINDINGS,
            )
            // What VulkanEngine does for a real ContentFeature: upload, then write into the
            // descriptor sets the pipeline already allocated.
            probe.writeContentTextures(
                mapOf(
                    1 to Texture(
                        graphicsDevice,
                        transferContext::runOneTimeCommands,
                        PROBE_TEXELS,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE,
                    ).also { cachedUploads += it },
                ),
            )

            cachedCleanup = headlessCleanup(
                graphicsDevice,
                transferContext,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                primary,
            )
            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
                uiShaderPairs = runBlocking {
                    UiShaderPairs(
                        quad = packShaderPair("ui_quad"),
                        glyph = packShaderPair("ui_glyph"),
                        texture = packShaderPair("ui_texture"),
                        roundedQuad = packShaderPair("ui_rounded_quad"),
                    )
                },
                transferContext = transferContext,
                renderFeatures = listOf(ContentTextureProbeFeature(probe)),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            )
            return renderer.also { cachedRenderer = it }
        }

        /**
         * ASL to SPIR-V at test time, through the same naga binding `VulkanShaderResolver` uses
         * in production -- so there is no committed `.spv` to regenerate and nothing to drift.
         * One module carries both entry points; the pair holds it twice, and the entry point
         * names pick the stage.
         */
        private fun probeShaderPair(): ShaderPair {
            val spirv = NagaShaderCompiler.wgslToSpirv(ProbeShader.emitWgsl())
            return ShaderPair(spirv, spirv)
        }
    }
}
