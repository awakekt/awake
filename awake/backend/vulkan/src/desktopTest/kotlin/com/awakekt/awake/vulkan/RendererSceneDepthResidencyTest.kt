/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.entryPoint
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.ShadowArrayResidency
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanRenderFrameContext
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies dynamic `needsSceneDepth` gating, independent lazy 1x1 residency for `sceneDepthTarget`
 * and `maskTarget`, `Depth16Unorm` formatting, residency-window release, and `trimMemory()`
 * reclamation under Vulkan validation layers.
 */
class RendererSceneDepthResidencyTest {

    @Test
    fun sceneDepthAndMaskStayDormantUntilNeededGrowIndependentlyAndReleaseWhenIdle() {
        var needsDepth = false
        val consumer = object : RenderFeature<RenderFrameContext> {
            override val pass = RenderPassSlot.Scene
            override fun needsSceneDepth(environment: GpuEnvironmentState): Boolean = needsDepth
            override fun recordCommands(context: RenderFrameContext) = Unit
            override fun destroy() = Unit
        }
        val fixture = buildPresentableRenderer(consumer, ShadowDepthFormat.Depth16Unorm)
        try {
            val renderer = fixture.renderer
            val sceneDepthPass = fixture.sceneDepthPass
            val maskPass = fixture.maskPass
            val idleInput = passInput(maskLayers = emptyList())

            assertFalse(sceneDepthPass.depthTarget.resident)
            assertFalse(maskPass.depthTarget.resident)
            assertEquals(ShadowDepthFormat.Depth16Unorm, sceneDepthPass.depthTarget.format)
            assertEquals(ShadowDepthFormat.Depth16Unorm, maskPass.depthTarget.format)
            assertEquals(MASK_TARGET_LAYER_COUNT, maskPass.depthTarget.layers)

            renderer.draw(idleInput)
            val scenePassesAfterInit = sceneDepthPass.passesRecorded
            val maskPassesAfterInit = maskPass.passesRecorded
            repeat(3) { renderer.draw(idleInput) }
            assertFalse(sceneDepthPass.depthTarget.resident, "idle frame must not grow sceneDepthTarget")
            assertFalse(maskPass.depthTarget.resident, "idle frame must not grow maskTarget")
            assertEquals(scenePassesAfterInit, sceneDepthPass.passesRecorded, "idle frames must record 0 scene-depth passes")
            assertEquals(maskPassesAfterInit, maskPass.passesRecorded, "idle frames must record 0 mask passes")

            needsDepth = true
            val beforeActiveDepth = sceneDepthPass.passesRecorded
            renderer.draw(idleInput)
            assertTrue(sceneDepthPass.depthTarget.resident)
            assertEquals(TARGET_SIZE, sceneDepthPass.depthTarget.width)
            assertEquals(TARGET_SIZE, sceneDepthPass.depthTarget.height)
            assertFalse(maskPass.depthTarget.resident, "maskTarget must remain dormant while unmasked")
            assertEquals(1L, sceneDepthPass.passesRecorded - beforeActiveDepth)
            assertTrue(renderer.sceneDepthTargetBytes >= TARGET_SIZE.toLong() * TARGET_SIZE.toLong() * 2L)

            needsDepth = false
            val beforeDisable = sceneDepthPass.passesRecorded
            renderer.draw(idleInput)
            assertEquals(1L, sceneDepthPass.passesRecorded - beforeDisable, "dirty layer cleared once on disable")
            val afterClear = sceneDepthPass.passesRecorded
            val window = ShadowArrayResidency.DEFAULT_RELEASE_AFTER_FRAMES
            repeat(window - 1) { renderer.draw(idleInput) }
            assertEquals(afterClear, sceneDepthPass.passesRecorded, "subsequent idle frames record 0 depth passes")
            repeat(window + 1) { renderer.draw(idleInput) }
            assertFalse(sceneDepthPass.depthTarget.resident, "sceneDepthTarget must release back to 1x1 after residency window")
            assertEquals(1, sceneDepthPass.depthTarget.width)

            assertMaskLifecycleAndTrim(fixture, passInput(maskLayers = listOf(0)))
        } finally {
            fixture.close()
        }
    }

    private fun assertMaskLifecycleAndTrim(fixture: ResidencyFixture, maskedInput: GpuPassInput) {
        val renderer = fixture.renderer
        val maskPass = fixture.maskPass
        renderer.draw(maskedInput)
        assertTrue(maskPass.depthTarget.resident)
        assertEquals(TARGET_SIZE, maskPass.depthTarget.width)
        assertEquals(MASK_TARGET_LAYER_COUNT, maskPass.depthTarget.layers)
        assertFalse(fixture.sceneDepthPass.depthTarget.resident)

        renderer.trimMemory()
        assertFalse(maskPass.depthTarget.resident, "trimMemory() must shrink maskTarget to 1x1")
        assertEquals(MASK_TARGET_LAYER_COUNT, maskPass.depthTarget.layers, "dormant maskTarget must keep all array layers")

        renderer.draw(maskedInput)
        assertTrue(maskPass.depthTarget.resident)
        assertEquals(TARGET_SIZE, maskPass.depthTarget.width)
    }

    private fun passInput(maskLayers: List<Int>): GpuPassInput = GpuPassInput(
        viewProjection = Mat4(),
        cameraEye = Vec3f(0f, 0f, 5f),
        passUniforms = FloatArray(0),
        resolvedPath = true,
        maskPasses = maskLayers.map { layer ->
            GpuSubPass(target = null, targetLayer = layer, viewProjection = Mat4())
        },
    )

    private class ResidencyFixture(
        val renderer: Renderer,
        val sceneDepthPass: DepthPrePassFeature,
        val maskPass: DepthPrePassFeature,
        private val primary: RenderPipeline,
        private val pipelineLayoutMaterial: Material,
        private val sceneRenderPass: Long,
        private val transferContext: TransferContext,
        private val swapchainManager: SwapchainManager,
        private val graphicsDevice: GraphicsDevice,
    ) : AutoCloseable {
        override fun close() {
            renderer.waitIdle()
            renderer.destroy()
            swapchainManager.destroy()
            swapchainManager.destroySyncObjects()
            primary.destroy()
            pipelineLayoutMaterial.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            transferContext.destroy()
            graphicsDevice.destroy()
        }
    }

    private fun buildPresentableRenderer(
        consumer: RenderFeature<RenderFrameContext>,
        format: ShadowDepthFormat,
    ): ResidencyFixture {
        val graphicsDevice = GraphicsDevice().apply { createHeadless() }
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT).apply {
            createHeadlessPresentable(TARGET_SIZE, TARGET_SIZE)
            createSyncObjects()
        }
        val pipelineLayoutMaterial = Material(graphicsDevice)
        val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
        val transferContext = TransferContext(graphicsDevice)
        val sceneDepthTarget = DepthTarget(graphicsDevice, size = DepthTarget.DORMANT_SIZE, format = format)
            .also { target -> transferContext.runOneTimeCommands(target::clearToFar) }
        val maskTarget = DepthTarget(
            graphicsDevice,
            size = DepthTarget.DORMANT_SIZE,
            layers = MASK_TARGET_LAYER_COUNT,
            arrayed = true,
            format = format,
        ).also { target -> transferContext.runOneTimeCommands(target::clearToFar) }
        val sceneDepthPass = createDepthPass(graphicsDevice, pipelineLayoutMaterial, sceneDepthTarget)
        val maskPass = createDepthPass(graphicsDevice, pipelineLayoutMaterial, maskTarget)
        val primary = createPrimaryPipeline(graphicsDevice, swapchainManager, sceneRenderPass, pipelineLayoutMaterial)
        val features = createRenderFeatures(graphicsDevice, swapchainManager, sceneRenderPass, consumer)
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionNormalColor),
            renderFeatures = features,
            transferContext = transferContext,
            uiShaderPairs = createUiShaderPairs(),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            sceneDepthPass = sceneDepthPass,
            maskPass = maskPass,
        )
        return ResidencyFixture(
            renderer, sceneDepthPass, maskPass, primary, pipelineLayoutMaterial,
            sceneRenderPass, transferContext, swapchainManager, graphicsDevice,
        )
    }

    private fun createPrimaryPipeline(
        graphicsDevice: GraphicsDevice,
        swapchainManager: SwapchainManager,
        sceneRenderPass: Long,
        material: Material,
    ): RenderPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        material.descriptorSetLayout,
        runBlocking { packShaderPair("triangle") },
        VertexFormat.PositionNormalColor,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
    )

    private fun createRenderFeatures(
        graphicsDevice: GraphicsDevice,
        swapchainManager: SwapchainManager,
        sceneRenderPass: Long,
        consumer: RenderFeature<RenderFrameContext>,
    ): List<RenderFeature<VulkanRenderFrameContext>> = listOf(
        OpaqueRenderFeature(
            VulkanLinePass(
                LineRenderPipeline(
                    graphicsDevice,
                    swapchainManager,
                    sceneRenderPass,
                    runBlocking { packShaderPair("debug_line") },
                    MAX_FRAMES_IN_FLIGHT,
                ),
            ),
        ),
        consumer,
        UiRenderFeature(VulkanUiPass()),
    )

    private fun createUiShaderPairs(): UiShaderPairs = runBlocking {
        UiShaderPairs(
            quad = packShaderPair("ui_quad"),
            glyph = packShaderPair("ui_glyph"),
            texture = packShaderPair("ui_texture"),
            roundedQuad = packShaderPair("ui_rounded_quad"),
        )
    }

    private fun createDepthPass(
        graphicsDevice: GraphicsDevice,
        material: Material,
        target: DepthTarget,
    ): DepthPrePassFeature = DepthPrePassFeature(
        target,
        DepthOnlyPipeline(
            graphicsDevice,
            target.renderPass,
            material.descriptorSetLayout,
            runBlocking { packShaderPair(PackShaderSets.SceneDepth) },
            VertexFormat.PositionNormalColor,
            target.size,
            PackShaderSets.SceneDepth.vulkan.entryPoint(ShaderStage.VERTEX),
            PackShaderSets.SceneDepth.vulkan.entryPoint(ShaderStage.FRAGMENT),
        ),
    )

    private companion object {
        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
    }
}
