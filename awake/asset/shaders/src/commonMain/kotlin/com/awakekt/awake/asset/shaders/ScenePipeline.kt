/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineRequest
import com.awakekt.awake.render.pipeline.PipelineVariant

/**
 * One pipeline an app needs, declared in backend-neutral terms.
 *
 * The app-facing half of the pipeline registry. It holds a whole [ShaderSet] rather
 * than resolved paths, so one declaration serves both backends and each engine picks its own
 * half when it builds the request.
 *
 * This replaces the per-feature engine constructor parameters -- `instancedShaderSet`,
 * `skinnedInstancedShaderSet`, `particleShaderSet`, `additionalPipelines`, `wireframeSupport`.
 * Each of those was a branch the engine had to take, so adding a render feature meant editing
 * both engines, both bootstraps and both `createBackendResources` bodies. A feature declares
 * itself instead, and the engine stops predicting.
 *
 * @property key Which pipeline family this is -- also the lookup key in the built table.
 * @property shaders Both backends' shader stages; the engine reads only its own.
 * @property vertexFormat The mesh layout this pipeline draws.
 * @property variant Structural shape -- instancing, blending, depth write. See [PipelineVariant].
 * @property bindingLayout Which descriptor groups the pipeline declares. [BindingLayout.Standard]
 * is the per-frame plus per-material pair every scene pipeline had before content features.
 * @property materialBindings Optional custom descriptor group bindings for the material pass.
 * @property usesMaterialGroup Whether the pipeline binds the per-material descriptor group.
 * @property buildWireframe Also build the line-topology companion. Only meaningful for
 * [PipelineKey.Primary] and [PipelineKey.Format]; instanced and particle pipelines have never
 * had one.
 * @property buildBackCulled Also build the back-face-culled companion, opted into per entity via
 * `MeshRenderer.cullMode`.
 * @property buildTransparent Also build the alpha-blended companion, opted into per draw via
 * `RenderDrawCommand.transparent`.
 * @property depthShaders How an instanced or skinned pipeline's draws cast into the shadow map.
 * Null leaves them to the plan's `depthPrePassVariants`, which cover only the primary format.
 * @property buildAdditive Also build the additive companion, for transparent draws that set
 * `RenderDrawCommand.additive`.
 */
data class ScenePipeline(
    val key: PipelineKey,
    val shaders: ShaderSet,
    val vertexFormat: VertexFormat,
    val variant: PipelineVariant = PipelineVariant.Opaque,
    val bindingLayout: BindingLayout = BindingLayout.Standard,
    val materialBindings: GroupBindings? = null,
    val usesMaterialGroup: Boolean = true,
    val buildWireframe: Boolean = false,
    val buildBackCulled: Boolean = false,
    val buildTransparent: Boolean = false,
    val depthShaders: ShaderSet? = null,
    val buildAdditive: Boolean = false,
)

/**
 * Whether the plan's depth shader, built for this pipeline's vertex format, draws its meshes into
 * the shadow map: an opaque pipeline for a format other than [primaryFormat], without joints. That
 * shader reads no joint palette, so a skinned mesh casts through its own depth variant or not at all.
 */
fun ScenePipeline.castsWithPrimaryDepthShader(primaryFormat: VertexFormat): Boolean =
    variant == PipelineVariant.Opaque && vertexFormat != primaryFormat && !vertexFormat.isSkinned

/**
 * The depth shader this pipeline's skinned meshes cast through, or null when it draws no skinned
 * meshes or names none. A skinned caster needs its own shader, one that reads its joint palette.
 */
fun ScenePipeline.skinnedDepthShaders(): ShaderSet? =
    depthShaders?.takeIf { variant == PipelineVariant.Opaque && vertexFormat.isSkinned }

/**
 * These declarations as build requests, reading each one's [stages] half.
 *
 * @receiver What the app declared.
 * @param stages Picks the backend's own half of a [ShaderSet] -- `ShaderSet::vulkan` or
 * `ShaderSet::webGpu`. The only backend-specific part of the conversion.
 * @return Requests ready for `buildPipelineTable` or `PipelineRegistry.register`.
 */
fun List<ScenePipeline>.toRequests(stages: (ShaderSet) -> ShaderStages): List<PipelineRequest> =
    map { declared ->
        val selectedStages = stages(declared.shaders)
        PipelineRequest(
            key = declared.key,
            spec = selectedStages.spec(
                vertexFormat = declared.vertexFormat,
                variant = declared.variant,
                bindingLayout = declared.bindingLayout,
                materialBindings = declared.materialBindings,
                usesMaterialGroup = declared.usesMaterialGroup,
                bindingsByGroup = selectedStages.bindingsByGroup,
                bindingsMetadataAvailable = selectedStages.bindingsMetadataAvailable,
            ),
            buildWireframe = declared.buildWireframe,
            buildBackCulled = declared.buildBackCulled,
            buildTransparent = declared.buildTransparent,
            buildAdditive = declared.buildAdditive,
        )
    }
