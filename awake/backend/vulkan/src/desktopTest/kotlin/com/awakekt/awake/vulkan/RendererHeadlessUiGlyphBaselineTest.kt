/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiPrimitiveTransform
import com.awakekt.awake.core.text.font.BitmapFont
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.testing.comparePixels
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.renderer.renderUiGlyphsToTexture
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

private fun writeUiBaselinePng(pixels: ByteArray, width: Int, height: Int, file: File) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    var offset = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val r = pixels[offset].toInt() and 0xFF
            val g = pixels[offset + 1].toInt() and 0xFF
            val b = pixels[offset + 2].toInt() and 0xFF
            val a = pixels[offset + 3].toInt() and 0xFF
            image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            offset += 4
        }
    }
    ImageIO.write(image, "png", file)
}

class RendererHeadlessUiGlyphBaselineTest {

    @Test
    fun headlessUiGlyphRenderMatchesBaseline() {
        withHeadlessUiRenderer { renderer ->
            val font = BitmapFont()
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            val glyphs = buildList {
                addAll(glyphRun("AWAKE", font, x = 14f, y = 18f, scale = 2f, color = Color(0.96f, 0.97f, 1f, 1f)))
                addAll(glyphRun("UI_01", font, x = 16f, y = 54f, scale = 2f, color = Color(0.6f, 0.78f, 1f, 1f)))
            }

            renderer.renderUiGlyphsToTexture(target, glyphs, font)
            val pixels = runBlocking { renderer.readPixels(target) }

            val baselineFile = baselineOutputFile()
            if (!baselineFile.exists()) {
                baselineFile.parentFile.mkdirs()
                baselineFile.writeBytes(pixels.data)
                assertTrue(
                    false,
                    "Recorded missing UI glyph baseline to ${baselineFile.absolutePath}. Review and rerun the test.",
                )
            }

            val baseline = baselineFile.readBytes()
            val result = comparePixels(pixels.data, baseline)
            if (!result.matches) {
                val failureDir = File("build/test-failures/RendererHeadlessUiGlyphBaselineTest").apply { mkdirs() }
                val actualPngFile = File(failureDir, "actual.png")
                val baselinePngFile = File(failureDir, "baseline.png")
                writeUiBaselinePng(pixels.data, TARGET_SIZE, TARGET_SIZE, actualPngFile)
                writeUiBaselinePng(baseline, TARGET_SIZE, TARGET_SIZE, baselinePngFile)
                assertTrue(
                    false,
                    "Headless UI glyph render diverged from baseline: ${result.diffPixelCount} pixels differ " +
                        "(max channel diff ${result.maxChannelDiff}). Compare ${actualPngFile.absolutePath} " +
                        "against ${baselinePngFile.absolutePath}. Re-record ${baselineFile.absolutePath} if this " +
                        "is an intentional renderer change.",
                )
            }
        }
    }

    @Test
    fun headlessUiGlyphRenderSupportsRunsLargerThanOneDynamicMesh() {
        withHeadlessUiRenderer { renderer ->
            val font = BitmapFont()
            // Rows derived from the capacity, not a fixed count: this test exists to drive a run
            // past one dynamic mesh, so hardcoding rows makes it silently stop doing that the next
            // time MAX_UI_QUADS moves -- which is exactly what happened when it went 256 -> 1024.
            val label = { row: Int -> "AWAKE_SHOWCASE_ROW_${row.toString().padStart(2, '0')}_0123456789" }
            val rowSpacing = 38f
            val glyphsPerRow = label(0).length
            val rows = Renderer.MAX_UI_QUADS / glyphsPerRow + 2
            val target = renderer.createRenderTarget(512, (12 + rows * rowSpacing).toInt())
            val glyphs = buildList {
                repeat(rows) { row ->
                    addAll(
                        glyphRun(
                            label = label(row),
                            font = font,
                            x = 12f,
                            y = 12f + row * rowSpacing,
                            scale = 1.5f,
                            color = Color(0.92f, 0.95f, 1f, 1f),
                        ),
                    )
                }
            }

            assertTrue(glyphs.size > Renderer.MAX_UI_QUADS, "test data must exceed one UI glyph mesh capacity")
            renderer.renderUiGlyphsToTexture(target, glyphs, font)
            val pixels = runBlocking { renderer.readPixels(target) }
            assertTrue(
                pixels.data.any { it.toInt() != 0 },
                "large headless glyph runs should render non-empty output instead of tripping the mesh capacity guard",
            )
        }
    }

    /**
     * Real GPU coverage for `graphicsLayer(scale(...))`'s scale-only transform (see
     * docs/tasks/2026-08-02-graphicslayer-rotation-scale.md) -- proves `ui_glyph.vert`'s
     * `pivot + (inPosition - pivot) * scale` vertex math actually moves rendered pixels on real
     * Vulkan hardware/MoltenVK, not just in the CPU rasterizer (`UiRasterizerTest`'s
     * `quadWithScaleTransformGrowsAroundItsPivot` covers the CPU path; this covers the GPU
     * shader independently since the two are hand-written and can drift, per this repo's own
     * `ui_quad.vert`'s Y-flip precedent for backend-specific shader bugs).
     */
    @Test
    fun headlessUiGlyphRenderAppliesGraphicsLayerScale() {
        withHeadlessUiRenderer { renderer ->
            val font = BitmapFont()
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            val glyph = glyphRun("A", font, x = 8f, y = 8f, scale = 2f, color = Color(1f, 1f, 1f, 1f))
            // 2x scale around the glyph's own top-left (pivot = 8,8) -- doubles its footprint
            // to the same size as an un-scaled glyph twice as big, growing away from (8,8).
            val scaledGlyph = glyph.map {
                it.copy(
                    transform = UiPrimitiveTransform(
                        scaleX = 2f,
                        scaleY = 2f,
                        pivotX = 8f,
                        pivotY = 8f,
                    ),
                )
            }

            renderer.renderUiGlyphsToTexture(target, scaledGlyph, font)
            val scaledPixels = runBlocking { renderer.readPixels(target) }

            renderer.renderUiGlyphsToTexture(target, glyph, font)
            val unscaledPixels = runBlocking { renderer.readPixels(target) }

            // renderUiGlyphsToTexture clears to opaque black (see Renderer.clearColorValue),
            // not a transparent background -- so "painted" here means "the white glyph ink
            // blended in and lifted this pixel's red channel above black", not alpha (which is
            // 255 everywhere thanks to the opaque clear).
            fun maxPaintedX(pixels: ByteArray): Int {
                var max = -1
                for (y in 0 until TARGET_SIZE) {
                    for (x in 0 until TARGET_SIZE) {
                        val red = pixels[(y * TARGET_SIZE + x) * 4].toInt() and 0xFF
                        if (red > 0 && x > max) max = x
                    }
                }
                return max
            }

            val unscaledMaxX = maxPaintedX(unscaledPixels.data)
            val scaledMaxX = maxPaintedX(scaledPixels.data)
            assertTrue(unscaledMaxX >= 0, "the un-scaled glyph should render some non-transparent pixels")
            // Bounding-box comparison (not a single probe pixel) -- robust regardless of the
            // font's actual glyph-interior coverage: whatever "A" looks like, scaling its quad
            // 2x around its own top-left MUST push its rightmost painted pixel further right,
            // since (pivot + (pos - pivot) * scale) with pivot=(8,8) and scale=2 doubles the
            // quad's on-screen width from the same origin.
            assertTrue(
                scaledMaxX > unscaledMaxX,
                "2x-scaled glyph (rightmost painted x=$scaledMaxX) should extend further right than " +
                    "the un-scaled glyph (rightmost painted x=$unscaledMaxX)",
            )
        }
    }

    @Test
    fun headlessUiGlyphRenderSupportsLargeMipChainFont() {
        withHeadlessUiRenderer { renderer ->
            val font = UiFonts.trueSans()
            val target = renderer.createRenderTarget(128, 128)
            val glyphs = glyphRun("AWAKE", font, x = 8f, y = 8f, scale = 2f, color = Color(0.96f, 0.97f, 1f, 1f))

            renderer.renderUiGlyphsToTexture(target, glyphs, font)
            val pixels = runBlocking { renderer.readPixels(target) }
            assertTrue(
                pixels.data.any { it.toInt() != 0 },
                "TrueSans (large mip chain, ~11 levels) should render non-empty output, not sample an UNDEFINED-layout mip",
            )
        }
    }

    @Test
    fun headlessMtsdfGlyphCoverageGrowsAcrossUiSizes() {
        withHeadlessUiRenderer { renderer ->
            val font = UiFonts.trueSans()
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            val bounds = listOf(1f, 1.25f, 1.5f).map { scale ->
                renderer.renderUiGlyphsToTexture(
                    target,
                    glyphRun("H", font, x = 20f, y = 20f, scale = scale, color = Color.White),
                    font,
                )
                val pixels = runBlocking { renderer.readPixels(target) }.data
                var minX = TARGET_SIZE
                var minY = TARGET_SIZE
                var maxX = -1
                var maxY = -1
                var partial = 0
                for (y in 0 until TARGET_SIZE) {
                    for (x in 0 until TARGET_SIZE) {
                        val red = pixels[(y * TARGET_SIZE + x) * 4].toInt() and 0xFF
                        if (red > 0) {
                            minX = minOf(minX, x)
                            minY = minOf(minY, y)
                            maxX = maxOf(maxX, x)
                            maxY = maxOf(maxY, y)
                            if (red < 255) partial++
                        }
                    }
                }
                assertTrue(maxX >= minX && maxY >= minY, "MTSDF glyph produced no visible ink at scale $scale")
                assertTrue(partial > 0, "MTSDF glyph lost all antialiased edge coverage at scale $scale")
                (maxX - minX + 1) to (maxY - minY + 1)
            }

            bounds.zipWithNext().forEach { (smaller, larger) ->
                assertTrue(
                    larger.second > smaller.second,
                    "MTSDF glyph ink stopped growing across scales: $bounds",
                )
            }
        }
    }

    private fun withHeadlessUiRenderer(block: (Renderer) -> Unit) {
        block(sharedFixture().renderer)
    }

    private class HeadlessUiRendererFixture(
        val graphicsDevice: GraphicsDevice,
        val swapchainManager: SwapchainManager,
        val pipelineLayoutMaterial: Material,
        val sceneRenderPass: Long,
        val renderPipeline: RenderPipeline,
        val lineRenderPipeline: LineRenderPipeline,
        val transferContext: TransferContext,
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

    companion object {
        /** Reuses this class's shared headless fixture for other test classes that need a real
         * headless Vulkan [Renderer] but don't otherwise belong in this file -- see
         * `RendererHeadlessStrokedPathChunkingTest` for the motivating case (avoids duplicating
         * the ~40 lines of GraphicsDevice/SwapchainManager/pipeline setup [sharedFixture] does). */
        fun withSharedHeadlessRenderer(block: (Renderer) -> Unit) {
            block(sharedFixture().renderer)
        }

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BASELINE_RESOURCE_PATH = "baselines/ui-glyph-headless.rgba"

        private var cachedFixture: HeadlessUiRendererFixture? = null

        /**
         * `RendererHeadlessPixelBaselineTest` already proves the full headless create/destroy
         * path end to end. This suite is narrower: it proves UI glyph rendering on that same
         * offscreen path. Reusing one fixture keeps the glyph proof stable on MoltenVK, where
         * tearing down and recreating a validation-enabled headless instance twice in one JVM
         * test class has been flaky on macOS.
         */
        private fun sharedFixture(): HeadlessUiRendererFixture {
            cachedFixture?.let { return it }

            val graphicsDevice = GraphicsDevice()
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val renderPipeline = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val lineRenderPipeline = LineRenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                runBlocking { packShaderPair("debug_line") },
                MAX_FRAMES_IN_FLIGHT,
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
                uiShaderPairs = UiShaderPairs(
                    quad = runBlocking { packShaderPair("ui_quad") },
                    glyph = runBlocking { packShaderPair("ui_glyph") },
                    texture = runBlocking {
                        packShaderPair("ui_texture")
                    },
                    roundedQuad = runBlocking {
                        packShaderPair("ui_rounded_quad")
                    },
                ),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            )

            return HeadlessUiRendererFixture(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelineLayoutMaterial = pipelineLayoutMaterial,
                sceneRenderPass = sceneRenderPass,
                renderPipeline = renderPipeline,
                lineRenderPipeline = lineRenderPipeline,
                transferContext = transferContext,
                renderer = renderer,
            ).also { cachedFixture = it }
        }

        @AfterClass
        @JvmStatic
        fun destroySharedFixture() {
            cachedFixture?.destroy()
            cachedFixture = null
        }

        fun baselineOutputFile(): File {
            val cwd = File(System.getProperty("user.dir"))
            val repoRelative = File(cwd, "awake/backend/vulkan/src/desktopTest/resources/$BASELINE_RESOURCE_PATH")
            return if (repoRelative.parentFile.exists()) {
                repoRelative
            } else {
                File(cwd, "src/desktopTest/resources/$BASELINE_RESOURCE_PATH")
            }
        }

        fun glyphRun(
            label: String,
            font: UiFont,
            x: Float,
            y: Float,
            scale: Float,
            color: Color,
        ): List<UiDrawPrimitive.Glyph> {
            val glyphSize = font.cellSize * scale
            var cursorX = x
            return buildList {
                label.forEach { char ->
                    val uv = font.uvFor(char) ?: return@forEach
                    add(
                        UiDrawPrimitive.Glyph(
                            x = cursorX,
                            y = y,
                            w = glyphSize,
                            h = glyphSize,
                            u0 = uv.u0,
                            v0 = uv.v0,
                            u1 = uv.u1,
                            v1 = uv.v1,
                            color = color,
                        ),
                    )
                    cursorX += glyphSize
                }
            }
        }
    }
}
