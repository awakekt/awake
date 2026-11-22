/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey

/**
 * One depth-pass descriptor set for every opaque instanced caster that animates its vertices alike:
 * Vulkan's twin of the WebGPU preparer's table (#1555, #1742).
 *
 * A depth pass binds each caster's material set, the one its scene draw binds, so a scene of 190
 * instanced meshes bound 190 sets a pass, in every cascade. An instanced caster's depth shader reads
 * only `vertexAnimation` from that set: its matrices come from the instance buffer, and an opaque
 * caster samples no texture. So casters through the same depth and scene pipeline whose vertex
 * animation and clock are equal read the same values from any of their sets, and the first one's
 * serves the rest; the depth pass then skips binding a set it already has bound.
 *
 * Pooled in a small linear-scan list so steady-state frames allocate neither key objects nor
 * closures per instanced caster. Held for one batch, which writes one frame slot: the first
 * caster's uniforms hold their values for as long as its own draw does, and a later batch may
 * write that slot for another draw.
 */
internal class SharedDepthBindings {
    @PublishedApi
    internal val entries = ArrayList<Entry>()

    @PublishedApi
    internal var count = 0

    /** Forgets every group, at the start of a batch, keeping the pooled entries. */
    fun clear() {
        for (i in 0 until count) entries[i].clear()
        count = 0
    }

    /**
     * The group [request]'s depth draw binds: one shared with an earlier caster that reads the
     * same values, or [create]'s for this draw's own buffer. Inline, so [create] allocates nothing.
     */
    @Suppress("LongParameterList")
    inline fun bindingFor(
        request: GpuDrawRequest,
        depthKey: DepthRenderKey,
        depthPipeline: Any,
        scenePipeline: Any?,
        wholeBlock: Boolean,
        create: () -> MaterialBinding?,
    ): MaterialBinding? {
        if (!sharesDepthGroup(depthKey)) return create()
        val animation = request.vertexAnimation
        // The clock the block carries: the frame time for an animated draw, 0 for a still one.
        val clock = if (animation.x != 0f) request.timeSeconds else 0f
        return findBinding(depthPipeline, scenePipeline, wholeBlock, animation.x, animation.y, animation.z, clock)
            ?: create()?.also { store(depthPipeline, scenePipeline, wholeBlock, animation.x, animation.y, animation.z, clock, it) }
    }

    @PublishedApi
    @Suppress("LongParameterList")
    internal fun findBinding(
        depthPipeline: Any,
        scenePipeline: Any?,
        wholeBlock: Boolean,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
    ): MaterialBinding? {
        for (i in 0 until count) {
            val entry = entries[i]
            if (entry.matches(depthPipeline, scenePipeline, wholeBlock, amplitude, wavelength, speed, clock)) {
                return entry.binding
            }
        }
        return null
    }

    @PublishedApi
    @Suppress("LongParameterList")
    internal fun store(
        depthPipeline: Any,
        scenePipeline: Any?,
        wholeBlock: Boolean,
        amplitude: Float,
        wavelength: Float,
        speed: Float,
        clock: Float,
        binding: MaterialBinding,
    ) {
        val entry = if (count < entries.size) {
            entries[count++]
        } else {
            Entry().also {
                entries.add(it)
                count++
            }
        }
        entry.set(depthPipeline, scenePipeline, wholeBlock, amplitude, wavelength, speed, clock, binding)
    }

    @PublishedApi
    internal class Entry {
        var depthPipeline: Any? = null
        var scenePipeline: Any? = null
        var wholeBlock: Boolean = false
        var amplitude: Float = 0f
        var wavelength: Float = 0f
        var speed: Float = 0f
        var clock: Float = 0f
        var binding: MaterialBinding? = null

        @Suppress("LongParameterList")
        fun matches(
            depthPipeline: Any,
            scenePipeline: Any?,
            wholeBlock: Boolean,
            amplitude: Float,
            wavelength: Float,
            speed: Float,
            clock: Float,
        ): Boolean = this.depthPipeline === depthPipeline &&
            this.scenePipeline === scenePipeline &&
            this.wholeBlock == wholeBlock &&
            this.amplitude == amplitude &&
            this.wavelength == wavelength &&
            this.speed == speed &&
            this.clock == clock

        @Suppress("LongParameterList")
        fun set(
            depthPipeline: Any,
            scenePipeline: Any?,
            wholeBlock: Boolean,
            amplitude: Float,
            wavelength: Float,
            speed: Float,
            clock: Float,
            binding: MaterialBinding,
        ) {
            this.depthPipeline = depthPipeline
            this.scenePipeline = scenePipeline
            this.wholeBlock = wholeBlock
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
        /** Whether a caster of [depthKey] reads nothing per draw from its group but vertex animation. */
        fun sharesDepthGroup(depthKey: DepthRenderKey): Boolean =
            depthKey.kind == DepthCasterKind.Instanced && depthKey.alphaMode != AlphaMode.Masked
    }
}
