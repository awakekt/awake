/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.asset.shadercompiler.NagaShaderCompiler
import io.github.awakelab.awake.asset.shaderdsl.bindingsForGroup
import io.github.awakelab.awake.asset.shaderpack.TerrainRenderFeature
import io.github.awakelab.awake.asset.shaderpack.TerrainShader
import io.github.awakelab.awake.asset.shaderpack.TerrainUniformLayout
import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapConfig
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapTracker
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.passes.ContentGeometry
import io.github.awakelab.awake.render.passes.RenderFeature
import io.github.awakelab.awake.render.passes.RenderFrameContext
import io.github.awakelab.awake.render.passes.RenderPassSlot
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.mesh.Mesh
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
import kotlin.test.assertTrue

/**
 * D28's first pixels: a clipmap heightfield, drawn.
 *
 * Everything before this was plumbing verified by not regressing. This runs the real
 * [TerrainRenderFeature] against a real lavapipe device, so the whole chain has to hold at once
 * -- ASL declares the bindings, the descriptor set is derived from them, the heightmap is
 * uploaded and sampled **in the vertex stage**, the merged clipmap's per-vertex ring tags index
 * the uniform array, and the displaced geometry rasterises.
 *
 * **Why coverage rather than colour.** `TerrainClipmapGeometry` gives every vertex the same
 * upward normal, so displacement moves geometry without changing its shading -- every terrain
 * pixel is one grey. Asserting a colour would therefore pass on a shader that ignored the
 * heightmap entirely. What displacement does change is which pixels the terrain covers, so the
 * test raises one heightmap and compares covered-pixel counts against a flat one.
 */
class RendererHeadlessTerrainTest {

    @Test
    fun aRaisedHeightmapCoversMorePixelsThanAFlatOne() {
        val flat = renderTerrain(raised = false).covered
        val raised = renderTerrain(raised = true).covered

        assertTrue(
            flat > 0,
            "Flat terrain covered no pixels at all, so the clipmap never drew -- this is a bind " +
                "or draw failure, not a displacement one.",
        )
        // A margin, not just inequality: two counts differing by a pixel or two would also
        // satisfy `!=` while the heightmap was effectively ignored.
        assertTrue(
            kotlin.math.abs(raised - flat) > flat / MIN_COVERAGE_SHIFT,
            "Raised terrain covered $raised pixels and flat covered $flat -- closer than a " +
                "${HEIGHT_SCALE}-unit displacement across half the map should manage. The " +
                "vertex stage is reading the heightmap weakly or not at all.",
        )
    }

    /**
     * Normals are derived from the heightmap's own gradient, so a slope must light differently
     * from level ground. Flat terrain has one normal everywhere and therefore one shade; if the
     * raised one also renders a single shade, the gradient is not reaching the lighting -- which
     * is exactly the state this replaced, and which the coverage test above cannot see.
     */
    @Test
    fun aSlopeShadesDifferentlyFromLevelGround() {
        val flat = renderTerrain(raised = false)
        val raised = renderTerrain(raised = true)

        assertTrue(
            flat.shades.size == 1,
            "Flat terrain rendered ${flat.shades.size} distinct shades, expected exactly one -- " +
                "every normal there is straight up, so anything else means the lighting is " +
                "reading something other than the surface.",
        )
        assertTrue(
            raised.shades.size > 1,
            "Domed terrain rendered one shade ${raised.shades} across ${raised.covered} " +
                "pixels, so its normals are still constant -- the heightmap gradient is not " +
                "reaching worldNormal, or the light sign is inverted so everything clamps to " +
                "ambient.",
        )
    }

    private class Rendered(val covered: Int, val shades: Set<Int>)

    /** Non-clear pixels in one rendered frame of the selected terrain, and the distinct
     * greys among them. */
    private fun renderTerrain(raised: Boolean): Rendered {
        val renderer = sharedRenderer()
        selectedFeature = if (raised) raisedFeature else flatFeature
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.renderToTexture(
                target,
                Lens(
                    eye = Vec3f(0f, EYE_HEIGHT, EYE_DISTANCE),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 500f,
                ),
                emptyList(),
            )
            val pixels = runBlocking { renderer.readPixels(target) }.data
            var covered = 0
            val shades = mutableSetOf<Int>()
            var offset = 0
            while (offset < pixels.size) {
                val r = pixels[offset].toInt() and 0xFF
                val g = pixels[offset + 1].toInt() and 0xFF
                val b = pixels[offset + 2].toInt() and 0xFF
                if (r + g + b > CLEAR_TOLERANCE) {
                    covered++
                    // Bucketed: 8-bit rounding across a large surface would otherwise report
                    // dozens of "distinct" shades that are one quantisation step apart.
                    shades += r / SHADE_BUCKET
                }
                offset += BYTES_PER_PIXEL
            }
            return Rendered(covered, shades)
        } finally {
            target.destroy()
        }
    }

    private companion object {
        val CONFIG = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)

        const val SAMPLES = 16
        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val CLEAR_TOLERANCE = 12
        const val EYE_HEIGHT = 6f
        const val EYE_DISTANCE = 18f
        const val HEIGHT_SCALE = 8f

        /** Coverage must shift by at least this fraction of the flat baseline. */
        const val MIN_COVERAGE_SHIFT = 10

        /** Width of one shade bucket, so 8-bit rounding does not read as real variation. */
        const val SHADE_BUCKET = 8

        fun flatHeightmap() = Heightmap(
            samples = FloatArray(SAMPLES * SAMPLES),
            width = SAMPLES,
            depth = SAMPLES,
            scale = Vec3f(1f, HEIGHT_SCALE, 1f),
        )

        /**
         * A radial dome, not a step.
         *
         * A step's gradient is confined to the one texel where it flips, and a linear ramp has a
         * constant gradient -- both light as a single shade, so neither would show a derived
         * normal working. A dome's slope varies continuously across the whole surface, which is
         * the thing the fix produces.
         */
        fun raisedHeightmap() = Heightmap(
            samples = FloatArray(SAMPLES * SAMPLES) { index ->
                val x = (index % SAMPLES - SAMPLES / 2f) / (SAMPLES / 2f)
                val z = (index / SAMPLES - SAMPLES / 2f) / (SAMPLES / 2f)
                (1f - (x * x + z * z)).coerceAtLeast(0f)
            },
            width = SAMPLES,
            depth = SAMPLES,
            scale = Vec3f(1f, HEIGHT_SCALE, 1f),
        )

        private var cachedRenderer: Renderer? = null

        /** Content-feature uploads this fixture makes by hand, exactly as `VulkanEngine` does
         * for a real one -- and destroyed for the same reason it destroys its own: a pipeline's
         * descriptor set references a texture without owning it. */
        private val cachedUploads = mutableListOf<Texture>()

        /** The two terrain pipelines and the clipmap mesh, for the same reason: a
         * `TerrainRenderFeature` records with them without owning them, so its own `destroy` is
         * a no-op and nothing else frees them. */
        private val cachedPipelines = mutableListOf<RenderPipeline>()
        private var cachedMesh: Mesh? = null


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
            cachedPipelines.forEach { it.destroy() }
            cachedPipelines.clear()
            cachedMesh?.destroy()
            cachedMesh = null
            cachedDevice?.destroy()
            cachedDevice = null
        }
        private lateinit var flatFeature: TerrainRenderFeature
        private lateinit var raisedFeature: TerrainRenderFeature

        /** Which terrain [SwitchingTerrainFeature] records this frame. */
        private var selectedFeature: TerrainRenderFeature? = null

        /**
         * Records whichever terrain the current test selected.
         *
         * One device and one renderer for the whole class, matching every other headless test
         * here -- a second `GraphicsDevice` in the same process aborts the JVM. Two heightmaps
         * therefore mean two pipelines behind this switch, not two renderers.
         */
        private class SwitchingTerrainFeature : RenderFeature<RenderFrameContext> {
            override val pass = RenderPassSlot.Scene
            override fun recordCommands(context: RenderFrameContext) {
                selectedFeature?.recordCommands(context)
            }
            override fun destroy() = Unit
        }

        fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val transferContext = TransferContext(graphicsDevice)

            val materialGroup = BindingLayout.Standard.slot(BindingSemantic.Material)
            // Compiled from ASL at test time -- no committed .spv for terrain, and none needed.
            val terrainSpirv = NagaShaderCompiler.wgslToSpirv(TerrainShader.emitWgsl())
            val merged = TerrainClipmapGeometry.buildMergedClipmapMesh(CONFIG)
            val mesh = Mesh(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                merged.vertices,
                merged.indices,
                merged.format,
            ).also { cachedMesh = it }
            val geometry = ContentGeometry(mesh.vertexBinding, mesh.indexBinding, mesh.indexCount)

            fun terrainFeature(heightmap: Heightmap): TerrainRenderFeature {
                val pipeline = RenderPipeline(
                    graphicsDevice,
                    swapchainManager,
                    sceneRenderPass,
                    pipelineLayoutMaterial.descriptorSetLayout,
                    ShaderPair(terrainSpirv, terrainSpirv),
                    VertexFormat.PositionNormalColorUv,
                    vertexEntryPoint = "vertexMain",
                    fragmentEntryPoint = "fragmentMain",
                    uniforms = TerrainUniformLayout.Layout,
                    framesInFlight = MAX_FRAMES_IN_FLIGHT,
                    materialBindings = TerrainShader.bindingsForGroup(materialGroup),
                ).also { cachedPipelines += it }
                // The same uploads VulkanEngine performs for a real ContentFeature.
                val encoded = encodeHeightmap(heightmap)
                pipeline.writeContentTextures(
                    mapOf(
                        1 to Texture(
                            graphicsDevice,
                            transferContext::runOneTimeCommands,
                            encoded.pixels,
                            heightmap.width,
                            heightmap.depth,
                        ).also { cachedUploads += it },
                    ),
                )
                return TerrainRenderFeature(
                    pipeline = pipeline,
                    uniforms = checkNotNull(pipeline.uniformBlock),
                    geometry = geometry,
                    tracker = TerrainClipmapTracker(CONFIG),
                    heightScale = encoded.scale,
                    heightBias = encoded.bias,
                    sampling = encoded.sampling,
                )
            }

            flatFeature = terrainFeature(flatHeightmap())
            raisedFeature = terrainFeature(raisedHeightmap())

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
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
                renderFeatures = listOf(SwitchingTerrainFeature()),
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

        class EncodedHeights(
            val pixels: ByteArray,
            val scale: Float,
            val bias: Float,
            val sampling: FloatArray,
        )

        /** Mirrors `terrainContentFeature`'s own 16-bit split encoding -- see its
         * `EncodedHeightmap`, and `decodeHeight` for the shader half. */
        fun encodeHeightmap(heightmap: Heightmap): EncodedHeights {
            val samples = heightmap.copySamples()
            val min = samples.min()
            val range = samples.max() - min
            val pixels = ByteArray(samples.size * BYTES_PER_PIXEL)
            samples.forEachIndexed { index, sample ->
                val normalised = if (range == 0f) 0f else (sample - min) / range
                val quantised = (normalised * 0xFFFF).toInt().coerceIn(0, 0xFFFF)
                pixels[index * BYTES_PER_PIXEL] = (quantised ushr 8).toByte()
                pixels[index * BYTES_PER_PIXEL + 1] = (quantised and 0xFF).toByte()
                pixels[index * BYTES_PER_PIXEL + 3] = 0xFF.toByte()
            }
            return EncodedHeights(
                pixels,
                range * heightmap.scale.y,
                min * heightmap.scale.y,
                floatArrayOf(
                    (heightmap.width - 1) * heightmap.scale.x,
                    (heightmap.depth - 1) * heightmap.scale.z,
                    1f / heightmap.width,
                    1f / heightmap.depth,
                ),
            )
        }

    }
}
