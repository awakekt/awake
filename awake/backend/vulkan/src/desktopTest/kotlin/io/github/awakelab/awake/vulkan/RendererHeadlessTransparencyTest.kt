/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import io.github.awakelab.awake.engine.compose.GraphicsLayerCompositor
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.core.graphics2d.DrawShape
import io.github.awakelab.awake.core.graphics2d.toPath
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.passes.OpaqueRenderFeature
import io.github.awakelab.awake.render.passes2d.UiRenderFeature
import io.github.awakelab.awake.render.pipeline.PipelineVariant
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.UiTargetCompositeMode
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.debug.LineRenderPipeline
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.pipeline.PipelineTable
import io.github.awakelab.awake.vulkan.pipeline.RenderPipeline
import io.github.awakelab.awake.vulkan.pipeline.ShaderPair
import io.github.awakelab.awake.vulkan.pipeline.UiShaderPairs
import io.github.awakelab.awake.vulkan.pipeline.VulkanLinePass
import io.github.awakelab.awake.vulkan.pipeline.VulkanUiPass
import io.github.awakelab.awake.vulkan.pipeline.createSceneRenderPass
import io.github.awakelab.awake.vulkan.renderer.Renderer
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.awakelab.awake.render.material.Material as RenderMaterial
import io.github.awakelab.awake.render.mesh.Mesh as RenderMesh

/**
 * Proves a `DrawCall.transparent` draw actually resolves to the alpha-blended pipeline and
 * blends against what is already in the colour buffer -- the one property nothing else checks.
 *
 * The whole transparency path (`MeshRenderer.transparent` -> `DrawCall.transparent` ->
 * `PipelineTable.transparentByFormat` -> `Renderer.pipelineFor`) was built without any scene
 * setting the flag, so every piece of it was unexercised. A wrong pipeline choice here is
 * invisible in every other test: the draw still renders, in the right place, at the right size
 * -- only unblended.
 *
 * Uses `transparent_probe.wgsl` rather than `triangle.wgsl` for a specific reason: that shader
 * returns a hardcoded alpha of 1.0, and straight-alpha blending at alpha 1 is arithmetically a
 * no-op, so a blend assertion written against it would pass identically whether the transparent
 * pipeline was selected or never built at all. The probe takes its alpha from `uv.x`, so one
 * shader covers both the opaque backdrop (uv.x = 1) and the half-transparent overlay
 * (uv.x = 0.5) in a single frame.
 *
 * Vulkan only. WebGPU has no headless render harness, so its own transparent pipeline -- added
 * alongside this test -- stays visually verified rather than covered here.
 *
 * The overlap pixel is asserted against the exact arithmetic midpoint: a 0.5-alpha red over
 * opaque blue measures (128, 0, 128), so the blend lands in the same space the vertex colours
 * were written in, with no transfer curve applied. Dropping the transparent pipeline from the
 * table instead measures (255, 0, 0) -- pure overlay, no backdrop -- which is what this test
 * was falsified against before being committed.
 */
class RendererHeadlessTransparencyTest {

    @Test
    fun compositorBlursAGenericShapeMaskWithoutFillingItsCorner() = withHeadlessRenderer { renderer ->
        val output = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val compositor = GraphicsLayerCompositor()
        try {
            val circle = DrawShape.Circle.toPath(Rectangle(0f, 0f, 16f, 16f))
            val primitives = compositor.composite(
                renderer = renderer,
                primitives = listOf(UiDrawPrimitive.Texture(21f, 21f, 22f, 22f, GraphicsLayerPlaceholder(1), premultiplied = true)),
                layers = listOf(
                    GraphicsLayerFrame(
                        id = 1, x = 24f, y = 24f, width = 16, height = 16, alpha = 1f,
                        blurRadiusX = 3f, blurRadiusY = 3f, effectInsetX = 3, effectInsetY = 3,
                        primitives = listOf(UiDrawPrimitive.FilledPath(circle, Color(0f, 0f, 0f, 0.25f))),
                    ),
                ),
                font = UiFonts.default(),
                viewportWidth = TARGET_SIZE,
                viewportHeight = TARGET_SIZE,
            )
            renderer.drawUiToTexture(output, primitives)
            val pixels = runBlocking { renderer.readPixels(output) }.data

            assertTrue(pixels.alphaAt(21, 21) == 0, "circle-mask corner must stay transparent")
            assertTrue(pixels.alphaAt(21, 32) > 0, "blurred circle edge must carry shadow alpha")
        } finally {
            compositor.dispose()
            output.destroy()
        }
    }

    @Test
    fun destroyedRenderTargetUnregistersFromRenderer() = withHeadlessRenderer { renderer ->
        val target = renderer.createRenderTarget(8, 8)

        target.destroy()

        assertEquals(0, renderer.createdRenderTargets.size)
    }

    @Test
    fun sampledUiTargetCompositeDistinguishesScreenAndOverlay() = withHeadlessRenderer { renderer ->
        val destination = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val source = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val screen = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val overlay = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.drawUiToTexture(destination, listOf(UiDrawPrimitive.Quad(0f, 0f, TARGET_SIZE.toFloat(), TARGET_SIZE.toFloat(), Color(0f, 0f, 1f, 1f))))
            renderer.drawUiToTexture(source, listOf(UiDrawPrimitive.Quad(0f, 0f, TARGET_SIZE.toFloat(), TARGET_SIZE.toFloat(), Color(1f, 0f, 0f, 1f))))
            renderer.compositeUiTargets(destination, source, screen, UiTargetCompositeMode.Screen)
            renderer.compositeUiTargets(destination, source, overlay, UiTargetCompositeMode.Overlay)

            val screenPixel = runBlocking { renderer.readPixels(screen) }.data.pixelAt(CENTER, CENTER)
            val overlayPixel = runBlocking { renderer.readPixels(overlay) }.data.pixelAt(CENTER, CENTER)
            assertTrue(screenPixel.red >= 253 && screenPixel.green <= CHANNEL_TOLERANCE && screenPixel.blue >= 253, "screen was $screenPixel")
            assertTrue(overlayPixel.red <= CHANNEL_TOLERANCE && overlayPixel.green <= CHANNEL_TOLERANCE && overlayPixel.blue >= 253, "overlay was $overlayPixel")
        } finally {
            destination.destroy()
            source.destroy()
            screen.destroy()
            overlay.destroy()
        }
    }

    @Test
    fun weightedTexturePassBlursAnIsolatedLayer() = withHeadlessRenderer { renderer ->
        val source = renderer.createRenderTarget(16, 16)
        val blurred = renderer.createRenderTarget(16, 16)
        var material: RenderMaterial? = null
        try {
            renderer.drawUiToTexture(
                source,
                listOf(UiDrawPrimitive.Quad(6f, 6f, 4f, 4f, Color(1f, 0f, 0f, 1f))),
            )
            val sourceMaterial = renderer.createMaterial(renderTarget = source).also { material = it }
            val offsets = listOf(
                Triple(-2f, -2f, 1f / 16f), Triple(0f, -2f, 2f / 16f), Triple(2f, -2f, 1f / 16f),
                Triple(-2f, 0f, 2f / 16f), Triple(0f, 0f, 4f / 16f), Triple(2f, 0f, 2f / 16f),
                Triple(-2f, 2f, 1f / 16f), Triple(0f, 2f, 2f / 16f), Triple(2f, 2f, 1f / 16f),
            )
            renderer.drawUiToTexture(
                blurred,
                offsets.map { (x, y, weight) ->
                    UiDrawPrimitive.Texture(
                        x = x,
                        y = y,
                        w = 16f,
                        h = 16f,
                        material = sourceMaterial,
                        alpha = weight,
                        blendMode = BlendMode.Plus,
                    )
                },
            )
            val pixels = runBlocking { renderer.readPixels(blurred) }.data
            val blurredEdge = pixels.alphaAt(4, 8, width = 16)
            assertTrue(
                blurredEdge in 46..50,
                "the 3x3 weighted pass must spread alpha outside the source square, was $blurredEdge",
            )
        } finally {
            material?.destroy()
            source.destroy()
            blurred.destroy()
        }
    }

    @Test
    fun textureCompositeUsesTheRequestedBlendMode() = withHeadlessRenderer { renderer ->
        val layer = renderer.createRenderTarget(8, 8)
        val sourceOver = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val plus = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        var material: RenderMaterial? = null
        try {
            renderer.drawUiToTexture(
                layer,
                listOf(UiDrawPrimitive.Quad(0f, 0f, 8f, 8f, Color(1f, 0f, 0f, 1f))),
            )
            val layerMaterial = renderer.createMaterial(renderTarget = layer).also { material = it }
            fun primitives(blendMode: BlendMode) = listOf(
                UiDrawPrimitive.Quad(0f, 0f, TARGET_SIZE.toFloat(), TARGET_SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                UiDrawPrimitive.Texture(
                    x = CENTER - 4f,
                    y = CENTER - 4f,
                    w = 8f,
                    h = 8f,
                    material = layerMaterial,
                    alpha = 0.5f,
                    blendMode = blendMode,
                    premultiplied = true,
                ),
            )
            renderer.drawUiToTexture(sourceOver, primitives(BlendMode.SourceOver))
            renderer.drawUiToTexture(plus, primitives(BlendMode.Plus))

            val sourceOverPixel = runBlocking { renderer.readPixels(sourceOver) }.data.pixelAt(CENTER, CENTER)
            val plusPixel = runBlocking { renderer.readPixels(plus) }.data.pixelAt(CENTER, CENTER)
            assertTrue(sourceOverPixel.isHalfway(RED_OVER_BLUE), "source-over should be purple, was $sourceOverPixel")
            assertTrue(
                plusPixel.red in 126..130 && plusPixel.green <= CHANNEL_TOLERANCE && plusPixel.blue >= 253,
                "plus should add red without dimming blue, was $plusPixel",
            )
        } finally {
            material?.destroy()
            layer.destroy()
            sourceOver.destroy()
            plus.destroy()
        }
    }

    @Test
    fun graphicsLayerTextureAppliesAlphaOnceAndRotatesOnTheGpu() = withHeadlessRenderer { renderer ->
        val layer = renderer.createRenderTarget(20, 20)
        val output = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        var material: RenderMaterial? = null
        try {
            // Two opaque children overlap in the layer. Applying alpha per child would make the
            // overlap more opaque than the single-child area; compositing the captured texture
            // once must leave both samples at the same alpha.
            renderer.drawUiToTexture(
                layer,
                listOf(
                    UiDrawPrimitive.Quad(0f, 0f, 20f, 20f, Color(1f, 0f, 0f, 1f)),
                    UiDrawPrimitive.Quad(8f, 0f, 12f, 20f, Color(1f, 0f, 0f, 1f)),
                ),
            )
            val layerMaterial = renderer.createMaterial(renderTarget = layer).also { material = it }
            renderer.drawUiToTexture(
                output,
                listOf(
                    UiDrawPrimitive.Texture(
                        x = 22f,
                        y = 22f,
                        w = 20f,
                        h = 20f,
                        material = layerMaterial,
                        alpha = 0.5f,
                        rotationDegrees = 45f,
                    ),
                ),
            )
            val pixels = runBlocking { renderer.readPixels(output) }.data

            // Centre is an overlap; (19,32) is outside the original 22..42 square but inside its
            // 45-degree rotated footprint. Both must retain the single composite alpha.
            val layerCenter = 32
            val overlapAlpha = pixels.alphaAt(layerCenter, layerCenter)
            val rotatedOnlyAlpha = pixels.alphaAt(19, layerCenter)
            assertTrue(overlapAlpha in 120..136, "layer alpha should be applied once, was $overlapAlpha")
            assertTrue(rotatedOnlyAlpha in 120..136, "rotation should extend the sampled texture, was $rotatedOnlyAlpha")
        } finally {
            material?.destroy()
            layer.destroy()
            output.destroy()
        }
    }

    @Test
    fun transparentDrawBlendsAgainstTheOpaqueDrawBehindIt() = withHeadlessRenderer { renderer ->
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val pixels = renderer.renderQuads(target, overlayZ = NEAR_Z)

        val overlap = pixels.pixelAt(CENTER, CENTER)
        val backdropOnly = pixels.pixelAt(BACKDROP_ONLY_X, CENTER)

        assertTrue(
            backdropOnly.blue > STRONG && backdropOnly.red < FAINT,
            "sanity: the backdrop-only sample should be the blue quad alone, was $backdropOnly. " +
                "If this fails the sampled coordinates no longer match the quad sizes, and the " +
                "blend assertion below would be reading the wrong pixel.",
        )
        assertTrue(
            overlap.isHalfway(RED_OVER_BLUE),
            "a transparent draw must blend with the opaque quad behind it, so the overlap pixel " +
                "should be the even mix $RED_OVER_BLUE, but was $overlap. A pure red " +
                "(255, 0, 0) means the draw resolved to the opaque pipeline instead of " +
                "PipelineTable.transparentByFormat; anything else means the blend factors " +
                "are no longer straight src-alpha.",
        )
    }

    /**
     * Depth *test* stays on for a transparent draw -- only depth *write* is disabled. A
     * transparent surface behind opaque geometry is still hidden by it, which is what stops
     * `depthWriteEnabled = false` from turning into "transparent draws ignore depth entirely".
     */
    @Test
    fun transparentDrawBehindOpaqueGeometryIsStillDepthTestedAway() =
        withHeadlessRenderer { renderer ->
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            val pixels = renderer.renderQuads(target, overlayZ = BEHIND_Z)

            val overlap = pixels.pixelAt(CENTER, CENTER)
            assertTrue(
                overlap.red < FAINT,
                "the transparent quad sits behind the opaque one, so depth test should reject it " +
                    "entirely and leave the backdrop untouched, but the overlap pixel was " +
                    "$overlap -- red present means depth testing was disabled along with " +
                    "depth writing.",
            )
        }

    /** Renders the blue opaque backdrop, then the half-transparent red overlay at [overlayZ]. */
    private fun Renderer.renderQuads(
        target: RenderTarget,
        overlayZ: Float,
    ): ByteArray {
        var backdropMesh: RenderMesh? = null
        var overlayMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val backdrop = createMesh(quad(BACKDROP_HALF, FAR_Z, BLUE, alpha = 1f))
                .also { backdropMesh = it }
            val overlay = createMesh(quad(OVERLAY_HALF, overlayZ, RED, alpha = 0.5f))
                .also { overlayMesh = it }
            val sharedMaterial = createMaterial().also { material = it }
            renderToTexture(
                target,
                Lens(
                    eye = Vec3f(0f, 0f, EYE_Z),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 10f,
                ),
                listOf(
                    DrawCall(backdrop, sharedMaterial),
                    DrawCall(overlay, sharedMaterial, transparent = true),
                ),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            backdropMesh?.destroy()
            overlayMesh?.destroy()
            material?.destroy()
        }
    }

    private data class Pixel(val red: Int, val green: Int, val blue: Int) {
        /** Every channel within [CHANNEL_TOLERANCE] of [expected] -- the slack absorbs the
         * rounding of a half-alpha blend into 8-bit channels, nothing wider. */
        fun isHalfway(expected: Pixel) =
            kotlin.math.abs(red - expected.red) <= CHANNEL_TOLERANCE &&
                kotlin.math.abs(green - expected.green) <= CHANNEL_TOLERANCE &&
                kotlin.math.abs(blue - expected.blue) <= CHANNEL_TOLERANCE
    }

    private fun ByteArray.pixelAt(x: Int, y: Int): Pixel {
        val offset = (y * TARGET_SIZE + x) * BYTES_PER_PIXEL
        return Pixel(
            this[offset].toInt() and 0xFF,
            this[offset + 1].toInt() and 0xFF,
            this[offset + 2].toInt() and 0xFF,
        )
    }

    private fun ByteArray.alphaAt(x: Int, y: Int, width: Int = TARGET_SIZE): Int =
        this[(y * width + x) * BYTES_PER_PIXEL + 3].toInt() and 0xFF


    /**
     * A camera-facing square of side `2 * halfSize` at [z], as `VertexFormat.PositionColorUv`
     * (position vec3, colour vec3, uv vec2). [alpha] rides in `uv.x`, which is what
     * `transparent_probe.wgsl` returns as the fragment's alpha.
     */
    private fun quad(halfSize: Float, z: Float, color: Triple<Float, Float, Float>, alpha: Float) =
        MeshGeometry(
            floatArrayOf(
                -halfSize, -halfSize, z, color.first, color.second, color.third, alpha, 0f,
                halfSize, -halfSize, z, color.first, color.second, color.third, alpha, 0f,
                halfSize, halfSize, z, color.first, color.second, color.third, alpha, 1f,
                -halfSize, halfSize, z, color.first, color.second, color.third, alpha, 1f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
        )

    /**
     * The shared headless renderer, built once per JVM.
     *
     * Not torn down between tests, and deliberately so: `RendererHeadlessUiGlyphBaselineTest`'s
     * own fixture documents that destroying and recreating a validation-enabled headless
     * instance twice in one JVM is flaky on MoltenVK, and this test hit exactly that -- the
     * second test's `vkCreateInstance` failed with `VK_ERROR_EXTENSION_NOT_PRESENT`. That
     * fixture can't be reused directly here: its `PipelineTable` has no
     * [PipelineTable.transparentByFormat] entry, which is the thing under test.
     */
    private fun withHeadlessRenderer(block: (Renderer) -> Unit) = block(sharedRenderer())

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

        /**
         * The primary pipeline plus the alpha-blended companion registered under
         * [VertexFormat.PositionColorUv] -- the entry `Renderer.pipelineFor` looks up for a
         * transparent draw. Both are built from `transparent_probe.wgsl`, so the only
         * difference between them is the [PipelineVariant].
         */
        fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val probeShaders = runBlocking { packShaderPair("transparent_probe") }
            fun buildPipeline(variant: PipelineVariant) = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                probeShaders,
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                variant = variant,
            )
            val lineRenderPipeline = LineRenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                runBlocking { packShaderPair("debug_line") },
                MAX_FRAMES_IN_FLIGHT,
            )
            val opaque = buildPipeline(PipelineVariant.Opaque)
            val transparent = buildPipeline(PipelineVariant.AlphaBlended)
            val transferContext = TransferContext(graphicsDevice)
            cachedCleanup = headlessCleanup(
                graphicsDevice,
                transferContext,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                opaque,
                transparent,
            )
            return Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = opaque,
                    primaryFormat = VertexFormat.PositionColorUv,
                    transparentByFormat = mapOf(VertexFormat.PositionColorUv to transparent),
                ),
                renderFeatures = listOf(
                    OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)),
                    UiRenderFeature(VulkanUiPass()),
                ),
                transferContext = transferContext,
                uiShaderPairs = runBlocking {
                    UiShaderPairs(
                        quad = packShaderPair("ui_quad"),
                        glyph = packShaderPair("ui_glyph"),
                        texture = packShaderPair("ui_texture"),
                        roundedQuad = packShaderPair("ui_rounded_quad"),
                        targetComposite = packShaderPair("ui_target_composite"),
                    )
                },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            ).also { cachedRenderer = it }
        }


        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val CENTER = TARGET_SIZE / 2

        /** Inside the backdrop quad's screen extent but outside the overlay's. */
        const val BACKDROP_ONLY_X = CENTER + 44

        const val EYE_Z = 3f
        const val FAR_Z = -0.5f
        const val NEAR_Z = 0.5f
        const val BEHIND_Z = -1.5f
        const val BACKDROP_HALF = 1.5f
        const val OVERLAY_HALF = 0.8f

        /** A channel this high can only come from a quad that actually painted. */
        const val STRONG = 128

        /** Above this a channel is a real contribution, not clear-colour or rounding. */
        const val FAINT = 24

        const val CHANNEL_TOLERANCE = 2

        /** Half-alpha red composited over opaque blue, measured on MoltenVK. */
        val RED_OVER_BLUE = Pixel(128, 0, 128)

        val BLUE = Triple(0f, 0f, 1f)
        val RED = Triple(1f, 0f, 0f)
    }
}
