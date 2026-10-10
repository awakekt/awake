/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.entryPoint
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.webgpu.application.casterDepthPass
import com.awakekt.awake.webgpu.application.wgsl
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The scene-depth pass draws every kind of caster the shadow pass does, built the way
 * `WebGpuEngine` builds it. It used to have a pipeline for the primary format alone, so a batched,
 * skinned or textured mesh was missing from the scene depth, and depth fog fogged it as if it were
 * whatever stood behind it.
 */
class WebGpuSceneDepthCastersTest {

    @Test
    fun theSceneDepthPassHasAPipelineForEveryKindOfCaster() = runBlocking {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-scene-depth-casters",
            onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        try {
            val sceneDepth = PackShaderSets.SceneDepth
            suspend fun primary() = DepthOnlyPipeline(
                graphicsDevice = graphicsDevice,
                shaderCode = sceneDepth.wgsl(),
                vertexFormat = VertexFormat.PositionNormalColor,
                vertexEntryPoint = sceneDepth.webGpu.entryPoint(ShaderStage.VERTEX),
                fragmentEntryPoint = sceneDepth.webGpu.entryPoint(ShaderStage.FRAGMENT),
                bindingsByGroup = sceneDepth.webGpu.bindingsByGroup,
                bindingsMetadataAvailable = sceneDepth.webGpu.bindingsMetadataAvailable,
            )
            val pass = casterDepthPass(
                graphicsDevice = graphicsDevice,
                plan = PLAN,
                target = DepthTarget(graphicsDevice),
                primary = primary(),
                primaryFormat = VertexFormat.PositionNormalColor,
                cascadeCount = 1,
                shadowBias = false,
            )
            CASTERS.forEach { (kind, format) ->
                assertNotNull(pass.pipelineFor(kind, format), "the scene depth draws no $kind caster in $format")
            }

            // The pass as it was built before: the primary format alone.
            val primaryOnly = DepthPrePassFeature(DepthTarget(graphicsDevice), primary())
            assertNull(primaryOnly.pipelineFor(DepthCasterKind.Instanced, VertexFormat.PositionNormalColor))
        } finally {
            graphicsDevice.destroy()
        }
    }

    private companion object {
        val CASTERS = listOf(
            DepthCasterKind.Ordinary to VertexFormat.PositionNormalColor,
            DepthCasterKind.Instanced to VertexFormat.PositionNormalColor,
            DepthCasterKind.Skinned to VertexFormat.PositionNormalColorSkin,
            DepthCasterKind.Ordinary to VertexFormat.PositionNormalColorUv,
        )

        /** The production plan's depth passes, with the batched and skinned casters a showcase scene has. */
        val PLAN = RenderPlan(
            primary = ScenePipeline(
                PipelineKey.Primary,
                PackShaderSets.LitShadow,
                VertexFormat.PositionNormalColor,
                materialBindings = GroupBindings.UniformOnlyMaterial,
            ),
            depthPrePassShaderSet = PackShaderSets.ShadowDepth,
            depthPrePassVariants = mapOf(
                DepthCasterKind.Instanced to PackShaderSets.InstancedShadowDepth,
                DepthCasterKind.Skinned to PackShaderSets.SkinnedShadowDepth,
            ),
            sceneDepthShaderSet = PackShaderSets.SceneDepth,
            scenePipelines = listOf(
                ScenePipeline(
                    PipelineKey.Format(VertexFormat.PositionNormalColorUv),
                    PackShaderSets.Textured,
                    VertexFormat.PositionNormalColorUv,
                ),
            ),
        )
    }
}
