/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaders.uiShaderSet
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.capture.FrameCapture
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.renderer.renderUiToTexture
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Reusable "record a real animation as it actually renders" primitive for `desktopTest`
 * investigations -- see docs/reference/ui-validation.md's Canonical Test Surfaces entry for
 * when to reach for this instead of the logical-`Rectangle`-sampling throwaway-probe idiom.
 *
 * Every earlier animation investigation this session (row-centering, sidebar collapse,
 * typography) drove animation purely through logical measurement (`measureColumnContent`,
 * `Rectangle` samples) -- never through the real Vulkan draw path, so none of them could see a
 * render-backend artifact (frame-pacing/dirty-rect lag between computed clip bounds and what
 * actually gets presented). This closes that gap: constructs the same real headless Vulkan
 * renderer [RendererHeadlessUiGlyphBaselineTest] uses, and for each of N real frames, renders
 * whatever [UiDrawPrimitive]s the caller's own real animated scene produced that frame (via
 * [Renderer.renderUiToTexture]) and dumps it as a numbered PNG.
 *
 * Deliberately minimal -- not a video/gif exporter, just a loop that calls the real render path
 * N times and writes N PNGs to [outputDir] for manual/visual frame-by-frame inspection. Owns its
 * headless renderer construction/teardown; callers drive their own [com.awakekt
 * .awake.ui.context.UiContext] frames and pass this class the resulting primitive list each
 * time (see `ShadcnCollapsibleRealRenderCollapseFrameCaptureTest` for the pattern).
 */
class UiAnimationFrameCapture private constructor(
    private val renderer: Renderer,
    private val capture: FrameCapture,
    private val outputDir: File,
    private val fixture: HeadlessUiRendererFixture,
) : AutoCloseable {

    /** Renders [primitives] (one real frame's worth, as returned by `UiContext.finishFrame().primitives`)
     * through the real Vulkan UI pipelines and writes it to `outputDir/<namePrefix><frameIndex
     * padded>.png`. Returns the written [File] so callers can immediately read it back (e.g.
     * for a pixel-level jump check) without re-deriving the naming scheme. */
    fun captureFrame(
        frameIndex: Int,
        primitives: List<UiDrawPrimitive>,
        font: UiFont?,
        namePrefix: String = "frame-",
    ): File {
        require(frameIndex >= 0) { "frameIndex must be non-negative." }
        val pixels = runBlocking { capture.capture { target -> renderer.renderUiToTexture(target, primitives, font) } }
        val file = File(outputDir, "$namePrefix${frameIndex.toString().padStart(3, '0')}.png")
        PixelMap(pixels.width, pixels.height, pixels.data).writePng(file)
        return file
    }

    override fun close() {
        capture.close()
        fixture.destroy()
    }

    companion object {
        /** Builds a real headless Vulkan renderer + offscreen render target, same construction
         * this module's other headless tests use (`GraphicsDevice.createHeadless`/
         * `SwapchainManager.createHeadless`, no GLFW window, no real swapchain). [outputDir] is
         * created if missing. */
        fun create(width: Int, height: Int, outputDir: File): UiAnimationFrameCapture {
            require(width > 0 && height > 0) { "Capture size must be positive, was ${width}x$height." }
            require(outputDir.isDirectory || outputDir.mkdirs()) {
                "Could not create capture output directory: ${outputDir.absolutePath}"
            }
            val fixture = buildHeadlessUiRendererFixture(width, height)
            return UiAnimationFrameCapture(
                fixture.renderer,
                FrameCapture(fixture.renderer, width, height),
                outputDir,
                fixture,
            )
        }
    }
}

/** Same fixture shape as [RendererHeadlessUiGlyphBaselineTest]'s private `HeadlessUiRendererFixture`,
 * pulled out standalone so [UiAnimationFrameCapture] can own its own renderer lifecycle without
 * depending on that test class's private nested type. */
private class HeadlessUiRendererFixture(
    private val graphicsDevice: GraphicsDevice,
    private val swapchainManager: SwapchainManager,
    private val pipelineLayoutMaterial: Material,
    private val sceneRenderPass: Long,
    private val renderPipeline: RenderPipeline,
    private val lineRenderPipeline: LineRenderPipeline,
    private val transferContext: TransferContext,
    val renderer: Renderer,
) {
    fun destroy() {
        renderer.destroy()
        renderPipeline.destroy()
        VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, pipelineLayoutMaterial.descriptorSetLayout.handle)
        transferContext.destroy()
        Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
        graphicsDevice.destroy()
    }
}

private const val UI_ANIMATION_MAX_FRAMES_IN_FLIGHT = 1

private fun buildHeadlessUiRendererFixture(width: Int, height: Int): HeadlessUiRendererFixture {
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.createHeadless()
    val swapchainManager = SwapchainManager(graphicsDevice, UI_ANIMATION_MAX_FRAMES_IN_FLIGHT)
    swapchainManager.createHeadless(width, height)
    val pipelineLayoutMaterial = Material(graphicsDevice)
    val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
    val renderPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        pipelineLayoutMaterial.descriptorSetLayout,
        runBlocking {
            packShaderPair("triangle")
        },
        VertexFormat.PositionColorUv,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
    )
    val lineRenderPipeline = LineRenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        runBlocking {
            packShaderPair("debug_line")
        },
        UI_ANIMATION_MAX_FRAMES_IN_FLIGHT,
    )
    val transferContext = TransferContext(graphicsDevice)
    val renderer = Renderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(
            primary = renderPipeline,
            primaryFormat = renderPipeline.vertexFormat,
        ),
        renderFeatures = listOf(OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)), UiRenderFeature(VulkanUiPass())),
        transferContext = transferContext,
        uiShaderPairs = runBlocking { uiShaderSet(::packShaderPair) },
        maxFramesInFlight = UI_ANIMATION_MAX_FRAMES_IN_FLIGHT,
    )
    return HeadlessUiRendererFixture(
        graphicsDevice,
        swapchainManager,
        pipelineLayoutMaterial,
        sceneRenderPass,
        renderPipeline,
        lineRenderPipeline,
        transferContext,
        renderer,
    )
}
