/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2dArray
import com.awakekt.awake.asset.shaderdsl.textureSampleArrayLevel
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.application.VulkanContentFeatureGpu
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.VulkanPipelineFactory
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Content features attached to a running renderer, through the engine's own [VulkanContentFeatureGpu]
 * and a real pipeline registry: a terrain appears on attach, is gone after detach, and comes back
 * identically when attached again.
 */
class RendererHeadlessContentAttachTest {

    @Test
    fun anAttachedTerrainDrawsUntilItIsDetached() {
        val attacher = shared().attacher
        assertEquals(0, render().covered, "Nothing is attached yet, so the frame is only the clear colour.")

        val terrain = runBlocking { attacher.attachContentFeature(terrainSource()) }
        val attached = render().covered
        terrain.detach()
        val detached = render().covered
        val again = runBlocking { attacher.attachContentFeature(terrainSource()) }
        val reattached = render().covered
        again.detach()

        assertTrue(attached > MIN_COVERED, "The attached terrain covered only $attached pixels.")
        assertEquals(0, detached, "The detached terrain still drew $detached pixels.")
        assertEquals(attached, reattached, "A re-attached terrain should draw exactly as before.")
    }

    @Test
    fun aSecondTerrainWithTheSameSpecIsRejected() {
        val attacher = shared().attacher
        val terrain = runBlocking { attacher.attachContentFeature(terrainSource()) }
        try {
            assertFailsWith<IllegalArgumentException> {
                runBlocking { attacher.attachContentFeature(terrainSource()) }
            }
        } finally {
            terrain.detach()
        }
    }

    /**
     * An arrayed content texture reaches the GPU with all its layers. The surface samples layer 1
     * only, so a single-layer upload of the same bytes cannot produce its colour.
     */
    @Test
    fun anArrayedSurfaceTextureUploadsEveryLayer() {
        val attacher = shared().attacher
        val surface = runBlocking {
            attacher.attachContentFeature(
                terrainContentFeature(
                    SECOND_LAYER_SURFACE,
                    dome(),
                    CONFIG,
                    surfaceTextures = mapOf(TERRAIN_SURFACE_FIRST_BINDING to TWO_LAYERS),
                ),
            )
        }
        val frame = try {
            render()
        } finally {
            surface.detach()
        }

        assertTrue(frame.covered > MIN_COVERED, "The surface covered only ${frame.covered} pixels.")
        assertEquals(frame.covered, frame.green, "Every covered pixel should be layer 1's green.")
    }

    private class Frame(val covered: Int, val green: Int)

    private fun render(): Frame {
        val renderer = shared().renderer
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
            return count(runBlocking { renderer.readPixels(target) }.data)
        } finally {
            target.destroy()
        }
    }

    /** Non-clear pixels, and how many of them are layer 1's green. */
    private fun count(pixels: ByteArray): Frame {
        var covered = 0
        var green = 0
        for (offset in pixels.indices step BYTES_PER_PIXEL) {
            val r = pixels[offset].toInt() and 0xFF
            val g = pixels[offset + 1].toInt() and 0xFF
            val b = pixels[offset + 2].toInt() and 0xFF
            if (r + g + b <= CLEAR_TOLERANCE) continue
            covered++
            if (g > GREEN_MIN && r < OTHER_MAX && b < OTHER_MAX) green++
        }
        return Frame(covered, green)
    }

    private class Fixture(val renderer: Renderer, val attacher: ContentFeatureAttacher<RenderPipeline>)

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
        const val MIN_COVERED = 100
        const val GREEN_MIN = 200
        const val OTHER_MAX = 40

        /** A radial dome, so the terrain covers a region a camera above it can see. */
        fun dome() = Heightmap(
            samples = FloatArray(SAMPLES * SAMPLES) { index ->
                val x = (index % SAMPLES - SAMPLES / 2f) / (SAMPLES / 2f)
                val z = (index / SAMPLES - SAMPLES / 2f) / (SAMPLES / 2f)
                (1f - (x * x + z * z)).coerceAtLeast(0f)
            },
            width = SAMPLES,
            depth = SAMPLES,
            scale = Vec3f(1f, HEIGHT_SCALE, 1f),
        )

        fun terrainSource(): ContentFeatureSource = terrainContentFeature(PackShaderSets.Terrain, dome(), CONFIG)

        /** Layer 0 red, layer 1 green, 2x2 each. */
        val TWO_LAYERS = TextureAsset(
            data = ByteArray(2 * 2 * 4 * 2) { index ->
                val layer = index / (2 * 2 * 4)
                when (index % 4) {
                    0 -> if (layer == 0) -1 else 0
                    1 -> if (layer == 1) -1 else 0
                    3 -> -1
                    else -> 0
                }
            },
            width = 2,
            height = 2,
            layerCount = 2,
        )

        /** Paints array layer 1 flat. Reads the normal only because ASL rejects an unread varying. */
        val SECOND_LAYER_SURFACE = aslShaderSet(
            shader("terrain_second_layer") {
                val terrain = terrainClipmapVertexStage(exportWorldPosition = false)
                val group = BindingLayout.Standard.slot(BindingSemantic.Material)
                val layers by texture2dArray(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING)
                val layerSampler by sampler(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING + 1)
                fragment {
                    val albedo = let(
                        "albedo",
                        textureSampleArrayLevel(layers, layerSampler, vec2(0.5f.lit, 0.5f.lit), 1.lit, 0f.lit),
                    )
                    val opaque = let("opaque", max(normalize(terrain.worldNormal).y, 1f.lit))
                    colorOutput(vec4(albedo.xyz, opaque))
                }
            },
        )

        private var fixture: Fixture? = null
        private var cachedDevice: GraphicsDevice? = null
        private var cachedCleanup: (() -> Unit)? = null

        fun shared(): Fixture {
            fixture?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val material = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val transferContext = TransferContext(graphicsDevice)
            val registry = PipelineRegistry(
                VulkanPipelineFactory(
                    graphicsDevice = graphicsDevice,
                    swapchainManager = swapchainManager,
                    renderPass = sceneRenderPass,
                    descriptorSetLayout = material.descriptorSetLayout,
                    framesInFlight = MAX_FRAMES_IN_FLIGHT,
                    loadShaders = { spec ->
                        NagaShaderCompiler.wgslToSpirv(spec.vertexShader.resolveBytes().decodeToString())
                            .let { ShaderPair(it, it) }
                    },
                ),
            )
            val attacher = ContentFeatureAttacher(VulkanContentFeatureGpu(graphicsDevice, transferContext, registry))

            // Required by Renderer; nothing draws through it, since the test issues no draw calls.
            val primary = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                material.descriptorSetLayout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
                uiShaderPairs = runBlocking { defaultUiShaderPairs() },
                transferContext = transferContext,
                renderFeatures = listOf(attacher.beforeGeometry, attacher.afterGeometry),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            )
            val cleanup = headlessCleanup(
                graphicsDevice,
                transferContext,
                sceneRenderPass,
                material.descriptorSetLayout,
                primary,
            )
            cachedCleanup = {
                registry.destroyAll { it.destroy() }
                attacher.releaseAll()
                cleanup()
            }
            return Fixture(renderer, attacher).also { fixture = it }
        }

        @AfterClass
        @JvmStatic
        fun releaseSharedRenderer() {
            fixture?.renderer?.destroy()
            fixture = null
            cachedCleanup?.invoke()
            cachedCleanup = null
            cachedDevice?.destroy()
            cachedDevice = null
        }
    }
}
