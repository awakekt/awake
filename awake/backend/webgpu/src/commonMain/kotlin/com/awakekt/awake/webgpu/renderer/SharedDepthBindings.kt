/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.hasBindingGroup
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice

/**
 * One stable depth-pass group for every opaque instanced caster that animates its vertices alike.
 *
 * A depth pass binds each caster's group 0, built from a draw uniform buffer, so a scene of 190
 * instanced meshes bound 190 groups a pass in every cascade. An instanced caster's depth shader
 * reads only `vertexAnimation` from that group: its matrices come from the instance buffer, and an
 * opaque caster samples no texture.
 *
 * Previously this borrowed the first caster's buffer each batch, so whenever that caster entered or
 * left the frustum on a moving camera, every remaining instanced caster switched to another group-0
 * reference and forced WebGPU cascade render bundles to re-encode (#1621). On the standard
 * split-block path (`!wholeBlock`), each distinct `(depthPipeline, amplitude, wavelength, speed)`
 * now owns one tiny stable uniform buffer and bind group whose `clock` updates in place when
 * animated, keeping group-0 references invariant across camera movement, culling, and animation.
 * Legacy whole-block shaders still share the first caster's buffer per batch without per-draw
 * key allocations.
 */
internal class SharedDepthBindings(
    private val device: GPUDevice,
    private val uniformUploads: UniformUploads,
) {
    private val stableEntries = ArrayList<StableEntry>()
    private val batchEntries = ArrayList<BatchEntry>()
    private var batchCount = 0
    private var batchId = 0L

    private val scratch = FloatArray(DEPTH_BLOCK_FLOATS).apply {
        this[0] = 1f
        this[5] = 1f
        this[10] = 1f
        this[15] = 1f
    }

    /** Advances to a new preparation batch and resets whole-block per-batch entries. */
    fun beginBatch() {
        batchId += 1L
        for (i in 0 until batchCount) batchEntries[i].clear()
        batchCount = 0
    }

    /** Releases every owned shared uniform buffer. Call after depth pipelines close their bind groups. */
    fun destroy() {
        for (i in 0 until stableEntries.size) stableEntries[i].buffer.close()
        stableEntries.clear()
        for (i in 0 until batchCount) batchEntries[i].clear()
        batchCount = 0
    }

    /**
     * The group [request]'s depth draw binds: a stable shared group on the split-block path, a
     * per-batch shared group on the whole-block path, or [create]'s for a draw that cannot share.
     * Inline, so [create] allocates nothing per draw.
     */
    @Suppress("LongParameterList")
    inline fun bindingFor(
        request: GpuDrawRequest,
        depthKey: DepthRenderKey,
        depthFeature: DepthPrePassFeature,
        depthPipeline: DepthOnlyPipeline,
        scenePipeline: Any?,
        wholeBlock: Boolean,
        create: () -> MaterialBinding?,
    ): MaterialBinding? = when {
        !sharesDepthGroup(depthKey) -> create()
        !wholeBlock -> stableBinding(request, depthFeature, depthPipeline)
        else -> batchBinding(request, depthPipeline, scenePipeline, create)
    }

    @PublishedApi
    internal fun stableBinding(
        request: GpuDrawRequest,
        depthFeature: DepthPrePassFeature,
        depthPipeline: DepthOnlyPipeline,
    ): MaterialBinding? {
        if (!depthPipeline.handle.hasBindingGroup(0)) return null
        val animation = request.vertexAnimation
        val amplitude = animation.x
        val wavelength = animation.y
        val speed = animation.z
        val clock = if (amplitude != 0f) request.timeSeconds else 0f
        val found = findStableEntry(depthPipeline, amplitude, wavelength, speed, clock)
        return if (found != null) {
            updateClockIfNeeded(found, amplitude, wavelength, speed, clock)
            found.binding
        } else {
            createStableEntry(depthFeature, depthPipeline, amplitude, wavelength, speed, clock)?.binding
        }
    }

    private fun updateClockIfNeeded(
        entry: StableEntry,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
    ) {
        if (amplitude != 0f && entry.lastBatchId != batchId) {
            entry.lastBatchId = batchId
            if (entry.clock != clock) {
                entry.clock = clock
                stageAnimation(entry.buffer, amplitude, wavelength, speed, clock)
            }
        }
    }

    private fun findStableEntry(
        depthPipeline: DepthOnlyPipeline,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
    ): StableEntry? {
        for (i in 0 until stableEntries.size) {
            val entry = stableEntries[i]
            if (entry.matches(depthPipeline, amplitude, wavelength, speed, batchId, clock)) return entry
        }
        return null
    }

    private fun createStableEntry(
        depthFeature: DepthPrePassFeature,
        depthPipeline: DepthOnlyPipeline,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
    ): StableEntry? {
        val buffer = device.createBuffer(
            BufferDescriptor(
                size = (DEPTH_BLOCK_FLOATS * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )
        val binding = depthFeature.materialBinding(depthPipeline, buffer, wholeBlock = false)
        if (binding == null) {
            buffer.close()
            return null
        }
        stageAnimation(buffer, amplitude, wavelength, speed, clock)
        return StableEntry(depthPipeline, amplitude, wavelength, speed, buffer, binding, clock, batchId).also {
            stableEntries.add(it)
        }
    }

    private fun stageAnimation(buffer: GPUBuffer, amplitude: Float, wavelength: Float, speed: Float, clock: Float) {
        scratch[VERTEX_ANIMATION_OFFSET] = amplitude
        scratch[VERTEX_ANIMATION_OFFSET + 1] = wavelength
        scratch[VERTEX_ANIMATION_OFFSET + 2] = speed
        scratch[VERTEX_ANIMATION_OFFSET + 3] = clock
        uniformUploads.stage(buffer, scratch)
    }

    @PublishedApi
    internal inline fun batchBinding(
        request: GpuDrawRequest,
        depthPipeline: Any,
        scenePipeline: Any?,
        create: () -> MaterialBinding?,
    ): MaterialBinding? {
        val animation = request.vertexAnimation
        val clock = if (animation.x != 0f) request.timeSeconds else 0f
        return findBatchBinding(depthPipeline, scenePipeline, animation.x, animation.y, animation.z, clock)
            ?: create()?.also { storeBatch(depthPipeline, scenePipeline, animation.x, animation.y, animation.z, clock, it) }
    }

    @PublishedApi
    @Suppress("LongParameterList")
    internal fun findBatchBinding(
        depthPipeline: Any,
        scenePipeline: Any?,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
    ): MaterialBinding? {
        for (i in 0 until batchCount) {
            val entry = batchEntries[i]
            if (entry.matches(depthPipeline, scenePipeline, amplitude, wavelength, speed, clock)) {
                return entry.binding
            }
        }
        return null
    }

    @PublishedApi
    @Suppress("LongParameterList")
    internal fun storeBatch(
        depthPipeline: Any,
        scenePipeline: Any?,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
        binding: MaterialBinding,
    ) {
        val entry = if (batchCount < batchEntries.size) {
            batchEntries[batchCount++]
        } else {
            BatchEntry().also {
                batchEntries.add(it)
                batchCount++
            }
        }
        entry.set(depthPipeline, scenePipeline, amplitude, wavelength, speed, clock, binding)
    }

    private class StableEntry(
        val depthPipeline: DepthOnlyPipeline,
        val amplitude: Float,
        val wavelength: Float,
        val speed: Float,
        val buffer: GPUBuffer,
        val binding: MaterialBinding,
        var clock: Float,
        var lastBatchId: Long,
    ) {
        fun matches(
            depthPipeline: DepthOnlyPipeline,
            amplitude: Float,
            wavelength: Float,
            speed: Float,
            batchId: Long,
            clock: Float,
        ): Boolean = this.depthPipeline === depthPipeline &&
            this.amplitude == amplitude &&
            this.wavelength == wavelength &&
            this.speed == speed &&
            (amplitude == 0f || this.lastBatchId != batchId || this.clock == clock)
    }

    @PublishedApi
    internal class BatchEntry {
        var depthPipeline: Any? = null
        var scenePipeline: Any? = null
        var amplitude: Float = 0f
        var wavelength: Float = 0f
        var speed: Float = 0f
        var clock: Float = 0f
        var binding: MaterialBinding? = null

        fun matches(
            depthPipeline: Any,
            scenePipeline: Any?,
            amplitude: Float,
            wavelength: Float,
            speed: Float,
            clock: Float,
        ): Boolean = this.depthPipeline === depthPipeline &&
            this.scenePipeline === scenePipeline &&
            this.amplitude == amplitude &&
            this.wavelength == wavelength &&
            this.speed == speed &&
            this.clock == clock

        fun set(
            depthPipeline: Any,
            scenePipeline: Any?,
            amplitude: Float,
            wavelength: Float,
            speed: Float,
            clock: Float,
            binding: MaterialBinding,
        ) {
            this.depthPipeline = depthPipeline
            this.scenePipeline = scenePipeline
            this.amplitude = amplitude
            this.wavelength = wavelength
            this.speed = speed
            this.clock = clock
            this.binding = binding
        }

        fun clear() {
            depthPipeline = null
            scenePipeline = null
            binding = null
        }
    }

    internal companion object {
        private val DEPTH_BLOCK_FLOATS = MaterialUniformLayouts.LitShadowDraw.total
        private val VERTEX_ANIMATION_OFFSET = MaterialUniformLayouts.LitShadowDraw.offsetOf(UniformFields.VertexAnimation)

        /** Whether a caster of [depthKey] reads nothing per draw from its group but vertex animation. */
        fun sharesDepthGroup(depthKey: DepthRenderKey): Boolean =
            depthKey.kind == DepthCasterKind.Instanced && depthKey.alphaMode != AlphaMode.Masked
    }
}
