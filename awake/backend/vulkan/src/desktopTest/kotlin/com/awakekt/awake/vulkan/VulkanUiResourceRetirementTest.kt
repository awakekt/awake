/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies Vulkan UI sampler resampling, staged UI invalidation on [Material.destroy], and deferred
 * [com.awakekt.awake.vulkan.texture.OffscreenRenderTarget.destroy] under `VK_LAYER_KHRONOS_validation`.
 */
class VulkanUiResourceRetirementTest {

    @Test
    fun uiMaterialTracksLiveSamplerAcrossAnisotropyResample() = withHeadlessRenderer { renderer, _ ->
        val material = renderer.createMaterial(
            texture = TextureAsset(ByteArray(TEX_SIZE * TEX_SIZE * 4) { -1 }, TEX_SIZE, TEX_SIZE),
        ) as Material
        val originalSampler = material.samplerHandle
        val quad = listOf(UiDrawPrimitive.Texture(0f, 0f, 32f, 32f, material))

        renderer.drawUi(quad, null)
        drawFrame(renderer, emptyList())

        val higherAnisotropy = minOf(4, renderer.maxSupportedAnisotropy)
        if (higherAnisotropy > 1) {
            renderer.maxAnisotropy = higherAnisotropy
            assertNotEquals(
                originalSampler,
                material.samplerHandle,
                "UI Material.samplerHandle must track Texture.sampler after maxAnisotropy resampling",
            )
            repeat(FRAMES_IN_FLIGHT + 3) {
                renderer.drawUi(quad, null)
                drawFrame(renderer, emptyList())
            }
            assertEquals(0, renderer.retiredSamplers.pending, "Retired samplers must drain cleanly after resampling")
        }
        material.destroy()
        renderer.drawUi(emptyList(), null)
        repeat(FRAMES_IN_FLIGHT + 2) { drawFrame(renderer, emptyList()) }
    }

    @Test
    fun destroyingOffscreenRenderTargetMidFlightDefersHandlesWithoutValidationErrors() = withHeadlessRenderer { renderer, _ ->
        val mesh = renderer.createMesh(MeshGeometry(TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor))
        val sceneMaterial = renderer.createMaterial()
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val targetMaterial = renderer.createMaterial(renderTarget = target)

        renderer.renderToTexture(target, compilePass(renderer, listOf(RenderDrawCommand(mesh, sceneMaterial))))
        renderer.drawUi(listOf(UiDrawPrimitive.Texture(0f, 0f, 32f, 32f, targetMaterial)), null)
        drawFrame(renderer, emptyList())

        // Destroy the offscreen target and its compositing material immediately while submitted commands are in flight.
        target.destroy()
        targetMaterial.destroy()
        assertEquals(0, renderer.gpuResourceStats.liveRenderTargets)
        assertTrue(
            renderer.deferredResourceReaper.pending >= 2,
            "OffscreenRenderTarget and Material destruction must defer through DeferredResourceReaper",
        )

        renderer.drawUi(emptyList(), null)
        repeat(FRAMES_IN_FLIGHT + 2) { drawFrame(renderer, emptyList()) }
        assertEquals(0, renderer.deferredResourceReaper.pending, "Retired offscreen target must drain after in-flight frames")
        mesh.destroy()
        sceneMaterial.destroy()
        repeat(FRAMES_IN_FLIGHT + 2) { drawFrame(renderer, emptyList()) }
    }

    @Test
    fun destroyingMaterialBetweenDrawUiAndDrawDropsStagedUiRun() = withHeadlessRenderer { renderer, _ ->
        val material = renderer.createMaterial(
            texture = TextureAsset(ByteArray(TEX_SIZE * TEX_SIZE * 4) { -1 }, TEX_SIZE, TEX_SIZE),
        )
        renderer.drawUi(listOf(UiDrawPrimitive.Texture(0f, 0f, 32f, 32f, material)), null)
        assertTrue(renderer.uiRuns.isNotEmpty(), "drawUi should stage a texture run")

        // Simulate a scene disposal freeing a UI texture material after drawUi staged it and before draw() records it.
        material.destroy()
        assertTrue(renderer.uiRuns.isEmpty(), "Destroying a material must drop its staged UI texture runs immediately")

        repeat(FRAMES_IN_FLIGHT + 2) { drawFrame(renderer, emptyList()) }
        assertEquals(0, renderer.deferredResourceReaper.pending)
    }

    private fun withHeadlessRenderer(block: (Renderer, GraphicsDevice) -> Unit) {
        val device = GraphicsDevice()
        device.createHeadless()
        val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT)
        swapchain.createHeadlessPresentable(SIZE, SIZE)
        val layout = Material.createDescriptorSetLayout(device)
        val renderPass = createSceneRenderPass(device, swapchain)
        val pipeline = RenderPipeline(
            device,
            swapchain,
            renderPass,
            layout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val transfer = TransferContext(device)
        val lines = LineRenderPipeline(device, swapchain, renderPass, runBlocking { packShaderPair("debug_line") }, FRAMES_IN_FLIGHT)
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = pipeline, primaryFormat = VertexFormat.PositionNormalColor),
            uiShaderPairs = runBlocking { defaultUiShaderPairs() },
            transferContext = transfer,
            renderFeatures = listOf(OpaqueRenderFeature(VulkanLinePass(lines)), UiRenderFeature(VulkanUiPass())),
            maxFramesInFlight = FRAMES_IN_FLIGHT,
        )
        swapchain.createSyncObjects()
        val cleanup = headlessCleanup(device, transfer, renderPass, layout, pipeline)
        try {
            block(renderer, device)
        } finally {
            renderer.destroy()
            swapchain.destroy()
            swapchain.destroySyncObjects()
            cleanup()
            device.destroy()
        }
    }

    private fun drawFrame(renderer: Renderer, draws: List<RenderDrawCommand>) {
        renderer.draw(compilePass(renderer, draws))
    }

    private fun compilePass(renderer: Renderer, draws: List<RenderDrawCommand>) = ScenePassCompiler.compile(
        lens = Lens(eye = Vec3f(0f, 0f, 2f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f),
        drawCalls = draws,
        light = DEFAULT_SCENE_LIGHT,
        environment = EnvironmentUniforms.Default,
        clipSpace = renderer.clipSpace,
        aspect = 1f,
        viewport = null,
        drawPreparer = renderer.gpuDrawPreparer,
    )

    private companion object {
        const val SIZE = 64
        const val TEX_SIZE = 16
        const val FRAMES_IN_FLIGHT = 2
        val TRIANGLE_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 0f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 0f,
            0f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 1f,
        )
        val TRIANGLE_INDICES = intArrayOf(0, 1, 2)
    }
}
