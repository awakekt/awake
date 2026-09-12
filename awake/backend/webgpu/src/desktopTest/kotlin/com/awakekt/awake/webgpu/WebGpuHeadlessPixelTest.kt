/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.compose.GraphicsLayerCompositor
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.renderer.Renderer
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first test that renders pixels through the WebGPU backend and reads them back.
 *
 * Until now every WebGPU change was compile-checked and, at best, argued correct by symmetry
 * with Vulkan. That is how the divergences this render-hardware-interface work keeps uncovering
 * got in: WebGPU shipped without an alpha-blended pipeline for weeks, silently lacked the
 * mesh-clustering Vulkan had, and populated `PipelineTable.particlePipelines` where Vulkan left
 * it empty. None of those would fail a compile. All of them would fail a pixel.
 *
 * Runs on the desktop JVM, not in a browser -- see `WebGpuDesktopDeviceSmokeTest` for how and
 * why. **This exercises Awake's WebGPU code path over wgpu-native, NOT a browser's WebGPU
 * implementation.** Canvas sizing, JS interop, wasm memory and browser driver behaviour stay
 * uncovered.
 *
 * Uses its own `pixel_probe.wgsl` because this backend ships only UI and debug-line shaders; a
 * scene shader had to come from somewhere. It is unlit on purpose, so an asserted channel value
 * is the vertex colour and nothing else.
 */
class WebGpuHeadlessPixelTest {

    @Test
    fun compositorBlursAGenericShapeMaskWithoutFillingItsCorner() = withHeadlessRenderer { renderer ->
        val output = renderer.createRenderTarget(SIZE, SIZE)
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
                viewportWidth = SIZE,
                viewportHeight = SIZE,
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
        val destination = renderer.createRenderTarget(SIZE, SIZE)
        val source = renderer.createRenderTarget(SIZE, SIZE)
        val screen = renderer.createRenderTarget(SIZE, SIZE)
        val overlay = renderer.createRenderTarget(SIZE, SIZE)
        try {
            renderer.drawUiToTexture(
                destination,
                listOf(UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f))),
            )
            renderer.drawUiToTexture(
                source,
                listOf(UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(1f, 0f, 0f, 1f))),
            )
            renderer.compositeUiTargets(destination, source, screen, UiTargetCompositeMode.Screen)
            renderer.compositeUiTargets(destination, source, overlay, UiTargetCompositeMode.Overlay)

            val screenPixel = runBlocking { renderer.readPixels(screen) }.data.pixelAt(SIZE / 2, SIZE / 2)
            val overlayPixel = runBlocking { renderer.readPixels(overlay) }.data.pixelAt(SIZE / 2, SIZE / 2)
            assertTrue(
                screenPixel.red >= 253 && screenPixel.green <= FAINT && screenPixel.blue >= 253,
                "screen must sample both red source and blue destination, but was $screenPixel",
            )
            assertTrue(
                overlayPixel.red <= FAINT && overlayPixel.green <= FAINT && overlayPixel.blue >= 253,
                "overlay must use the blue destination as its base colour, but was $overlayPixel",
            )
        } finally {
            destination.destroy()
            source.destroy()
            screen.destroy()
            overlay.destroy()
        }
    }

    @Test
    fun texturedUiPrimitiveUsesSourceOverAndPlus() = withHeadlessRenderer { renderer ->
        val layer = renderer.createRenderTarget(8, 8)
        val sourceOver = renderer.createRenderTarget(SIZE, SIZE)
        val plus = renderer.createRenderTarget(SIZE, SIZE)
        var material: com.awakekt.awake.render.material.Material? = null
        try {
            renderer.drawUiToTexture(layer, listOf(UiDrawPrimitive.Quad(0f, 0f, 8f, 8f, Color(1f, 0f, 0f, 1f))))
            val layerMaterial = renderer.createMaterial(renderTarget = layer).also { material = it }
            fun primitives(blendMode: BlendMode) = listOf(
                UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                UiDrawPrimitive.Texture(
                    x = 28f,
                    y = 28f,
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

            val sourceOverPixel = runBlocking { renderer.readPixels(sourceOver) }.data.pixelAt(SIZE / 2, SIZE / 2)
            val plusPixel = runBlocking { renderer.readPixels(plus) }.data.pixelAt(SIZE / 2, SIZE / 2)
            assertTrue(
                sourceOverPixel.red in HALF_SRGB_RANGE && sourceOverPixel.green <= FAINT && sourceOverPixel.blue in HALF_SRGB_RANGE,
                "source-over should be purple, but was $sourceOverPixel",
            )
            assertTrue(
                plusPixel.red in HALF_SRGB_RANGE && plusPixel.green <= FAINT && plusPixel.blue >= 253,
                "plus should add red without dimming blue, but was $plusPixel",
            )
        } finally {
            material?.destroy()
            layer.destroy()
            sourceOver.destroy()
            plus.destroy()
        }
    }

    @Test
    fun texturedUiPrimitiveHonoursZeroAlpha() = withHeadlessRenderer { renderer ->
        val layer = renderer.createRenderTarget(8, 8)
        val output = renderer.createRenderTarget(SIZE, SIZE)
        var material: com.awakekt.awake.render.material.Material? = null
        try {
            renderer.drawUiToTexture(layer, listOf(UiDrawPrimitive.Quad(0f, 0f, 8f, 8f, Color(1f, 0f, 0f, 1f))))
            val layerMaterial = renderer.createMaterial(renderTarget = layer).also { material = it }
            renderer.drawUiToTexture(
                output,
                listOf(
                    UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                    UiDrawPrimitive.Texture(28f, 28f, 8f, 8f, layerMaterial, alpha = 0f, premultiplied = true),
                ),
            )
            val pixel = runBlocking { renderer.readPixels(output) }.data.pixelAt(SIZE / 2, SIZE / 2)
            assertTrue(pixel.blue >= 253 && pixel.red <= FAINT, "zero-alpha texture must not change blue backdrop, was $pixel")
        } finally {
            material?.destroy()
            layer.destroy()
            output.destroy()
        }
    }

    @Test
    fun rendersAQuadOffscreenAndReadsItBack() = withHeadlessRenderer { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        var mesh: com.awakekt.awake.render.mesh.Mesh? = null
        var material: com.awakekt.awake.render.material.Material? = null
        try {
            val quad = renderer.createMesh(greenQuad()).also { mesh = it }
            val quadMaterial = renderer.createMaterial().also { material = it }

            renderer.renderSceneToTexture(target, camera(), listOf(RenderDrawCommand(quad, quadMaterial)))
            val pixels = runBlocking { renderer.readPixels(target) }.data

            val centre = pixels.pixelAt(SIZE / 2, SIZE / 2)
            val corner = pixels.pixelAt(1, 1)

            assertTrue(
                centre.green > STRONG && centre.red < FAINT && centre.blue < FAINT,
                "the quad covers the centre, so it should read as the green it was given, but " +
                    "was $centre. Anything else means the draw never reached the colour " +
                    "attachment, or reached it through the wrong pipeline.",
            )
            assertTrue(
                corner.green < FAINT,
                "the quad does not reach the corner, so it should be the cleared background, " +
                    "but was $corner. Green here means the geometry is not where the camera and " +
                    "projection say it is -- a clip-space or viewport error, which is exactly " +
                    "the class of bug that differs between backends.",
            )
        } finally {
            mesh?.destroy()
            material?.destroy()
        }
    }

    @Test
    fun sceneViewportConfinesOffscreenRenderingAndClamps() = withHeadlessRenderer { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        var mesh: com.awakekt.awake.render.mesh.Mesh? = null
        var material: com.awakekt.awake.render.material.Material? = null
        try {
            val quad = renderer.createMesh(greenQuad()).also { mesh = it }
            val quadMaterial = renderer.createMaterial().also { material = it }

            // 1. Right half only
            val rightHalfViewport = com.awakekt.awake.render.renderer.RenderViewport(
                x = (SIZE / 2).toFloat(),
                y = 0f,
                width = (SIZE / 2).toFloat(),
                height = SIZE.toFloat(),
            )
            renderer.renderSceneToTexture(
                target,
                camera(),
                listOf(RenderDrawCommand(quad, quadMaterial)),
                viewport = rightHalfViewport,
            )
            val confined = runBlocking { renderer.readPixels(target) }.data

            val leftPixel = confined.pixelAt(SIZE / 4, SIZE / 2)
            val rightPixel = confined.pixelAt(3 * SIZE / 4, SIZE / 2)

            assertEquals(0, leftPixel.green, "no geometry may reach outside the right-half scene viewport")
            assertTrue(rightPixel.green > STRONG, "the quad must render inside the right-half scene viewport")

            // 2. Oversized viewport clamps safely
            val oversizedViewport = com.awakekt.awake.render.renderer.RenderViewport(
                x = -100f,
                y = -100f,
                width = SIZE * 4f,
                height = SIZE * 4f,
            )
            renderer.renderSceneToTexture(
                target,
                camera(),
                listOf(RenderDrawCommand(quad, quadMaterial)),
                viewport = oversizedViewport,
            )
            val clamped = runBlocking { renderer.readPixels(target) }.data
            val centrePixel = clamped.pixelAt(SIZE / 2, SIZE / 2)
            assertTrue(centrePixel.green > STRONG, "a clamped viewport must still render the scene")
        } finally {
            mesh?.destroy()
            material?.destroy()
        }
    }

    @Test
    fun instancedLitShadowPipelineAllocatesSufficientUniformBufferWithoutValidationError() = runBlocking {
        var uncapturedError: String? = null
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-instanced-error-test",
            onUncapturedError = { error -> uncapturedError = error.toString() },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, 1)
        swapchainManager.create()
        try {
            val instancedPipeline = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                DescriptorSetLayoutHandle(0),
                engineWgsl(PackShaderSets.Instanced),
                ByteArray(0),
                VertexFormat.PositionNormalColor,
                "vertexMain",
                "fragmentMain",
                variant = PipelineVariant.Instanced,
                bindingsByGroup = PackShaderSets.Instanced.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.Instanced.webGpu.bindingsMetadataAvailable,
            )
            val linePipeline = LineRenderPipeline(graphicsDevice, swapchainManager, engineWgsl(EngineShaderSets.DebugLine))
            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = instancedPipeline,
                    primaryFormat = VertexFormat.PositionNormalColor,
                    instancedByFormat = mapOf(VertexFormat.PositionNormalColor to instancedPipeline),
                ),
                lineRenderPipeline = linePipeline,
                uiShaderSources = UiShaderSources(
                    quad = engineWgsl(EngineShaderSets.UiQuad),
                    glyph = engineWgsl(EngineShaderSets.UiGlyph),
                    texture = engineWgsl(EngineShaderSets.UiTexture),
                    roundedQuad = engineWgsl(EngineShaderSets.UiRoundedQuad),
                    targetComposite = engineWgsl(EngineShaderSets.UiTargetComposite),
                ),
                maxFramesInFlight = 1,
            )
            try {
                renderer.bufferPools.instancedUniformResources(instancedPipeline.handle)
                assertNull(uncapturedError, "WebGPU uncaptured error: $uncapturedError")
            } finally {
                renderer.destroy()
                instancedPipeline.destroy()
            }
        } finally {
            swapchainManager.destroy()
            graphicsDevice.destroy()
        }
    }

    private data class Pixel(val red: Int, val green: Int, val blue: Int)

    private fun ByteArray.pixelAt(x: Int, y: Int): Pixel {
        val offset = (y * SIZE + x) * BYTES_PER_PIXEL
        return Pixel(
            this[offset].toInt() and 0xFF,
            this[offset + 1].toInt() and 0xFF,
            this[offset + 2].toInt() and 0xFF,
        )
    }

    private fun ByteArray.alphaAt(x: Int, y: Int): Int =
        this[(y * SIZE + x) * BYTES_PER_PIXEL + 3].toInt() and 0xFF

    private fun camera() = Lens(
        eye = Vec3f(0f, 0f, EYE_Z),
        center = Vec3f(0f, 0f, 0f),
        fovYRadians = 1f,
        near = 0.1f,
        far = 10f,
    )

    /** A camera-facing green square at the origin, as `VertexFormat.PositionColorUv`. */
    private fun greenQuad() = MeshGeometry(
        floatArrayOf(
            -HALF, -HALF, 0f, 0f, 1f, 0f, 0f, 0f,
            HALF, -HALF, 0f, 0f, 1f, 0f, 1f, 0f,
            HALF, HALF, 0f, 0f, 1f, 0f, 1f, 1f,
            -HALF, HALF, 0f, 0f, 1f, 0f, 0f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
    )

    private fun withHeadlessRenderer(block: (Renderer) -> Unit) = runBlocking {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-pixels",
            onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.create()

        val probe = readResourceBytes("assets/shader/webgpu/pixel_probe.wgsl")
        val primary = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            probe,
            // One WGSL file carries both stages on this backend.
            ByteArray(0),
            VertexFormat.PositionColorUv,
            "vertexMain",
            "fragmentMain",
            bindingsByGroup = mapOf(0 to GroupBindings.UniformOnlyMaterial),
            bindingsMetadataAvailable = true,
        )
        val lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            engineWgsl(EngineShaderSets.DebugLine),
        )
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(
                primary = primary,
                primaryFormat = VertexFormat.PositionColorUv,
            ),
            lineRenderPipeline = lineRenderPipeline,
            // Real sources, not empty arrays: the UI pipelines are built lazily on first
            // drawUi(), so this test never touches them, but handing the Renderer a stub would
            // make that laziness load-bearing for the harness rather than incidental.
            uiShaderSources = UiShaderSources(
                quad = engineWgsl(EngineShaderSets.UiQuad),
                glyph = engineWgsl(EngineShaderSets.UiGlyph),
                texture = engineWgsl(EngineShaderSets.UiTexture),
                roundedQuad = engineWgsl(EngineShaderSets.UiRoundedQuad),
                targetComposite = engineWgsl(EngineShaderSets.UiTargetComposite),
            ),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            // The same feature list WebGpuEngine builds. Not optional any more: rendering to a
            // texture records features exactly as the on-screen path does, so a renderer with an
            // empty list draws nothing -- which is what a capture of a featureless renderer
            // always meant, it just used to draw anyway through a second, hand-written path.
            renderFeatures = listOf(
                OpaqueRenderFeature(WebGpuLinePass(lineRenderPipeline)),
                UiRenderFeature(WebGpuUiPass()),
            ),
        )
        try {
            block(renderer)
        } finally {
            renderer.destroy()
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val SIZE = 64
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val EYE_Z = 3f

        /** Half-extent chosen so the quad covers the centre and leaves the corners cleared. */
        const val HALF = 0.8f

        const val STRONG = 128
        const val FAINT = 24

        // The headless attachment uses BGRA8UnormSrgb. A linear 0.5 channel reads back as 188
        // after sRGB encoding, with a small tolerance for the GPU's float-to-8-bit rounding.
        val HALF_SRGB_RANGE = 185..191
    }
}

/** An engine shader set's WGSL. Inline text now, so this reads no file -- which is the point of
 * [EngineShaderSets] carrying its own source rather than a resource path. */
private suspend fun engineWgsl(set: ShaderSet): ByteArray =
    checkNotNull(set.webGpu[ShaderStage.VERTEX]) {
        "Every engine shader set declares a WebGPU vertex stage."
    }.resolveBytes()
