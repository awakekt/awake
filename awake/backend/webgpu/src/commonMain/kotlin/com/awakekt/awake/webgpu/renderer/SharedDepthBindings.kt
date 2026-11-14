/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey

/**
 * One depth-pass group for every opaque instanced caster that animates its vertices alike.
 *
 * A depth pass binds each caster's group 0, built from the draw's own uniform buffer, so a
 * scene of 190 instanced meshes bound 190 groups a pass, in every cascade. An instanced caster's
 * depth shader reads only `vertexAnimation` from that group: its matrices come from the
 * instance buffer, and an opaque caster samples no texture. So casters through the same depth
 * and scene pipeline whose vertex animation and clock are equal read the same values from any
 * of their buffers, and the first one's group serves the rest; `PassBindings` then skips the
 * repeated binds.
 *
 * Held for one batch: the first caster's buffer holds its values for as long as its own draw
 * does, and a later batch may write that slot for another draw.
 */
internal class SharedDepthBindings {
    @PublishedApi
    internal val shared = HashMap<Any, MaterialBinding>()

    /** Forgets every group, at the start of a batch. */
    fun clear() = shared.clear()

    /**
     * The group [request]'s depth draw binds: one shared with an earlier caster that reads the
     * same values, or [create]'s for this draw's own buffer. Inline, so [create] is no object per draw.
     */
    inline fun bindingFor(
        request: GpuDrawRequest,
        depthKey: DepthRenderKey,
        depthPipeline: Any,
        scenePipeline: Any?,
        wholeBlock: Boolean,
        create: () -> MaterialBinding?,
    ): MaterialBinding? {
        if (!sharesDepthGroup(depthKey)) return create()
        val key = keyOf(request, depthPipeline, scenePipeline, wholeBlock)
        return shared[key] ?: create()?.also { shared[key] = it }
    }

    /** [request]'s batch of alike casters: the clock is the frame time for an animated draw, 0 for a still one. */
    @PublishedApi
    internal fun keyOf(request: GpuDrawRequest, depthPipeline: Any, scenePipeline: Any?, wholeBlock: Boolean): Any {
        val animation = request.vertexAnimation
        val clock = if (animation.x != 0f) request.timeSeconds else 0f
        return Key(depthPipeline, scenePipeline, wholeBlock, animation.x, animation.y, animation.z, clock)
    }

    private data class Key(
        val depthPipeline: Any,
        val scenePipeline: Any?,
        val wholeBlock: Boolean,
        val amplitude: Float,
        val wavelength: Float,
        val speed: Float,
        val clock: Float,
    )

    internal companion object {
        /** Whether a caster of [depthKey] reads nothing per draw from its group but vertex animation. */
        fun sharesDepthGroup(depthKey: DepthRenderKey): Boolean =
            depthKey.kind == DepthCasterKind.Instanced && depthKey.alphaMode != AlphaMode.Masked
    }
}
