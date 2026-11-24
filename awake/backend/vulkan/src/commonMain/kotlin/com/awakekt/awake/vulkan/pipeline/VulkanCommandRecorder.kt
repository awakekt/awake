/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.render.command.BufferHandle as RenderBufferHandle

/**
 * This backend's half of the [CommandRecorder] port: turns shared render-feature calls into
 * real Vulkan commands with redundant-bind elision and single-JNI draw submission.
 *
 * Pipeline, set-0 material descriptor, vertex buffer 0, and index buffer changes are coalesced
 * and submitted together with `vkCmdDrawIndexed` / `vkCmdDraw` via [VulkanCommands.vkCmdDrawIndexedWithState]
 * / [VulkanCommands.vkCmdDrawWithState], cutting per-draw JNI transitions from 4-5 down to 1 while
 * skipping unchanged handles altogether.
 *
 * [commandBuffer] is retargeted per render pass via [beginPass] rather than allocating a new
 * recorder per frame. Safe because a `Renderer` records from one thread.
 */
@Suppress("TooManyFunctions")
internal class VulkanCommandRecorder(
    /** Where every draw recorded through this port is counted; the owning `Renderer`'s. */
    val stats: RenderStatsCounter,
) : CommandRecorder {
    var commandBuffer: Long = 0

    /**
     * Descriptor sets the engine owns and rebinds per frame, by what they mean.
     *
     * Mirrored into a flat [engineSetHandles] array on assignment so [bindPipeline] iterates zero
     * `Map.Entry` objects on hot pipeline switches.
     */
    var engineDescriptorSets: Map<BindingSemantic, Long> = emptyMap()
        set(value) {
            field = value
            val entries = value.entries.filter { it.value != 0L }
            engineSemantics = Array(entries.size) { entries[it].key }
            engineSetHandles = LongArray(entries.size) { entries[it].value }
        }

    private var engineSemantics: Array<BindingSemantic> = emptyArray()
    private var engineSetHandles: LongArray = LongArray(0)

    /** Set by [bindPipeline]. Vulkan needs the bound pipeline's layout to bind a descriptor set. */
    private var boundPipelineLayout: Long = 0L
    private var boundPipeline: Long = 0L
    private var boundSet0: Long = 0L
    private val boundExtraSets = LongArray(MAX_DESCRIPTOR_SETS)
    private val boundVertexBuffers = LongArray(MAX_VERTEX_BINDINGS)
    private var boundIndexBuffer: Long = 0L

    private var pendingPipeline: Long = 0L
    private var pendingSet0: Long = 0L
    private var pendingVertexBuffer0: Long = 0L
    private var pendingIndexBuffer: Long = 0L

    private var bindingLayout: BindingLayout = BindingLayout.Standard
    private var drawsTriangles: Boolean = true

    /** Diagnostic counters for tests verifying state-bind elision. */
    internal var elidedPipelineBinds: Long = 0L
        private set
    internal var elidedDescriptorBinds: Long = 0L
        private set
    internal var elidedBufferBinds: Long = 0L
        private set
    internal var batchedDrawCalls: Long = 0L
        private set

    /**
     * Retargets this recorder to [targetCommandBuffer] at the start of a render pass and clears
     * cached Vulkan bind state so the first draw in the pass binds everything fresh.
     */
    fun beginPass(targetCommandBuffer: Long) {
        commandBuffer = targetCommandBuffer
        boundPipeline = 0L
        boundPipelineLayout = 0L
        boundSet0 = 0L
        boundExtraSets.fill(0L)
        boundVertexBuffers.fill(0L)
        boundIndexBuffer = 0L
        pendingPipeline = 0L
        pendingSet0 = 0L
        pendingVertexBuffer0 = 0L
        pendingIndexBuffer = 0L
    }

    /** Resets diagnostic bind-elision counters, for deterministic tests. */
    internal fun resetDiagnostics() {
        elidedPipelineBinds = 0L
        elidedDescriptorBinds = 0L
        elidedBufferBinds = 0L
        batchedDrawCalls = 0L
    }

    override fun bindPipeline(pipeline: PipelineHandle) {
        val vulkanPipeline = pipeline as VulkanPipelineHandle
        val nextPipeline = vulkanPipeline.pipelineHandle
        val nextLayout = vulkanPipeline.pipelineLayoutHandle
        if (nextPipeline == boundPipeline && pendingPipeline == 0L) {
            elidedPipelineBinds += 1L
        } else if (nextPipeline == pendingPipeline) {
            elidedPipelineBinds += 1L
        } else {
            pendingPipeline = nextPipeline
        }
        val layoutChanged = nextLayout != boundPipelineLayout
        boundPipelineLayout = nextLayout
        bindingLayout = vulkanPipeline.bindingLayout
        drawsTriangles = vulkanPipeline.drawsTriangles
        if (layoutChanged) {
            boundSet0 = 0L
            pendingSet0 = 0L
            boundExtraSets.fill(0L)
        }
        bindEngineDescriptorSets(vulkanPipeline, layoutChanged)
    }

    private fun bindEngineDescriptorSets(pipeline: VulkanPipelineHandle, layoutChanged: Boolean) {
        val semantics = engineSemantics
        val handles = engineSetHandles
        for (index in semantics.indices) {
            val handle = handles[index]
            val semantic = semantics[index]
            if (handle != 0L && semantic in pipeline.engineBoundSemantics) {
                val slot = bindingLayout.slot(semantic)
                if (!layoutChanged && slot < boundExtraSets.size && boundExtraSets[slot] == handle) {
                    elidedDescriptorBinds += 1L
                } else {
                    VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, boundPipelineLayout, slot, handle)
                    if (slot < boundExtraSets.size) boundExtraSets[slot] = handle
                }
            }
        }
    }

    override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
        val slot = bindingLayout.slot(semantic)
        val handle = (binding as VulkanMaterialBinding).descriptorSetHandle
        if (slot == 0) {
            val effectiveSet0 = if (pendingSet0 != 0L) pendingSet0 else boundSet0
            if (handle == effectiveSet0) {
                elidedDescriptorBinds += 1L
            } else {
                pendingSet0 = handle
            }
            return
        }
        if (slot < boundExtraSets.size && boundExtraSets[slot] == handle) {
            elidedDescriptorBinds += 1L
            return
        }
        VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, boundPipelineLayout, slot, handle)
        if (slot < boundExtraSets.size) boundExtraSets[slot] = handle
    }

    override fun bindVertexBuffer(binding: Int, buffer: RenderBufferHandle) {
        val handle = (buffer as VulkanBufferBinding).handle
        if (binding == 0) {
            val effectiveVbo0 = if (pendingVertexBuffer0 != 0L) pendingVertexBuffer0 else boundVertexBuffers[0]
            if (handle == effectiveVbo0) {
                elidedBufferBinds += 1L
            } else {
                pendingVertexBuffer0 = handle
            }
            return
        }
        if (binding in boundVertexBuffers.indices && boundVertexBuffers[binding] == handle) {
            elidedBufferBinds += 1L
            return
        }
        VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, binding, handle, 0L)
        if (binding in boundVertexBuffers.indices) boundVertexBuffers[binding] = handle
    }

    override fun bindIndexBuffer(buffer: RenderBufferHandle) {
        val handle = (buffer as VulkanBufferBinding).handle
        val effectiveIbo = if (pendingIndexBuffer != 0L) pendingIndexBuffer else boundIndexBuffer
        if (handle == effectiveIbo) {
            elidedBufferBinds += 1L
        } else {
            pendingIndexBuffer = handle
        }
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        VulkanCommands.vkCmdSetScissorRect(commandBuffer, x, y, width, height)
    }

    override fun draw(vertexCount: Int, instanceCount: Int) {
        VulkanCommands.vkCmdDrawWithState(
            commandBuffer = commandBuffer,
            pipeline = pendingPipeline,
            pipelineLayout = boundPipelineLayout,
            descriptorSet0 = pendingSet0,
            vertexBuffer0 = pendingVertexBuffer0,
            vertexCount = vertexCount,
            instanceCount = instanceCount,
        )
        commitPendingState(includeIndex = false)
        batchedDrawCalls += 1L
        stats.recordDraw(vertexCount, instanceCount, drawsTriangles)
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int) {
        drawIndexed(indexCount, instanceCount, firstIndex = 0)
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int) {
        VulkanCommands.vkCmdDrawIndexedWithState(
            commandBuffer = commandBuffer,
            pipeline = pendingPipeline,
            pipelineLayout = boundPipelineLayout,
            descriptorSet0 = pendingSet0,
            vertexBuffer0 = pendingVertexBuffer0,
            indexBuffer = pendingIndexBuffer,
            indexCount = indexCount,
            instanceCount = instanceCount,
            firstIndex = firstIndex,
        )
        commitPendingState(includeIndex = true)
        batchedDrawCalls += 1L
        stats.recordDraw(indexCount, instanceCount, drawsTriangles)
    }

    private fun commitPendingState(includeIndex: Boolean) {
        if (pendingPipeline != 0L) {
            boundPipeline = pendingPipeline
            pendingPipeline = 0L
        }
        if (pendingSet0 != 0L) {
            boundSet0 = pendingSet0
            pendingSet0 = 0L
        }
        if (pendingVertexBuffer0 != 0L) {
            boundVertexBuffers[0] = pendingVertexBuffer0
            pendingVertexBuffer0 = 0L
        }
        if (includeIndex && pendingIndexBuffer != 0L) {
            boundIndexBuffer = pendingIndexBuffer
            pendingIndexBuffer = 0L
        }
    }

    private companion object {
        const val MAX_DESCRIPTOR_SETS = 8
        const val MAX_VERTEX_BINDINGS = 8
    }
}

/**
 * The offsets of one vertex buffer bound at offset 0, as every vertex buffer in this backend is.
 * Shared, so a bind allocates nothing: the depth pass bound each caster's buffers with a new array,
 * once per draw per cascade (#1742). Only ever read.
 */
internal val ZERO_OFFSET = longArrayOf(0L)

/** A pipeline the shared layer can bind. Implemented by every real pipeline class here, which is
 * why the recorder above can cast unconditionally. */
interface VulkanPipelineHandle : PipelineHandle {
    /** The `VkPipeline` that `vkCmdBindPipeline` binds. */
    val pipelineHandle: Long

    /** The `VkPipelineLayout` that descriptor sets are bound against. */
    val pipelineLayoutHandle: Long

    /**
     * Which engine-owned groups this pipeline's layout actually declares.
     *
     * Binding one a pipeline did not declare a set layout for is a validation error, so the
     * recorder binds only what is named here. Empty for a pipeline that reads none.
     */
    val engineBoundSemantics: Set<BindingSemantic>
}

/** A descriptor set the shared layer can bind at some set index. Implemented by the already-
 * allocated per-frame/per-draw slot objects (`Material`'s uniform slots, `PerFrameUniformSlots
 * .Slot`, `SkinnedInstanceBuffer`'s frame resources), so resolving one per draw allocates
 * nothing. */
interface VulkanMaterialBinding : MaterialBinding {
    /** The `VkDescriptorSet` bound for this binding. */
    val descriptorSetHandle: Long
}

/**
 * A `VkBuffer` the shared layer can bind. Built once per buffer at resource-creation time, not
 * per draw -- [asArray] is the single-element array `vkCmdBindVertexBuffers` takes, kept here so
 * the per-frame bind path allocates nothing (the hand-written `Mesh.bind` it replaces built a
 * fresh one every call).
 *
 * @property handle The `VkBuffer` handle. The binding does not own it: whoever created the buffer
 * destroys it.
 */
class VulkanBufferBinding(val handle: Long) : RenderBufferHandle {
    val asArray = longArrayOf(handle)
}
