/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

/**
 * What one render pass already has bound, so [WebGpuCommandRecorder] skips a call that binds the
 * same object again.
 *
 * On the web every encoder call crosses into JS. A static scene of 1,500 draws, in a main pass, a
 * depth pre-pass and three shadow cascades, re-bound its mesh buffers and its shadow group per draw:
 * about 10,260 of the 22,600 calls a frame. Each `bind*` returns whether the call must be sent,
 * and records the object as bound when it must.
 *
 * The state lives as long as its recorder, which is built per pass, so it never outlives a
 * `beginRenderPass`/`end`. [forget] runs on every pipeline bind: conservative, since WebGPU keeps
 * groups and buffers across `setPipeline`, but it also covers the UI and image-pass recorders that
 * bind on the same pass encoder directly. Each of them sets its own pipeline first, and the scene
 * binds a pipeline again before it draws after them. The engine binds groups with no dynamic
 * offsets and buffers whole, so the object alone says whether a binding changed. A group or slot
 * past WebGPU's guaranteed limits is never elided.
 */
internal class PassBindings {
    private val groups = arrayOfNulls<Any>(MAX_GROUPS)
    private val vertexBuffers = arrayOfNulls<Any>(MAX_VERTEX_BUFFERS)
    private var indexBuffer: Any? = null

    /** Forgets everything bound, as for a fresh pass. */
    fun forget() {
        groups.fill(null)
        vertexBuffers.fill(null)
        indexBuffer = null
    }

    /** Whether binding [bindGroup] at [group] must be sent. */
    fun bindGroup(group: Int, bindGroup: Any): Boolean = bind(groups, group, bindGroup)

    /** Whether binding [buffer] to vertex [slot] must be sent. */
    fun bindVertexBuffer(slot: Int, buffer: Any): Boolean = bind(vertexBuffers, slot, buffer)

    /** Whether binding [buffer] as the index buffer must be sent. */
    fun bindIndexBuffer(buffer: Any): Boolean {
        if (indexBuffer === buffer) return false
        indexBuffer = buffer
        return true
    }

    private fun bind(bound: Array<Any?>, slot: Int, value: Any): Boolean {
        if (slot !in bound.indices) return true
        val changed = bound[slot] !== value
        bound[slot] = value
        return changed
    }

    private companion object {
        /** WebGPU's guaranteed `maxBindGroups`. */
        const val MAX_GROUPS = 4

        /** WebGPU's guaranteed `maxVertexBuffers`. */
        const val MAX_VERTEX_BUFFERS = 8
    }
}
