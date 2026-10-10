/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.uniforms.MAX_SHADOW_TARGET_LAYERS
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.webgpu.application.WebGpuContentFeatureGpu
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.device.withGpuTiming
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineFactory
import com.awakekt.awake.webgpu.pipeline.WebGpuShaderResolver
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import com.awakekt.awake.webgpu.renderer.Renderer as WebGpuRenderer

/**
 * A WebGPU renderer over wgpu-native that can draw a lit, shadowed scene.
 *
 * [webGpuHeadlessUi]'s sibling. Built the same way `WebGpuEngine` builds its own: `lit_shadow` as
 * the scene pipeline, and a depth pre-pass whose target is layered and arrayed at
 * [MAX_SHADOW_TARGET_LAYERS], because the shader declares `texture_depth_2d_array`. Content
 * features attach through the renderer's `ContentFeatureHost`.
 *
 * **This is wgpu-native, not a browser** -- see [webGpuHeadlessUi] for what that leaves uncovered.
 */
fun webGpuHeadlessScene(): HeadlessRenderSession = runBlocking {
    val context = glfwContextRenderer(
        width = 1,
        height = 1,
        title = "awake-render-parity-scene",
        onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
    )
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.create(context.wgpuContext.withGpuTiming { error -> println("WGPU UNCAPTURED: $error") })
    val swapchainManager = SwapchainManager(graphicsDevice, FRAMES_IN_FLIGHT)
    swapchainManager.create()
    val scenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.LitShadow),
        // One WGSL file carries both stages on this backend.
        ByteArray(0),
        VertexFormat.PositionNormalColor,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.LitShadow.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.LitShadow.webGpu.bindingsMetadataAvailable,
    )
    val backCulledScenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.LitShadow),
        ByteArray(0),
        VertexFormat.PositionNormalColor,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.LitShadow.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.LitShadow.webGpu.bindingsMetadataAvailable,
        cullMode = io.ygdrasil.webgpu.GPUCullMode.Back,
    )
    val atlasPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.Sprite),
        ByteArray(0),
        VertexFormat.PositionUv,
        "vertexMain",
        "fragmentMain",
        variant = PipelineVariant.AlphaBlended,
        bindingsByGroup = PackShaderSets.Sprite.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.Sprite.webGpu.bindingsMetadataAvailable,
    )
    val texturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.Textured),
        ByteArray(0),
        VertexFormat.PositionNormalColorUv,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.Textured.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.Textured.webGpu.bindingsMetadataAvailable,
    )
    val skinnedTexturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.SkinnedTextured),
        ByteArray(0),
        VertexFormat.PositionNormalColorUvSkin,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.SkinnedTextured.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.SkinnedTextured.webGpu.bindingsMetadataAvailable,
    )

    // The opaque formats' wireframe-overlay companions, as buildPipelineTable builds them.
    suspend fun edgeCompanion(shaders: ShaderSet, format: VertexFormat) = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(shaders),
        ByteArray(0),
        format,
        "vertexMain",
        "fragmentMain",
        topology = GPUPrimitiveTopology.LineList,
        variant = PipelineVariant.EdgeOverlay,
        bindingsByGroup = shaders.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = shaders.webGpu.bindingsMetadataAvailable,
    )
    val edgePipelines = mapOf(
        VertexFormat.PositionNormalColor to edgeCompanion(PackShaderSets.LitShadow, VertexFormat.PositionNormalColor),
        VertexFormat.PositionNormalColorUv to edgeCompanion(PackShaderSets.Textured, VertexFormat.PositionNormalColorUv),
        VertexFormat.PositionNormalColorUvSkin to edgeCompanion(PackShaderSets.SkinnedTextured, VertexFormat.PositionNormalColorUvSkin),
    )

    // The textured format's blended companions, as RenderPlan builds them.
    suspend fun texturedCompanion(variant: PipelineVariant) = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.Textured),
        ByteArray(0),
        VertexFormat.PositionNormalColorUv,
        "vertexMain",
        "fragmentMain",
        variant = variant,
        bindingsByGroup = PackShaderSets.Textured.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.Textured.webGpu.bindingsMetadataAvailable,
    )
    val transparentTexturedPipeline = texturedCompanion(PipelineVariant.AlphaBlended)
    val additiveTexturedPipeline = texturedCompanion(PipelineVariant.AdditiveBlended)
    val instancedTexturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.InstancedTextured),
        ByteArray(0),
        VertexFormat.PositionNormalColorUv,
        "vertexMain",
        "fragmentMain",
        variant = PipelineVariant.Instanced,
        bindingsByGroup = PackShaderSets.InstancedTextured.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.InstancedTextured.webGpu.bindingsMetadataAvailable,
    )
    // The sprite pipeline RenderPlan builds for PipelineKey.Particle.
    val spritePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.Particle),
        ByteArray(0),
        VertexFormat.PositionUv,
        "vertexMain",
        "fragmentMain",
        variant = PipelineVariant.AlphaBlendedParticle,
        materialBindings = GroupBindings.ParticleMaterial,
        bindingsByGroup = PackShaderSets.Particle.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.Particle.webGpu.bindingsMetadataAvailable,
    )
    val depthPrePass = DepthPrePassFeature(
        depthTarget = DepthTarget(graphicsDevice, layers = MAX_SHADOW_TARGET_LAYERS, arrayed = true, comparison = true),
        depthOnlyPipeline = DepthOnlyPipeline(
            graphicsDevice = graphicsDevice,
            shaderCode = wgsl(PackShaderSets.ShadowDepth),
            vertexFormat = VertexFormat.PositionNormalColor,
            cascadeCount = MAX_SHADOW_TARGET_LAYERS,
            bindingsByGroup = PackShaderSets.ShadowDepth.webGpu.bindingsByGroup,
            bindingsMetadataAvailable = PackShaderSets.ShadowDepth.webGpu.bindingsMetadataAvailable,
        ),
        // As WebGpuEngine builds it when a render plan opts particles into shadows.
        variantPipelines = mapOf(
            DepthCasterKind.Particle to DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = wgsl(PackShaderSets.ParticleShadowDepth),
                vertexFormat = VertexFormat.PositionUv,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                variant = PipelineVariant.AlphaBlendedParticle,
                bindingsByGroup = PackShaderSets.ParticleShadowDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.ParticleShadowDepth.webGpu.bindingsMetadataAvailable,
            ),
        ),
        // As WebGpuEngine builds it: every other opaque scene format casts through the same shader.
        formatPipelines = mapOf(
            VertexFormat.PositionNormalColorUv to DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = wgsl(PackShaderSets.ShadowDepth),
                vertexFormat = VertexFormat.PositionNormalColorUv,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                bindingsByGroup = PackShaderSets.ShadowDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.ShadowDepth.webGpu.bindingsMetadataAvailable,
            ),
            // As WebGpuEngine builds a skinned pipeline that names its depth shader.
            VertexFormat.PositionNormalColorUvSkin to DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = wgsl(PackShaderSets.SkinnedTexturedShadowDepth),
                vertexFormat = VertexFormat.PositionNormalColorUvSkin,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                bindingsByGroup = PackShaderSets.SkinnedTexturedShadowDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.SkinnedTexturedShadowDepth.webGpu.bindingsMetadataAvailable,
            ),
        ),
        keyedVariantPipelines = mapOf(
            DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Masked) to DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = wgsl(PackShaderSets.MaskedTexturedShadowDepth),
                vertexFormat = VertexFormat.PositionNormalColorUv,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                bindingsByGroup = PackShaderSets.MaskedTexturedShadowDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.MaskedTexturedShadowDepth.webGpu.bindingsMetadataAvailable,
            ),
            DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Masked) to DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = wgsl(PackShaderSets.SkinnedMaskedTexturedShadowDepth),
                vertexFormat = VertexFormat.PositionNormalColorUvSkin,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                bindingsByGroup = PackShaderSets.SkinnedMaskedTexturedShadowDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.SkinnedMaskedTexturedShadowDepth.webGpu.bindingsMetadataAvailable,
            ),
        ),
        instancedFormatPipelines = mapOf(
            VertexFormat.PositionNormalColorUv to DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = wgsl(PackShaderSets.InstancedTexturedShadowDepth),
                vertexFormat = VertexFormat.PositionNormalColorUv,
                cascadeCount = MAX_SHADOW_TARGET_LAYERS,
                variant = PipelineVariant.Instanced,
                bindingsByGroup = PackShaderSets.InstancedTexturedShadowDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = PackShaderSets.InstancedTexturedShadowDepth.webGpu.bindingsMetadataAvailable,
            ),
        ),
    )
    val linePipeline = LineRenderPipeline(graphicsDevice, swapchainManager, wgsl(EngineShaderSets.DebugLine))
    // Content features attach as WebGpuEngine attaches them, casting into the cascades.
    val contentPipelines = PipelineRegistry(WebGpuPipelineFactory(graphicsDevice, swapchainManager, WebGpuShaderResolver()))
    val attacher = ContentFeatureAttacher(WebGpuContentFeatureGpu(graphicsDevice, contentPipelines, depthPrePass))
    val renderer = WebGpuRenderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(
            primary = scenePipeline,
            primaryFormat = VertexFormat.PositionNormalColor,
            byFormat = mapOf(
                VertexFormat.PositionNormalColorUv to texturedPipeline,
                VertexFormat.PositionUv to atlasPipeline,
                VertexFormat.PositionNormalColorUvSkin to skinnedTexturedPipeline,
            ),
            instancedByFormat = mapOf(VertexFormat.PositionNormalColorUv to instancedTexturedPipeline),
            transparentByFormat = mapOf(VertexFormat.PositionNormalColorUv to transparentTexturedPipeline, VertexFormat.PositionUv to atlasPipeline),
            additiveByFormat = mapOf(VertexFormat.PositionNormalColorUv to additiveTexturedPipeline),
            backCulledByFormat = mapOf(VertexFormat.PositionNormalColor to backCulledScenePipeline),
            particlePipelines = mapOf(VertexFormat.PositionUv to spritePipeline),
            edgesByFormat = edgePipelines,
        ),
        lineRenderPipeline = linePipeline,
        uiShaderSources = UiShaderSources(
            quad = wgsl(EngineShaderSets.UiQuad),
            glyph = wgsl(EngineShaderSets.UiGlyph),
            texture = wgsl(EngineShaderSets.UiTexture),
            roundedQuad = wgsl(EngineShaderSets.UiRoundedQuad),
            targetComposite = wgsl(EngineShaderSets.UiTargetComposite),
        ),
        maxFramesInFlight = FRAMES_IN_FLIGHT,
        renderFeatures = listOf(
            attacher.beforeGeometry,
            OpaqueRenderFeature(WebGpuLinePass(linePipeline)),
            attacher.afterGeometry,
            UiRenderFeature(WebGpuUiPass()),
        ),
        depthPrePass = depthPrePass,
    )
    renderer.contentFeatureHost = attacher
    object : HeadlessRenderSession {
        override val renderer = renderer

        override fun close() {
            renderer.destroy()
            scenePipeline.destroy()
            backCulledScenePipeline.destroy()
            texturedPipeline.destroy()
            atlasPipeline.destroy()
            skinnedTexturedPipeline.destroy()
            instancedTexturedPipeline.destroy()
            transparentTexturedPipeline.destroy()
            additiveTexturedPipeline.destroy()
            spritePipeline.destroy()
            edgePipelines.values.forEach { it.destroy() }
            contentPipelines.destroyAll { it.destroy() }
            attacher.releaseAll()
            graphicsDevice.destroy()
        }
    }
}
