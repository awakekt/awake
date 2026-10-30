/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.shadowMapViewContentFeature
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.BitmapFont
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.effects.FxaaEffect
import com.awakekt.awake.render.effects.PostProcessingStack
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.MultisampleState
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Production-plan pixel probes. Measured negative controls on 2026-10-27:
 * disabling MSAA yields 0 geometry and 0 mask-edge intermediate pixels on both backends (all
 * four edge tests fail). Leaving the scene's encoded cutoff in the hard depth shader changes
 * 5,604 shadow-view pixels on Vulkan. Positive counts: geometry 146/218, mask 70/70;
 * the UI glyph has 42 white pixels (omitting it produces 0 and fails the UI layer guard).
 * Letting opaque texture alpha reach A2C changes 17,694/15,483 RGB channels on Vulkan/WebGPU.
 * Offscreen FXAA originally fails Vulkan validation when
 * resumed against the multisampled scene framebuffer (attachment count and sample mismatch).
 */
abstract class MultisampleSceneFrameProbe {
    protected fun unsupportedWebGpuCountsDowngradeWithoutUpscaling() = runBlocking {
        assertPixelsEqual(capture(1, RenderBackend.WebGpu), capture(2, RenderBackend.WebGpu), "WebGPU 2x downgrades to 1x")
        assertPixelsEqual(capture(4, RenderBackend.WebGpu), capture(8, RenderBackend.WebGpu), "WebGPU 8x downgrades to 4x")
    }

    protected fun maskedShadowMapKeepsItsSingleSampleHardCutout() = runBlocking {
        for (backend in RenderBackend.entries) {
            val single = capture(1, backend, masked = true, shadowMap = true)
            val coverage = capture(4, backend, masked = true, coverage = true, shadowMap = true)
            write(single, "$backend-shadow-single")
            write(coverage, "$backend-shadow-coverage")
            assertTrue(single.toList().chunked(4).drop(SIZE * UI_HEIGHT).map { it.first() }.toSet().size > 1, "Shadow viewer must show both caster and clear depth")
            assertPixelsEqual(single, coverage, "$backend shadow map remains a single-sample hard cutout")
        }
    }

    protected fun offscreenUiLayersStaySingleSample() = runBlocking {
        for (backend in RenderBackend.entries) {
            val single = capture(1, backend, offscreen = true, uiLayer = true)
            val multisampled = capture(4, backend, offscreen = true, uiLayer = true)
            val ink = single.toList().chunked(4).count { rgba -> rgba.take(3).all { (it.toInt() and 255) > 200 } }
            assertTrue(ink > 20, "UI layer must contain glyph ink; measured $ink white pixels")
            assertPixelsEqual(single, multisampled, "$backend UI layer pixels must not depend on scene samples")
            write(single, "$backend-ui-layer-single")
            write(multisampled, "$backend-ui-layer-four")
        }
    }

    protected fun offscreenResolvesBeforeExplicitFxaa() = runBlocking {
        for (backend in RenderBackend.entries) {
            val resolved = capture(4, backend, offscreen = true)
            val filtered = capture(4, backend, offscreen = true, fxaa = true)
            val changed = resolved.indices.count { resolved[it] != filtered[it] }
            println("MSAA+FXAA/$backend: $changed changed channels")
            assertTrue(changed > 20, "Explicit FXAA must filter resolved geometry edges")
            write(resolved, "$backend-offscreen-resolve")
            write(filtered, "$backend-offscreen-fxaa")
        }
    }

    protected fun gpuTimestampCostAt1080p() = runBlocking {
        for (samples in listOf(1, 2, 4)) capture(samples, RenderBackend.Vulkan, masked = true, width = 1920, height = 1080, frames = 160)
    }

    protected fun vulkanFourSamplesResolveGeometryEdgesBeforeTheUiOverlay() = runBlocking { verifyGeometry(RenderBackend.Vulkan) }

    protected fun webGpuFourSamplesResolveGeometryEdgesBeforeTheUiOverlay() = runBlocking { verifyGeometry(RenderBackend.WebGpu) }

    private suspend fun verifyGeometry(backend: RenderBackend) {
        val single = capture(1, backend)
        val multisampled = capture(4, backend)
        assertTrue(single.toList().chunked(4).take(SIZE * UI_HEIGHT).count { rgba -> rgba.take(3).all { (it.toInt() and 255) > 200 } } > 20, "UI glyph must be visible")
        val oldColors = single.toList().chunked(4).map { it.take(3) }.toSet()
        val newEdgePixels = multisampled.toList().chunked(4).drop(SIZE * UI_HEIGHT).count { it.take(3) !in oldColors }
        println("MSAA/$backend: $newEdgePixels new edge colours, single-sample palette=${oldColors.size}")
        assertTrue(newEdgePixels > 20, "4x resolve must create intermediate edge coverage; got $newEdgePixels")
        assertPixelsEqual(single.copyOf(SIZE * UI_HEIGHT * 4), multisampled.copyOf(SIZE * UI_HEIGHT * 4), "UI stays single-sample")
        val filtered = capture(4, backend, fxaa = true)
        val covered = capture(4, backend, coverage = true)
        assertPixelsEqual(multisampled, covered, "Untextured opaque geometry keeps full coverage")
        assertPixelsEqual(multisampled.copyOf(SIZE * UI_HEIGHT * 4), filtered.copyOf(SIZE * UI_HEIGHT * 4), "UI follows FXAA")
        write(single, "$backend-single")
        write(multisampled, "$backend-four")
    }

    protected fun vulkanMaskedTextureEdgesUseCoverageOnlyWhenRequested() = runBlocking { verifyMask(RenderBackend.Vulkan) }

    protected fun webGpuMaskedTextureEdgesUseCoverageOnlyWhenRequested() = runBlocking { verifyMask(RenderBackend.WebGpu) }

    private suspend fun verifyMask(backend: RenderBackend) {
        val single = capture(1, backend, masked = true)
        val hard = capture(4, backend, masked = true)
        val coverage = capture(4, backend, masked = true, coverage = true)
        fun interior(pixels: ByteArray) = (32 until 96).flatMap { y ->
            (32 until 96).map { x -> pixels.slice((y * SIZE + x) * 4 until (y * SIZE + x) * 4 + 3) }
        }
        val hardColors = interior(hard).toSet()
        assertEquals(interior(single), interior(hard), "MSAA alone preserves the hard internal mask edge")
        val intermediate = interior(coverage).count { it !in hardColors }
        println("A2C/$backend: $intermediate intermediate mask edge pixels, hard palette=${hardColors.size}")
        write(hard, "$backend-mask-hard")
        write(single, "$backend-mask-single")
        write(coverage, "$backend-mask-coverage")
        assertTrue(intermediate > 20, "Coverage must smooth the internal mask edge; got $intermediate")
        assertPixelsEqual(hard.copyOf(SIZE * UI_HEIGHT * 4), coverage.copyOf(SIZE * UI_HEIGHT * 4))
    }

    protected fun opaqueTextureAlphaDoesNotReduceCoverage(backend: RenderBackend) = runBlocking {
        val opaque = capture(4, backend, masked = true, forceOpaque = true)
        val covered = capture(4, backend, masked = true, forceOpaque = true, coverage = true)
        val changed = opaque.indices.count { it % 4 != 3 && opaque[it] != covered[it] }
        assertEquals(0, changed, "An opaque material ignores texture alpha with coverage enabled")
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod") // One production frame fixture for all backend/pass combinations.
    private suspend fun capture(
        samples: Int,
        backend: RenderBackend,
        masked: Boolean = false,
        coverage: Boolean = false,
        offscreen: Boolean = false,
        fxaa: Boolean = false,
        width: Int = SIZE,
        height: Int = SIZE,
        frames: Int = 1,
        uiLayer: Boolean = false,
        shadowMap: Boolean = false,
        forceOpaque: Boolean = false,
    ): ByteArray {
        val state = MultisampleState(samples, coverage)
        val engine = if (backend == RenderBackend.Vulkan) CaptureEngine(state, width, height) else null
        val webSession = if (engine == null) {
            webGpuHeadlessPlan(
                EngineShowcaseRenderPlan.copy(multisample = state),
                surface = HeadlessSurface(width, height),
            )
        } else {
            null
        }
        engine?.create(HeadlessSurface(width, height))
        try {
            if (engine != null) {
                withTimeout(60_000) {
                    while (!engine.isReady) {
                        engine.startupError?.let { throw it }
                        delay(1)
                    }
                }
            }
            val renderer = engine?.backend() ?: requireNotNull(webSession).renderer
            if (shadowMap) (renderer as com.awakekt.awake.asset.shaders.ContentFeatureHost).attachContentFeature(shadowMapViewContentFeature())
            val mesh = renderer.createMesh(
                if (masked) {
                    maskedGeometry()
                } else {
                    MeshGeometry(
                        floatArrayOf(
                            -0.82f, -0.77f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f,
                            0.79f, -0.61f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f,
                            -0.49f, 0.55f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f,
                        ),
                        intArrayOf(0, 1, 2),
                        VertexFormat.PositionNormalColor,
                    )
                },
            )
            val material = renderer.createMaterial(
                texture = if (masked) maskTexture() else null,
                uniformFloatCount = if (masked) MaterialUniformLayouts.PbrTextured.total else com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout.total,
            )
            val target = if (offscreen) renderer.createRenderTarget(width, height) else null
            try {
                val environment = GpuEnvironmentState.Default.copy(
                    clearColor = Color.Black,
                    debugView = when {
                        shadowMap -> GpuDebugView(code = RenderDebugView.ShadowMap.code)
                        masked && !forceOpaque -> GpuDebugView(code = RenderDebugView.Albedo.code)
                        else -> GpuDebugView.Off
                    },
                )
                val cascades = if (shadowMap) GpuShadowCascadeData(viewProjections = listOf(Mat4()), splitDistances = floatArrayOf(100f)) else null
                val context = GpuDrawPreparationContext(
                    viewProjection = Mat4(),
                    cameraEye = Vec3f(0f, 0f, 2f),
                    cameraForward = Vec3f(0f, 0f, -1f),
                    passUniforms = sceneLightUniforms(DEFAULT_SCENE_LIGHT, Vec3f(0f, 0f, 2f)).packed,
                    environment = environment,
                    shadowCascadeData = cascades,
                    shadowViewProjections = cascades?.viewProjections.orEmpty(),
                )
                val prepared = requireNotNull((renderer as GpuDrawPreparationSource).gpuDrawPreparer)
                    .prepare(GpuDrawRequest(mesh, material, alphaMode = if (masked && !forceOpaque) AlphaMode.Masked else AlphaMode.Opaque), 0, context)
                val font = BitmapFont()
                val glyph = requireNotNull(font.glyphFor('A'.code, FontWeight.Normal))
                val uiPrimitives = listOf(
                    UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), UI_HEIGHT.toFloat(), Color(0.1f, 0.2f, 0.4f, 1f)),
                    UiDrawPrimitive.Glyph(5f, 5f, 10f, 14f, glyph.u0, glyph.v0, glyph.u1, glyph.v1, Color.White, font = font),
                )
                renderer.drawUi(uiPrimitives, font)
                val input = GpuPassInput.EMPTY.copy(
                    resolvedOpaqueDraws = if (shadowMap) emptyList() else listOf(requireNotNull(prepared)),
                    prePasses = if (shadowMap) listOf(GpuSubPass(target = null, viewProjection = Mat4(), resolvedDraws = listOf(requireNotNull(prepared)))) else emptyList(),
                    shadowCascadeData = cascades,
                    environment = environment,
                    postPassChain = if (fxaa) PostProcessingStack.buildChain(listOf(FxaaEffect()), renderer.imagePassTargetContext(target)) else ImagePassChain.Empty,
                )
                val timings = mutableListOf<Float>()
                repeat(frames) { frame ->
                    when {
                        target == null -> renderer.draw(input)
                        uiLayer -> renderer.drawUiToTexture(target, uiPrimitives, font)
                        else -> renderer.renderToTexture(target, input)
                    }
                    if (frame >= 40) renderer.frameStats?.gpuTimeMs?.let { timings += it }
                }
                if (frames > 1) {
                    val sorted = timings.sorted()
                    println("MSAA GPU/$backend samples=$samples ${width}x$height warmup=40 measured=${sorted.size} medianMs=${sorted.getOrNull(sorted.size / 2)} p95Ms=${sorted.getOrNull((sorted.size * 0.95).toInt())}")
                }
                return if (target == null) renderer.readPresentedPixels().data else renderer.readPixels(target).data
            } finally {
                target?.destroy()
                mesh.destroy()
                material.destroy()
            }
        } finally {
            engine?.dispose()
            webSession?.close()
        }
    }

    private class CaptureEngine(state: MultisampleState, width: Int, height: Int) :
        VulkanEngine(
            appSpec { window { size(width, height) } }.createLifecycle(),
            EngineShowcaseRenderPlan.copy(multisample = state),
        ) {
        fun backend(): Renderer = renderer
    }

    private fun maskedGeometry() = MeshGeometry(
        floatArrayOf(
            -0.9f, -0.9f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f,
            0.9f, -0.9f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0f,
            0.9f, 0.9f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
            -0.9f, 0.9f, 0.4f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 1f,
        ),
        intArrayOf(0, 1, 2, 0, 2, 3),
        VertexFormat.PositionNormalColorUv,
    )

    private fun maskTexture() = TextureAsset(
        data = ByteArray(64 * 64 * 4) { index ->
            val pixel = index / 4
            when (index % 4) {
                0 -> 40.toByte()
                1 -> 220.toByte()
                2 -> 70.toByte()
                else -> ((pixel % 64 + (pixel / 64) * 0.45f - 43f) * 100f + 128f).toInt().coerceIn(0, 255).toByte()
            }
        },
        width = 64,
        height = 64,
    )

    private fun assertPixelsEqual(expected: ByteArray, actual: ByteArray, message: String = "Pixels must match") {
        assertEquals(expected.size, actual.size, message)
        assertEquals(0, expected.indices.count { expected[it] != actual[it] }, message)
    }

    private fun write(pixels: ByteArray, name: String) {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        repeat(SIZE * SIZE) { pixel ->
            fun channel(index: Int) = pixels[pixel * 4 + index].toInt() and 0xff
            image.setRGB(pixel % SIZE, pixel / SIZE, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
        }
        val directory = File("build/reports/msaa").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }

    private companion object {
        const val SIZE = 128
        const val UI_HEIGHT = 24
    }
}

// Native GPU fixtures fork per class. Keep each comparison in its own process: the desktop
// WebGPU binding retains native child objects past device close (tracked by #996).

class MultisampleSceneFrameVulkanGeometryTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = vulkanFourSamplesResolveGeometryEdgesBeforeTheUiOverlay()
}

class MultisampleSceneFrameWebGpuGeometryTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = webGpuFourSamplesResolveGeometryEdgesBeforeTheUiOverlay()
}

class MultisampleSceneFrameVulkanMaskTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = vulkanMaskedTextureEdgesUseCoverageOnlyWhenRequested()
}

class MultisampleSceneFrameWebGpuMaskTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = webGpuMaskedTextureEdgesUseCoverageOnlyWhenRequested()
}

class MultisampleSceneFrameUiLayersTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = offscreenUiLayersStaySingleSample()
}

class MultisampleSceneFrameFxaaTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = offscreenResolvesBeforeExplicitFxaa()
}

class MultisampleSceneFrameShadowTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = maskedShadowMapKeepsItsSingleSampleHardCutout()
}

class MultisampleSceneFrameGpuCostTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = gpuTimestampCostAt1080p()
}

class MultisampleSceneFrameFallbackTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = unsupportedWebGpuCountsDowngradeWithoutUpscaling()
}

class MultisampleSceneFrameVulkanOpaqueTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = opaqueTextureAlphaDoesNotReduceCoverage(RenderBackend.Vulkan)
}

class MultisampleSceneFrameWebGpuOpaqueTest : MultisampleSceneFrameProbe() {
    @Test
    fun verify() = opaqueTextureAlphaDoesNotReduceCoverage(RenderBackend.WebGpu)
}
