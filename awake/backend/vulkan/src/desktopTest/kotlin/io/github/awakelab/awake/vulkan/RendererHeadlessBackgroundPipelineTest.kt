/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.command.CommandRecorder
import io.github.awakelab.awake.render.command.UniformBlock
import io.github.awakelab.awake.render.passes.RenderFeature
import io.github.awakelab.awake.render.passes.RenderFrameContext
import io.github.awakelab.awake.render.passes.RenderPassSlot
import io.github.awakelab.awake.render.pipeline.PipelineVariant
import io.github.awakelab.awake.render.pipeline.BindingSemantic
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
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real-Vulkan-headless proof of the three primitives a content feature is assembled from:
 * [VertexFormat.None] (no vertex buffer), `PipelineSpec.uniforms` (a uniform block the pipeline
 * owns rather than a material's), and [PipelineVariant.Background] (depth test and write off).
 *
 * The gate for phase 3 of docs/tasks/2026-08-23-backend-content-split-plan.md, which rebuilds the
 * skybox on exactly these. That conversion's failure mode is silent -- a misbound descriptor slot
 * compiles clean and draws nothing -- so this asserts pixels, not a successful build.
 *
 * Not skybox.wgsl: a GPU backend must not carry content even as a test fixture
 * (docs/reference/render-extensibility.md). `background_probe.wgsl` exercises the same mechanism
 * without naming a sky.
 *
 * Three assertions, chosen so none can pass while the pipeline is broken:
 * - the top row is the declared top colour, and the bottom row the declared bottom one, so a
 *   uniform block that is misbound or packed in the wrong order fails;
 * - the two differ, so a flat fill of one wrong-but-uniform colour fails;
 * - neither is the clear colour, so a pipeline that never drew fails rather than reading the
 *   cleared attachment and passing.
 */
class RendererHeadlessBackgroundPipelineTest {

    @Test
    fun aVertexlessPipelineFillsTheFrameFromItsOwnUniformBlock() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            val pixels = renderer.renderBackground(target)

            val top = pixels.pixelAt(CENTER, TOP_ROW)
            val bottom = pixels.pixelAt(CENTER, BOTTOM_ROW)

            assertTrue(
                top.isNear(TOP_EXPECTED),
                "The top of the frame should be the block's topColor $TOP_EXPECTED, but was " +
                    "$top. A wrong colour here means the uniform block was misbound or written " +
                    "in the wrong field order -- the pipeline itself drew.",
            )
            assertTrue(
                bottom.isNear(BOTTOM_EXPECTED),
                "The bottom of the frame should be the block's bottomColor $BOTTOM_EXPECTED, " +
                    "but was $bottom.",
            )
            assertTrue(
                top != bottom,
                "Top and bottom are both $top -- a flat fill, so the gradient never sampled the " +
                    "block's second field and this assertion would pass for a half-bound buffer.",
            )
            assertTrue(
                !top.isNear(CLEAR_PIXEL) && !bottom.isNear(CLEAR_PIXEL),
                "The frame is still the clear colour $CLEAR_PIXEL, so the background pipeline " +
                    "recorded nothing: either VertexFormat.None bound a vertex buffer it has no " +
                    "attributes for, or the Scene feature never ran on the offscreen path.",
            )
        } finally {
            target.destroy()
        }
    }

    /** Renders with no draw calls at all -- whatever lands is the background pipeline's output. */
    private fun Renderer.renderBackground(target: RenderTarget): ByteArray {
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
        /** Every channel within [CHANNEL_TOLERANCE]; the slack absorbs the gradient's own ramp
         * across the sampled row and 8-bit rounding, nothing wider. */
        fun isNear(expected: Pixel) =
            kotlin.math.abs(red - expected.red) <= CHANNEL_TOLERANCE &&
                kotlin.math.abs(green - expected.green) <= CHANNEL_TOLERANCE &&
                kotlin.math.abs(blue - expected.blue) <= CHANNEL_TOLERANCE
    }

    /**
     * The feature under test: binds the background pipeline and draws the generated triangle,
     * having filled the pipeline's own block. The same three calls `SharedSkyboxRenderFeature`
     * makes, without the sky's uniform math.
     */
    private class BackgroundProbeFeature(
        private val pipeline: RenderPipeline,
    ) : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) {
            val block: UniformBlock = checkNotNull(pipeline.uniformBlock) {
                "The probe pipeline was built from a spec with non-null uniforms, so the factory " +
                    "must have allocated a block."
            }
            block.write(context.frameIndex) {
                put(BOTTOM_FIELD, BOTTOM_COLOR)
                put(TOP_FIELD, TOP_COLOR)
            }
            val recorder: CommandRecorder = context.recorder
            recorder.bindPipeline(pipeline)
            recorder.bindMaterial(BindingSemantic.Material, block.binding(context.frameIndex))
            recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
        }

        override fun destroy() = pipeline.destroy()
    }

    private companion object {
        private var cachedRenderer: Renderer? = null

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
            cachedDevice?.destroy()
            cachedDevice = null
        }

        val BOTTOM_FIELD = UniformField("bottomColor", GpuDataShape.Vec4)
        val TOP_FIELD = UniformField("topColor", GpuDataShape.Vec4)
        val PROBE_LAYOUT = UniformLayout(BOTTOM_FIELD, TOP_FIELD)

        val BOTTOM_COLOR = Color(r = 1f, g = 0f, b = 0f, a = 1f)
        val TOP_COLOR = Color(r = 0f, g = 0f, b = 1f, a = 1f)
        val BOTTOM_EXPECTED = Pixel(255, 0, 0)
        val TOP_EXPECTED = Pixel(0, 0, 255)

        /** `Renderer`'s default clear -- distinct from both probe colours on every channel, so
         * "still cleared" can never be mistaken for "drew correctly". */
        val CLEAR_PIXEL = Pixel(0, 0, 0)

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val CENTER = TARGET_SIZE / 2
        const val CHANNEL_TOLERANCE = 6
        const val FULLSCREEN_TRIANGLE_VERTICES = 3
        const val EYE_Z = 3f

        /** One row in from each edge -- the exact edge row can land on the gradient's clamp. */
        const val TOP_ROW = 1
        const val BOTTOM_ROW = TARGET_SIZE - 2

        fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)

            // The primary pipeline exists only because Renderer requires one; nothing draws
            // through it here, since the test issues no draw calls.
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
            val background = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                runBlocking { packShaderPair("background_probe") },
                VertexFormat.None,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                variant = PipelineVariant.Background,
                uniforms = PROBE_LAYOUT,
                framesInFlight = MAX_FRAMES_IN_FLIGHT,
            )
            val transferContext = TransferContext(graphicsDevice)
            cachedCleanup = headlessCleanup(
                graphicsDevice,
                transferContext,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                primary,
            )
            return Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = primary,
                    primaryFormat = VertexFormat.PositionColorUv,
                ),
                renderFeatures = listOf(BackgroundProbeFeature(background)),
                transferContext = transferContext,
                uiShaderPairs = runBlocking {
                    UiShaderPairs(
                        quad = packShaderPair("ui_quad"),
                        glyph = packShaderPair("ui_glyph"),
                        texture = packShaderPair("ui_texture"),
                        roundedQuad = packShaderPair("ui_rounded_quad"),
                    )
                },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            ).also { cachedRenderer = it }
        }

    }
}
