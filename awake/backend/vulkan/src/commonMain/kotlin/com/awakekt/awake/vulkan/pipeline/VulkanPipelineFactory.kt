/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.PipelineFactory
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkCullModeFlagBits
import com.awakekt.awake.vulkan.enums.VkPolygonMode
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.swapchain.SwapchainManager

/**
 * Turns a backend-neutral [PipelineSpec] into a Vulkan [RenderPipeline].
 *
 * The whole Vulkan-specific half of pipeline creation. Which pipelines exist and which
 * companions each one needs is `buildPipelineTable`'s job, shared with WebGPU -- this only
 * translates one already-decided description into `VkPolygonMode`/`VkCullModeFlagBits` and the
 * constructor call.
 *
 * [renderPass] is built once by the caller (via `createSceneRenderPass`) and reused for every
 * pipeline: they all share the same swapchain/depth attachment shape, so one pass covers all of
 * them rather than one per pipeline. [loadShaders] is injected so this file needs no knowledge
 * of `readResourceBytes`'s IO.
 */
class VulkanPipelineFactory(
    private val graphicsDevice: GraphicsDevice,
    private val swapchainManager: SwapchainManager,
    private val renderPass: Long,
    private val descriptorSetLayout: DescriptorSetLayoutHandle,
    /** Extra descriptor set layouts per pipeline family, appended after [descriptorSetLayout].
     * Today's only entry is the skinned-instanced joint-palette layout. */
    private val extraDescriptorSetLayouts: Map<PipelineKey, List<DescriptorSetLayoutHandle>> = emptyMap(),
    /** Which engine-owned groups each pipeline family declares a set layout for. Empty for a
     * family that reads none; see [VulkanPipelineHandle.engineBoundSemantics]. */
    private val engineSemanticsByKey: Map<PipelineKey, Set<BindingSemantic>> = emptyMap(),
    /** How many copies of a spec-owned uniform block to allocate -- the engine's frames in
     * flight. Unused by a spec whose `uniforms` is null, which is every mesh pipeline. */
    private val framesInFlight: Int = 1,
    /**
     * Engine-owned set layouts by semantic, for a pipeline the maps above do not list: a content
     * feature attached after start gets each one its shader declares a group for. Gaps take
     * [emptySetLayout], so every declared group keeps its `BindingLayout` slot.
     */
    private val declaredEngineSetLayouts: Map<BindingSemantic, DescriptorSetLayoutHandle> = emptyMap(),
    private val emptySetLayout: () -> DescriptorSetLayoutHandle = { error("No empty set layout was supplied.") },
    private val loadShaders: suspend (spec: PipelineSpec) -> ShaderPair,
) : PipelineFactory<RenderPipeline> {

    /** The engine-owned sets [spec]'s shader declares, as set layouts from slot 1 and their semantics. */
    private fun declaredEngineSets(spec: PipelineSpec): Pair<List<DescriptorSetLayoutHandle>, Set<BindingSemantic>> {
        val bySlot = declaredEngineSetLayouts
            .filterKeys { spec.bindingLayout.slot(it) in spec.bindingsByGroup }
            .entries.associate { (semantic, layout) -> spec.bindingLayout.slot(semantic) to (semantic to layout) }
        if (bySlot.isEmpty()) return emptyList<DescriptorSetLayoutHandle>() to emptySet()
        val layouts = (1..bySlot.keys.max()).map { slot -> bySlot[slot]?.second ?: emptySetLayout() }
        return layouts to bySlot.values.map { it.first }.toSet()
    }

    override suspend fun create(key: PipelineKey, spec: PipelineSpec): RenderPipeline {
        // A family the engine listed at start keeps exactly what it was given.
        val declared = if (key in extraDescriptorSetLayouts || key in engineSemanticsByKey) null else declaredEngineSets(spec)
        return RenderPipeline(
            graphicsDevice,
            swapchainManager,
            renderPass,
            descriptorSetLayout,
            loadShaders(spec),
            spec.vertexFormat,
            spec.vertexEntryPoint,
            spec.fragmentEntryPoint,
            polygonMode = if (spec.wireframe) {
                VkPolygonMode.VK_POLYGON_MODE_LINE
            } else {
                VkPolygonMode.VK_POLYGON_MODE_FILL
            },
            cullMode = when (spec.cullMode) {
                CullMode.Back -> VkCullModeFlagBits.VK_CULL_MODE_BACK_BIT
                // Front-culling has no pipeline on either backend and no `VK_CULL_MODE_FRONT_BIT` in
                // the generated enum, so a Front mesh draws unculled -- exactly what it did before
                // this factory existed. Stated here rather than left as a non-exhaustive `when`:
                // the fan-out never asks for Front today, and when something does, this is the line
                // that has to change.
                CullMode.None, CullMode.Front -> VkCullModeFlagBits.VK_CULL_MODE_NONE
            },
            frontFace = spec.frontFace,
            variant = spec.variant,
            extraDescriptorSetLayouts = extraDescriptorSetLayouts[key] ?: declared?.first.orEmpty(),
            uniforms = spec.uniforms,
            framesInFlight = framesInFlight,
            engineBoundSemantics = engineSemanticsByKey[key] ?: declared?.second.orEmpty(),
            bindingLayout = spec.bindingLayout,
            materialBindings = spec.materialBindings,
        )
    }
}
