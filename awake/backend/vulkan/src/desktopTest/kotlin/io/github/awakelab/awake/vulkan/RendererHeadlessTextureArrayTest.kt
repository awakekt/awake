/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.asset.shadercompiler.NagaShaderCompiler
import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaderdsl.bindingsForGroup
import io.github.awakelab.awake.asset.shaderdsl.fieldsFrom
import io.github.awakelab.awake.asset.shaderdsl.fullScreenTriangleCorner
import io.github.awakelab.awake.asset.shaderdsl.gt
import io.github.awakelab.awake.asset.shaderdsl.lit
import io.github.awakelab.awake.asset.shaderdsl.minus
import io.github.awakelab.awake.asset.shaderdsl.plus
import io.github.awakelab.awake.asset.shaderdsl.sampler
import io.github.awakelab.awake.asset.shaderdsl.select
import io.github.awakelab.awake.asset.shaderdsl.shader
import io.github.awakelab.awake.asset.shaderdsl.texture2dArray
import io.github.awakelab.awake.asset.shaderdsl.textureSampleArrayLevel
import io.github.awakelab.awake.asset.shaderdsl.times
import io.github.awakelab.awake.asset.shaderdsl.toU32
import io.github.awakelab.awake.asset.shaderdsl.vec2
import io.github.awakelab.awake.asset.shaderdsl.vec4
import io.github.awakelab.awake.asset.shaderdsl.x
import io.github.awakelab.awake.asset.shaderdsl.y
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.command.CommandRecorder
import io.github.awakelab.awake.render.passes.RenderFeature
import io.github.awakelab.awake.render.passes.RenderFrameContext
import io.github.awakelab.awake.render.passes.RenderPassSlot
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.pipeline.PipelineVariant
import io.github.awakelab.awake.render.pipeline.ResourceKind
import io.github.awakelab.awake.render.renderer.UniformField
import io.github.awakelab.awake.render.renderer.UniformLayout
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.pipeline.PipelineTable
import io.github.awakelab.awake.vulkan.pipeline.RenderPipeline
import io.github.awakelab.awake.vulkan.pipeline.ShaderPair
import io.github.awakelab.awake.vulkan.pipeline.UiShaderPairs
import io.github.awakelab.awake.vulkan.pipeline.createSceneRenderPass
import io.github.awakelab.awake.vulkan.renderer.Renderer
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import io.github.awakelab.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D28 item 1's capability on real pixels: one binding holding several layers, with the shader
 * choosing between them.
 *
 * A splat terrain wants N diffuse layers without spending N bindings, and every layer of the
 * chain had to learn what a layer is -- `TextureAsset.layerCount`, `arrayLayers` and the
 * `VK_IMAGE_VIEW_TYPE_2D_ARRAY` view, the barrier's `layerCount` (its own commit), ASL's
 * `texture_2d_array`, and `ResourceBinding.arrayed`. Each is invisible on its own.
 *
 * Every wrong version of that chain still compiles and still draws, so this asserts pixels. Two
 * layers of two halves each give four quadrants that fail differently:
 *
 * - the halves differ per layer, so ignoring the layer index reads the same colours on both
 *   sides and fails;
 * - the layers differ from each other, so a second layer that never uploaded fails rather than
 *   quietly repeating the first;
 * - each layer is split internally, so a wrong `layerOffset` -- layer 1 read from byte 0 -- fails
 *   even though both halves would still differ;
 * - none of the four is the clear colour, so a pipeline that never drew fails too.
 */
class RendererHeadlessTextureArrayTest {

    /** The declaration half, asserted alone so a failure says which end broke. */
    @Test
    fun theShaderDeclaresItsSampledTextureAsArrayed() {
        val derived = assertNotNull(ProbeShader.bindingsForGroup(MATERIAL_GROUP))

        val texture = assertNotNull(derived.at(1))
        assertEquals(ResourceKind.SampledTexture, texture.kind)
        assertTrue(
            texture.arrayed,
            "Binding 1 is declared texture2dArray, so the derived binding must say arrayed -- " +
                "otherwise ContentFeature's layer-count check passes a single-layer asset.",
        )
        assertEquals(false, assertNotNull(derived.at(2)).arrayed, "A sampler is never arrayed.")
    }

    @Test
    fun aShaderSamplesTwoLayersOfOneBinding() {
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
                "Top-left samples layer 0's top half and should be $EXPECTED_TOP_LEFT, but was " +
                    "$topLeft.",
            )
            assertTrue(
                bottomLeft.isNear(EXPECTED_BOTTOM_LEFT),
                "Bottom-left samples layer 0's bottom half and should be $EXPECTED_BOTTOM_LEFT, " +
                    "but was $bottomLeft.",
            )
            assertTrue(
                topRight.isNear(EXPECTED_TOP_RIGHT),
                "Top-right samples layer 1's top half and should be $EXPECTED_TOP_RIGHT, but was " +
                    "$topRight. Layer 1 never reached the GPU, or the view exposes only layer 0.",
            )
            assertTrue(
                bottomRight.isNear(EXPECTED_BOTTOM_RIGHT),
                "Bottom-right samples layer 1's bottom half and should be " +
                    "$EXPECTED_BOTTOM_RIGHT, but was $bottomRight.",
            )
            assertTrue(
                !topLeft.isNear(topRight),
                "Both sides read $topLeft, so the layer index selects nothing -- every sample " +
                    "landed on the same layer.",
            )
            assertTrue(
                !topLeft.isNear(CLEAR_PIXEL),
                "Top-left is the clear colour, so the probe pipeline never drew at all.",
            )
        } finally {
            target.destroy()
        }
    }

    /** No draw calls -- whatever lands is the probe pipeline's own output. */
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

    private class ArrayProbeFeature(
        private val pipeline: RenderPipeline,
    ) : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) {
            val block = checkNotNull(pipeline.uniformBlock) {
                "The probe pipeline was built with non-null uniforms, so a block must exist."
            }
            block.write(context.frameIndex) { put(floatArrayOf(SPLIT_X), SPLIT_FIELD) }
            val recorder: CommandRecorder = context.recorder
            recorder.bindPipeline(pipeline)
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

        val SPLIT_FIELD = UniformField("split", GpuDataShape.Float)
        val PROBE_LAYOUT = UniformLayout(SPLIT_FIELD)

        /**
         * The layer index comes through a uniform-driven comparison rather than a literal, so a
         * compiler that folded a constant index could not fake the result.
         */
        val ProbeShader: AslShaderDefinition = shader("texture_array_probe") {
            val u = uniformBlock("Uniforms", group = MATERIAL_GROUP, binding = 0)
            // Derived from PROBE_LAYOUT rather than restated, so the block the pipeline allocates
            // and the struct the shader reads cannot disagree.
            val split = u.fieldsFrom(PROBE_LAYOUT).value("split")
            val layers by texture2dArray(group = MATERIAL_GROUP, binding = 1)
            val layerSampler by sampler(group = MATERIAL_GROUP, binding = 2)

            val out = varyings("VertexOutput")
            val uv by out.varying(GpuDataShape.Vec2, location = 0)

            vertex {
                val corner = fullScreenTriangleCorner()
                out.position set vec4(corner, 0f.lit, 1f.lit)
                uv set vec2((corner.x + 1f.lit) * 0.5f.lit, (corner.y + 1f.lit) * 0.5f.lit)
            }

            fragment {
                val layer = toU32(select(0f.lit, 1f.lit, uv.x gt split))
                colorOutput(textureSampleArrayLevel(layers, layerSampler, uv, layer, 0f.lit))
            }
        }

        val PROBE_BINDINGS = checkNotNull(ProbeShader.bindingsForGroup(MATERIAL_GROUP)) {
            "The probe shader declares bindings in group $MATERIAL_GROUP."
        }

        /** Right of this samples layer 1, left samples layer 0. */
        const val SPLIT_X = 0.5f

        /** Big enough that the linear filter's blend stays near the seams, well away from where
         * this samples -- the lesson the content-texture probe learned at 2x2. */
        const val TEXTURE_SIZE = 64
        const val LAYER_COUNT = 2
        const val OPAQUE = 255.toByte()

        val TEXEL_RED = byteArrayOf(OPAQUE, 0, 0, OPAQUE)
        val TEXEL_GREEN = byteArrayOf(0, OPAQUE, 0, OPAQUE)
        val TEXEL_BLUE = byteArrayOf(0, 0, OPAQUE, OPAQUE)
        val TEXEL_WHITE = byteArrayOf(OPAQUE, OPAQUE, OPAQUE, OPAQUE)

        /**
         * Two layers back to back, each split top/bottom: layer 0 is red over green, layer 1 is
         * blue over white. All four colours differ, so no pair of the failure modes above
         * produces the same picture.
         */
        val PROBE_TEXELS =
            ByteArray(TEXTURE_SIZE * TEXTURE_SIZE * BYTES_PER_PIXEL * LAYER_COUNT).also { pixels ->
                for (layer in 0 until LAYER_COUNT) {
                    val layerStart = layer * TEXTURE_SIZE * TEXTURE_SIZE * BYTES_PER_PIXEL
                    for (y in 0 until TEXTURE_SIZE) {
                        val top = y < TEXTURE_SIZE / 2
                        val texel = when {
                            layer == 0 && top -> TEXEL_RED
                            layer == 0 -> TEXEL_GREEN
                            top -> TEXEL_BLUE
                            else -> TEXEL_WHITE
                        }
                        for (x in 0 until TEXTURE_SIZE) {
                            texel.copyInto(
                                pixels,
                                layerStart + (y * TEXTURE_SIZE + x) * BYTES_PER_PIXEL,
                            )
                        }
                    }
                }
            }

        val EXPECTED_TOP_LEFT = Pixel(255, 0, 0)
        val EXPECTED_BOTTOM_LEFT = Pixel(0, 255, 0)
        val EXPECTED_TOP_RIGHT = Pixel(0, 0, 255)
        val EXPECTED_BOTTOM_RIGHT = Pixel(255, 255, 255)

        /** `Renderer`'s default clear -- distinct from every expected pixel. */
        val CLEAR_PIXEL = Pixel(0, 0, 0)

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val CHANNEL_TOLERANCE = 6
        const val FULLSCREEN_TRIANGLE_VERTICES = 3
        const val EYE_Z = 3f

        /** Well inside each quadrant, away from both the layer split and the halves' seam. */
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
            val probe = probePipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial,
                transferContext,
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
                pipelines = PipelineTable(
                    primary = primary,
                    primaryFormat = VertexFormat.PositionColorUv,
                ),
                uiShaderPairs = runBlocking {
                    UiShaderPairs(
                        quad = packShaderPair("ui_quad"),
                        glyph = packShaderPair("ui_glyph"),
                        texture = packShaderPair("ui_texture"),
                        roundedQuad = packShaderPair("ui_rounded_quad"),
                    )
                },
                transferContext = transferContext,
                renderFeatures = listOf(ArrayProbeFeature(probe)),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            )
            return renderer.also { cachedRenderer = it }
        }

        /**
         * The pipeline under test, with its layered texture written into the descriptor sets it
         * just allocated -- what `VulkanEngine` does for a real `ContentFeature`.
         */
        private fun probePipeline(
            graphicsDevice: GraphicsDevice,
            swapchainManager: SwapchainManager,
            sceneRenderPass: Long,
            pipelineLayoutMaterial: Material,
            transferContext: TransferContext,
        ): RenderPipeline {
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
            probe.writeContentTextures(
                mapOf(
                    1 to Texture(
                        graphicsDevice,
                        transferContext::runOneTimeCommands,
                        PROBE_TEXELS,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE,
                        layerCount = LAYER_COUNT,
                    ).also { cachedUploads += it },
                ),
            )
            return probe
        }

        /** ASL to SPIR-V at test time through the same naga binding production uses, so there is
         * no committed `.spv` to regenerate and nothing to drift. */
        private fun probeShaderPair(): ShaderPair {
            val spirv = NagaShaderCompiler.wgslToSpirv(ProbeShader.emitWgsl())
            return ShaderPair(spirv, spirv)
        }

    }
}
