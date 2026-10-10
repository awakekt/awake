/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.castsWithPrimaryDepthShader
import com.awakekt.awake.asset.shaders.keyedCasterLayout
import com.awakekt.awake.asset.shaders.skinnedDepthShaders
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.texture.DepthTarget
import com.awakekt.awake.asset.shaders.ShaderStage as ShaderProgramStage

/**
 * A depth pass over every kind of caster [plan] has a depth shader for: [primary] for the primary
 * format, and the plan's depth variants for instanced, skinned, particle, per-format and masked
 * casters, each placed by the pass's own matrix.
 *
 * The shadow cascades and the camera's scene depth are both this pass, into their own targets. A
 * caster missing from the scene depth reads, to every effect that samples it, as whatever lies
 * behind it: depth fog fogged batched meshes as if they were the ground behind them.
 *
 * @param graphicsDevice The device the pipelines are built on.
 * @param plan The render plan, which names a depth shader for each kind of caster.
 * @param target The depth target the pass draws into.
 * @param primary The primary format's depth pipeline.
 * @param primaryFormat The primary pipeline's vertex format.
 * @param cascadeCount The target's layers, one pass matrix each.
 * @param shadowBias Whether the pipelines bias depth against shadow acne. The scene depth must not:
 *   biased, it sits behind the surface the scene pass drew.
 */
@Suppress("LongParameterList") // A pass is its device, plan, target, primary pipeline and two settings.
internal suspend fun casterDepthPass(
    graphicsDevice: GraphicsDevice,
    plan: RenderPlan,
    target: DepthTarget,
    primary: DepthOnlyPipeline,
    primaryFormat: VertexFormat,
    cascadeCount: Int,
    shadowBias: Boolean,
): DepthPrePassFeature {
    suspend fun pipeline(shaders: ShaderSet, format: VertexFormat, variant: PipelineVariant = PipelineVariant.Opaque) =
        DepthOnlyPipeline(
            graphicsDevice = graphicsDevice,
            shaderCode = shaders.wgsl(),
            vertexFormat = format,
            vertexEntryPoint = shaders.webGpu.entryPoint(ShaderProgramStage.VERTEX),
            fragmentEntryPoint = shaders.webGpu.entryPoint(ShaderProgramStage.FRAGMENT),
            cascadeCount = cascadeCount,
            variant = variant,
            bindingsByGroup = shaders.webGpu.bindingsByGroup,
            bindingsMetadataAvailable = shaders.webGpu.bindingsMetadataAvailable,
            shadowBias = shadowBias,
        )

    val variants = plan.depthPrePassVariants
    val casterShaders = plan.depthPrePassShaderSet
    val formatPipelines = buildMap {
        // Every other opaque scene format casts through the same shader, when the plan has one.
        if (casterShaders != null) {
            plan.scenePipelines
                .filter { it.castsWithPrimaryDepthShader(primaryFormat) }
                .forEach { put(it.vertexFormat, pipeline(casterShaders, it.vertexFormat)) }
        }
        // A skinned pipeline casts through its own depth shader, which reads its joint palette.
        plan.scenePipelines.forEach { scenePipeline ->
            val shaders = scenePipeline.skinnedDepthShaders() ?: return@forEach
            put(scenePipeline.vertexFormat, pipeline(shaders, scenePipeline.vertexFormat))
        }
    }
    val keyedPipelines = buildMap {
        plan.depthPrePassKeyedVariants.forEach { (key, variant) ->
            val (format, pipelineVariant) = key.keyedCasterLayout() ?: return@forEach
            put(key, pipeline(variant, format, pipelineVariant))
        }
    }
    // Each instanced scene pipeline past the primary format casts through its own depth shader.
    val instancedFormatPipelines = buildMap {
        plan.scenePipelines.filter { it.key is PipelineKey.InstancedFormat }.forEach { scenePipeline ->
            val shaders = scenePipeline.depthShaders ?: return@forEach
            put(scenePipeline.vertexFormat, pipeline(shaders, scenePipeline.vertexFormat, PipelineVariant.Instanced))
        }
    }
    val variantPipelines = buildMap {
        variants[DepthCasterKind.Instanced]?.let {
            put(DepthCasterKind.Instanced, pipeline(it, VertexFormat.PositionNormalColor, PipelineVariant.Instanced))
        }
        variants[DepthCasterKind.Skinned]?.let { put(DepthCasterKind.Skinned, pipeline(it, VertexFormat.PositionNormalColorSkin)) }
        variants[DepthCasterKind.SkinnedInstanced]?.let {
            put(DepthCasterKind.SkinnedInstanced, pipeline(it, VertexFormat.PositionNormalColorSkin, PipelineVariant.Instanced))
        }
        variants[DepthCasterKind.Particle]?.let {
            put(DepthCasterKind.Particle, pipeline(it, VertexFormat.PositionUv, PipelineVariant.AlphaBlendedParticle))
        }
    }
    return DepthPrePassFeature(
        depthTarget = target,
        depthOnlyPipeline = primary,
        formatPipelines = formatPipelines,
        keyedVariantPipelines = keyedPipelines,
        instancedFormatPipelines = instancedFormatPipelines,
        variantPipelines = variantPipelines,
    )
}
