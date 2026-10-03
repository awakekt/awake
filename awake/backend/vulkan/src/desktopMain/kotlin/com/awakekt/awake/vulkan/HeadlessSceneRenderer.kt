/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/**
 * A windowless Vulkan renderer that can draw a lit, shadowed scene.
 *
 * [vulkanHeadlessUi]'s sibling, and the same reasoning for being public: this is how a scene is
 * captured without a window, and how it is compared against the other backend from a module that
 * can see neither backend's internals. The shadow lookup being mirrored on WebGPU for as long as it
 * was is what a cross-backend scene capture exists to prevent.
 *
 * `lit_shadow` with a real depth pre-pass, layered and arrayed at [DEFAULT_SHADOW_CASCADES]: the
 * shader declares `texture_depth_2d_array`, so a single-layer view is a validation error rather
 * than a dimmer picture.
 */
fun vulkanHeadlessScene(width: Int, height: Int): HeadlessRenderSession {
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.createHeadless()
    val swapchainManager = SwapchainManager(graphicsDevice, FRAMES_IN_FLIGHT)
    swapchainManager.createHeadless(width, height)
    val descriptorSetLayout = Material.createDescriptorSetLayout(graphicsDevice)
    val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
    val depthTarget = DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true)
    val scenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.LitShadow) },
        VertexFormat.PositionNormalColor,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        // Set 1: the shadow map's own descriptor set, beside the material's set 0.
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
    )
    val backCulledScenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.LitShadow) },
        VertexFormat.PositionNormalColor,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
        cullMode = com.awakekt.awake.vulkan.enums.VkCullModeFlagBits.VK_CULL_MODE_BACK_BIT,
    )
    val texturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.Textured) },
        VertexFormat.PositionNormalColorUv,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
    )
    val skinnedTexturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.SkinnedTextured) },
        VertexFormat.PositionNormalColorUvSkin,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
    )
    // The textured format's blended companions, as RenderPlan builds them.
    fun texturedCompanion(variant: PipelineVariant) = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.Textured) },
        VertexFormat.PositionNormalColorUv,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
        variant = variant,
    )
    val transparentTexturedPipeline = texturedCompanion(PipelineVariant.AlphaBlended)
    val additiveTexturedPipeline = texturedCompanion(PipelineVariant.AdditiveBlended)
    val instancedTexturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.InstancedTextured) },
        VertexFormat.PositionNormalColorUv,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
        variant = PipelineVariant.Instanced,
    )
    // The sprite pipeline RenderPlan builds for PipelineKey.Particle.
    val spritePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.Particle) },
        VertexFormat.PositionUv,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        variant = PipelineVariant.AlphaBlendedParticle,
    )
    val depthPrePass = DepthPrePassFeature(
        depthTarget,
        DepthOnlyPipeline(
            graphicsDevice,
            depthTarget.renderPass,
            descriptorSetLayout,
            runBlocking { spirvPair(PackShaderSets.ShadowDepth) },
            VertexFormat.PositionNormalColor,
            depthTarget.size,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
            cascadeCount = depthTarget.layers,
            framesInFlight = FRAMES_IN_FLIGHT,
        ),
        // As VulkanEngine builds it when a render plan opts particles into shadows.
        variantPipelines = mapOf(
            DepthCasterKind.Particle to DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { spirvPair(PackShaderSets.ParticleShadowDepth) },
                VertexFormat.PositionUv,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
                framesInFlight = FRAMES_IN_FLIGHT,
                variant = PipelineVariant.AlphaBlendedParticle,
            ),
        ),
        // As VulkanEngine builds it: every other opaque scene format casts through the same shader.
        formatPipelines = mapOf(
            VertexFormat.PositionNormalColorUv to DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { spirvPair(PackShaderSets.ShadowDepth) },
                VertexFormat.PositionNormalColorUv,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
                framesInFlight = FRAMES_IN_FLIGHT,
            ),
            // As VulkanEngine builds a skinned pipeline that names its depth shader.
            VertexFormat.PositionNormalColorUvSkin to DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { spirvPair(PackShaderSets.SkinnedTexturedShadowDepth) },
                VertexFormat.PositionNormalColorUvSkin,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
                framesInFlight = FRAMES_IN_FLIGHT,
            ),
        ),
        keyedVariantPipelines = mapOf(
            DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Masked) to DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { spirvPair(PackShaderSets.MaskedTexturedShadowDepth) },
                VertexFormat.PositionNormalColorUv,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
                framesInFlight = FRAMES_IN_FLIGHT,
            ),
            DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Masked) to DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { spirvPair(PackShaderSets.SkinnedMaskedTexturedShadowDepth) },
                VertexFormat.PositionNormalColorUvSkin,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
                framesInFlight = FRAMES_IN_FLIGHT,
            ),
        ),
        instancedFormatPipelines = mapOf(
            VertexFormat.PositionNormalColorUv to DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { spirvPair(PackShaderSets.InstancedTexturedShadowDepth) },
                VertexFormat.PositionNormalColorUv,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
                framesInFlight = FRAMES_IN_FLIGHT,
                variant = PipelineVariant.Instanced,
            ),
        ),
    )
    val linePipeline = LineRenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        runBlocking { spirvPair(EngineShaderSets.DebugLine) },
        FRAMES_IN_FLIGHT,
    )
    val transferContext = TransferContext(graphicsDevice)
    val renderer = VulkanRenderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(
            primary = scenePipeline,
            primaryFormat = scenePipeline.vertexFormat,
            byFormat = mapOf(
                VertexFormat.PositionNormalColorUv to texturedPipeline,
                VertexFormat.PositionNormalColorUvSkin to skinnedTexturedPipeline,
            ),
            instancedByFormat = mapOf(VertexFormat.PositionNormalColorUv to instancedTexturedPipeline),
            transparentByFormat = mapOf(VertexFormat.PositionNormalColorUv to transparentTexturedPipeline),
            additiveByFormat = mapOf(VertexFormat.PositionNormalColorUv to additiveTexturedPipeline),
            backCulledByFormat = mapOf(VertexFormat.PositionNormalColor to backCulledScenePipeline),
            particlePipelines = mapOf(VertexFormat.PositionUv to spritePipeline),
        ),
        renderFeatures = listOf(
            OpaqueRenderFeature(VulkanLinePass(linePipeline)),
            UiRenderFeature(VulkanUiPass()),
        ),
        depthPrePass = depthPrePass,
        transferContext = transferContext,
        uiShaderPairs = runBlocking { headlessUiShaderPairs() },
        maxFramesInFlight = FRAMES_IN_FLIGHT,
    )
    return object : HeadlessRenderSession {
        override val renderer = renderer

        override fun close() {
            renderer.destroy()
            scenePipeline.destroy()
            backCulledScenePipeline.destroy()
            texturedPipeline.destroy()
            skinnedTexturedPipeline.destroy()
            instancedTexturedPipeline.destroy()
            transparentTexturedPipeline.destroy()
            additiveTexturedPipeline.destroy()
            spritePipeline.destroy()
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, descriptorSetLayout.handle)
            transferContext.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            graphicsDevice.destroy()
        }
    }
}
