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
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes2d.UiRenderFeature
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
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the mask pre-pass avoids full-screen clear+store passes on inactive clean layers
 * and skips [COVERING_EXEMPT_MASK_LAYER] when no attached [RenderFeature] consumes it.
 */
class RendererMaskPassSkipTest {

    @Test
    fun skipsCoveringExemptMaskAndCleanInactiveLayersWithoutConsumer() {
        val (renderer, maskPass) = buildRenderer(exemptConsumer = null)
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            val exemptOnly = passInput(maskLayers = listOf(COVERING_EXEMPT_MASK_LAYER))
            val outlineAndExempt = passInput(maskLayers = listOf(0, COVERING_EXEMPT_MASK_LAYER))

            // Frame 1: no consumer needs the covering-exempt layer, so layers are only
            // transitioned out of UNDEFINED once (3 layers).
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(MASK_TARGET_LAYER_COUNT.toLong(), maskPass.passesRecorded)

            // Frame 2: all layers are already initialized and clean -> zero mask passes.
            val beforeIdle = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(0L, maskPass.passesRecorded - beforeIdle)

            // Frame 3: layer 0 becomes active while layer 2 is still filtered -> 1 mask pass, not 3.
            val beforeOutline = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, outlineAndExempt)
            assertEquals(1L, maskPass.passesRecorded - beforeOutline)

            // Frame 4: layer 0 leaves -> clears only previously-dirty layer 0 once (1 pass).
            val beforeClear = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(1L, maskPass.passesRecorded - beforeClear)

            // Frame 5: back to clean idle -> zero mask passes.
            val beforeCleanAgain = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(0L, maskPass.passesRecorded - beforeCleanAgain)
        } finally {
            target.destroy()
            renderer.destroy()
        }
    }

    @Test
    fun recordsCoveringExemptMaskOnlyWhileConsumerNeedsIt() {
        var needsExempt = true
        val consumer = object : RenderFeature<RenderFrameContext> {
            override val pass = RenderPassSlot.Scene
            override fun needsCoveringExemptMask(environment: GpuEnvironmentState): Boolean = needsExempt
            override fun recordCommands(context: RenderFrameContext) = Unit
            override fun destroy() = Unit
        }
        val (renderer, maskPass) = buildRenderer(exemptConsumer = consumer)
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            val exemptOnly = passInput(maskLayers = listOf(COVERING_EXEMPT_MASK_LAYER))

            // Frame 1: records layer 2 and initializes layers 0 and 1 once (3 total).
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(MASK_TARGET_LAYER_COUNT.toLong(), maskPass.passesRecorded)

            // Frame 2: consumer still needs layer 2 -> records only layer 2 (1 pass, not 3).
            val beforeActive = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(1L, maskPass.passesRecorded - beforeActive)

            // Frame 3: consumer turns off -> clears previously-dirty layer 2 once (1 pass).
            needsExempt = false
            val beforeDisable = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(1L, maskPass.passesRecorded - beforeDisable)

            // Frame 4: consumer stays off -> zero mask passes.
            val beforeIdle = maskPass.passesRecorded
            renderer.gpuPassExecutor.renderToTexture(target, exemptOnly)
            assertEquals(0L, maskPass.passesRecorded - beforeIdle)
        } finally {
            target.destroy()
            renderer.destroy()
        }
    }

    private fun passInput(maskLayers: List<Int>): GpuPassInput = GpuPassInput(
        viewProjection = Mat4(),
        cameraEye = Vec3f(0f, 0f, 5f),
        passUniforms = FloatArray(0),
        resolvedPath = true,
        maskPasses = maskLayers.map { layer ->
            GpuSubPass(
                target = null,
                targetLayer = layer,
                viewProjection = Mat4(),
            )
        },
    )

    private fun buildRenderer(exemptConsumer: RenderFeature<RenderFrameContext>?): Pair<Renderer, DepthPrePassFeature> {
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.createHeadless()
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
        val pipelineLayoutMaterial = Material(graphicsDevice)
        val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
        val transferContext = TransferContext(graphicsDevice)

        val maskPass = createMaskPass(graphicsDevice, pipelineLayoutMaterial)
        val primary = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            pipelineLayoutMaterial.descriptorSetLayout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val features = buildList {
            add(
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
            )
            if (exemptConsumer != null) add(exemptConsumer)
            add(UiRenderFeature(VulkanUiPass()))
        }
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionNormalColor),
            renderFeatures = features,
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
            maskPass = maskPass,
        )
        return renderer to maskPass
    }

    private fun createMaskPass(graphicsDevice: GraphicsDevice, material: Material): DepthPrePassFeature {
        val maskTarget = DepthTarget(
            graphicsDevice,
            size = TARGET_SIZE,
            layers = MASK_TARGET_LAYER_COUNT,
            arrayed = true,
        )
        return DepthPrePassFeature(
            maskTarget,
            DepthOnlyPipeline(
                graphicsDevice,
                maskTarget.renderPass,
                material.descriptorSetLayout,
                runBlocking { packShaderPair(PackShaderSets.SceneDepth) },
                VertexFormat.PositionNormalColor,
                maskTarget.size,
                PackShaderSets.SceneDepth.vulkan.entryPoint(ShaderStage.VERTEX),
                PackShaderSets.SceneDepth.vulkan.entryPoint(ShaderStage.FRAGMENT),
            ),
        )
    }

    private companion object {
        const val TARGET_SIZE = 64
        const val MAX_FRAMES_IN_FLIGHT = 1
    }
}
