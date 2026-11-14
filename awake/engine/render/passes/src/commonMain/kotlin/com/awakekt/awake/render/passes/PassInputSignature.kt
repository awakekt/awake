/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.vertexAnimationClock
import com.awakekt.awake.render.renderer.RenderViewport

/**
 * Everything a scene compile reads, recorded frame after frame and compared with the frame before
 * as it is written, so [record] answers whether this frame's compile would come out as the last
 * one did. Two buffers swap each frame; nothing is allocated once they reach the scene's size.
 *
 * A draw contributes the clocks its shader would read, not the raw time, so a still draw stays
 * equal while time passes and an animated one does not. Floats compare bit for bit, references by
 * identity, and immutable values ([EnvironmentUniforms], a viewport, point lights) by equality.
 * A draw carrying per-instance palettes, colours or frames always differs: those are animated.
 */
internal class PassInputSignature {
    private val buffer = SignatureBuffer()

    /** Records this frame's inputs and answers whether they equal the last frame's. */
    @Suppress("LongParameterList") // What ScenePassCompiler.compile reads.
    fun record(
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
        light: SceneLight?,
        environment: EnvironmentUniforms,
        clipSpace: ClipSpace,
        aspect: Float,
        viewport: RenderViewport?,
        maskLayers: List<FloatArray>,
        postPassChain: ImagePassChain,
    ): Boolean = with(buffer) {
        begin()
        lens(lens)
        value(clipSpace)
        float(aspect)
        value(viewport)
        value(environment)
        value(postPassChain)
        light(light)
        float(maskLayers.size.toFloat())
        for (index in maskLayers.indices) floats(maskLayers[index])
        // A frame whose view, light or masks changed cannot be handed back, so its draws are left
        // unrecorded: a moving camera pays for these few values, not a pass over every draw. The
        // first still frame after it records them and the next one is handed back.
        if (!markFrame()) return false
        float(drawCalls.size.toFloat())
        for (index in drawCalls.indices) draw(drawCalls[index])
        return matchesPrevious()
    }

    /** Forgets the last frame, so the next [record] cannot match. */
    fun clear() = buffer.clear()

    private fun lens(lens: Lens) {
        vec(lens.eye)
        vec(lens.center)
        vec(lens.up)
        buffer.float(lens.fovYRadians)
        buffer.float(lens.near)
        buffer.float(lens.far)
        buffer.float(lens.orthoHalfHeight)
        buffer.ref(lens.projection)
    }

    private fun light(light: SceneLight?) {
        buffer.float(if (light == null) 0f else 1f)
        if (light == null) return
        vec(light.direction)
        vec(light.color)
        buffer.value(light.points)
        matrix(light.viewProjection)
    }

    private fun draw(draw: RenderDrawCommand) = with(buffer) {
        val models = draw.instanceModels
        reserve(DRAW_FLOATS + draw.extraUniformFloats.size + (models?.size ?: 0) * MATRIX_FLOATS, DRAW_REFS)
        ref(draw.mesh)
        ref(draw.material)
        matrix(draw.model)
        floats(draw.extraUniformFloats)
        vec(draw.vertexAnimation)
        float(vertexAnimationClock(draw.vertexAnimation, draw.timeSeconds))
        float(draw.textureClock())
        float(models?.size?.toFloat() ?: -1f)
        if (models != null) for (index in models.indices) matrix(models[index])
        if (draw.instanceJointPalettes != null || draw.instanceColors != null || draw.instanceFrames != null) differ()
        ref(draw.cullMode)
        ref(draw.alphaMode)
        float(draw.alphaCutoff)
        float(draw.fadeOpacity)
        float(draw.sortOrder.toFloat())
        float(draw.maskLayer.toFloat())
        float(draw.sortLayer.toFloat())
        float(draw.flags())
        val bounds = draw.worldBounds
        float(if (bounds == null) 0f else 1f)
        if (bounds != null) {
            vec(bounds.min)
            vec(bounds.max)
        }
    }

    private fun RenderDrawCommand.flags(): Float {
        var bits = 0
        if (transparent) bits = bits or 1
        if (shadowsOnly) bits = bits or 2
        if (additive) bits = bits or 4
        if (castsShadows) bits = bits or 8
        if (exemptFromCoverings) bits = bits or 16
        if (depthWrite) bits = bits or 32
        return bits.toFloat()
    }

    private fun vec(value: Vec3f) {
        buffer.float(value.x)
        buffer.float(value.y)
        buffer.float(value.z)
    }

    private fun matrix(value: Mat4?) {
        buffer.float(if (value == null) 0f else 1f)
        if (value != null) floats(value.data)
    }

    private fun floats(values: FloatArray) {
        buffer.float(values.size.toFloat())
        buffer.floats(values)
    }

    private companion object {
        /** A matrix as [matrix] writes it: whether there is one, its size, its 16 values. */
        const val MATRIX_FLOATS = 18

        /** Every float [draw] writes besides its extras and instance models: model, clocks, state and bounds. */
        const val DRAW_FLOATS = 48
        const val DRAW_REFS = 4
    }
}

/**
 * This frame's values beside the last frame's, in two buffers that swap each frame. Values are
 * written unconditionally and compared once, at the end, in one pass: floats bit for bit,
 * references by identity. An immutable value compared by equality is checked as it is written.
 */
private class SignatureBuffer {
    private var floats = FloatArray(INITIAL_FLOATS)
    private var previousFloats = FloatArray(INITIAL_FLOATS)
    private var refs = arrayOfNulls<Any>(INITIAL_REFS)
    private var previousRefs = arrayOfNulls<Any>(INITIAL_REFS)
    private var floatCount = NOT_RECORDED
    private var refCount = 0
    private var previousFloatCount = NOT_RECORDED
    private var previousRefCount = NOT_RECORDED
    private var matches = false
    private var frameFloatEnd = 0
    private var frameRefEnd = 0

    fun begin() {
        val lastFloats = floats
        floats = previousFloats
        previousFloats = lastFloats
        val lastRefs = refs
        refs = previousRefs
        previousRefs = lastRefs
        previousFloatCount = floatCount
        previousRefCount = refCount
        floatCount = 0
        refCount = 0
        matches = previousFloatCount != NOT_RECORDED
    }

    fun clear() {
        floatCount = NOT_RECORDED
        refCount = 0
    }

    /** Marks this frame as differing, whatever else it records. */
    fun differ() {
        matches = false
    }

    /** Room for [floatsNeeded] more floats and [refsNeeded] references, grown once a scene first needs it. */
    fun reserve(floatsNeeded: Int, refsNeeded: Int) {
        while (floatCount + floatsNeeded > floats.size) floats = floats.copyOf(floats.size * 2)
        while (refCount + refsNeeded > refs.size) refs = refs.copyOf(refs.size * 2)
    }

    fun float(value: Float) {
        if (floatCount == floats.size) floats = floats.copyOf(floats.size * 2)
        floats[floatCount++] = value
    }

    /** All of [values], copied in one move. */
    fun floats(values: FloatArray) {
        while (floatCount + values.size > floats.size) floats = floats.copyOf(floats.size * 2)
        values.copyInto(floats, floatCount)
        floatCount += values.size
    }

    /** By identity. */
    fun ref(value: Any?) {
        if (refCount == refs.size) refs = refs.copyOf(refs.size * 2)
        refs[refCount++] = value
    }

    /** By equality: stored as the last frame's when equal, so the identity pass sees the same object. */
    fun value(value: Any?) {
        val previous = if (refCount < previousRefCount) previousRefs[refCount] else null
        ref(if (refCount < previousRefCount && previous == value) previous else value)
    }

    /**
     * Ends the frame-level part of this frame and answers whether to go on to its draws: yes when
     * it equals the frame before's, or when there is no frame before to differ from. When it
     * differs, the frame ends here and the draws that would follow are never recorded.
     */
    fun markFrame(): Boolean {
        frameFloatEnd = floatCount
        frameRefEnd = refCount
        val recordedBefore = previousFloatCount != NOT_RECORDED
        val same = recordedBefore && matches && previousFloatCount >= floatCount && previousRefCount >= refCount &&
            same(0, floatCount, 0, refCount)
        if (!same) matches = false
        return same || !recordedBefore
    }

    /** Whether the whole frame, its draws after [markFrame], equals the frame before's. */
    fun matchesPrevious(): Boolean = matches && floatCount == previousFloatCount && refCount == previousRefCount &&
        same(frameFloatEnd, floatCount, frameRefEnd, refCount)

    /** Floats bit for bit in [floatsFrom] until [floatsTo], references by identity in [refsFrom] until [refsTo]. */
    private fun same(floatsFrom: Int, floatsTo: Int, refsFrom: Int, refsTo: Int): Boolean {
        var same = true
        var index = floatsFrom
        while (same && index < floatsTo) {
            same = floats[index].toRawBits() == previousFloats[index].toRawBits()
            index += 1
        }
        index = refsFrom
        while (same && index < refsTo) {
            same = refs[index] === previousRefs[index]
            index += 1
        }
        return same
    }

    private companion object {
        const val INITIAL_FLOATS = 4_096
        const val INITIAL_REFS = 1_024
        const val NOT_RECORDED = -1
    }
}
