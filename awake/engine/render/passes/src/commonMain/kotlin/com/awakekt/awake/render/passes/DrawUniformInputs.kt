/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.textureAnimationClock
import com.awakekt.awake.render.passes.uniforms.vertexAnimationClock
import com.awakekt.awake.render.renderer.UniformFields

/**
 * The frame's share of every draw's uniform block -- camera, lights, shadow cascades, fog, debug view
 * and exposure -- as a [version] that moves only when one of them changes.
 *
 * With [DrawUniformInputs], it lets a backend skip packing a draw whose block would come out as it
 * did the frame before (#1452). Compared by value, not identity: the scene compiler builds a new
 * light block and cascade set every frame, the same ones when nothing moved. Render thread only.
 */
class FrameUniformInputs {
    /** Moves each time [update] is given a frame that differs from the one before. */
    var version: Int = 0
        private set

    private var last = FloatArray(0)
    private var next = FloatArray(0)

    /** Records this frame's inputs and returns [version], moved if any of them changed. */
    @Suppress("LongParameterList") // One per input of the frame's share of the block.
    fun update(
        viewProjection: Mat4,
        cameraEye: Vec3f,
        cameraForward: Vec3f,
        lightPayload: FloatArray,
        shadowCascades: GpuShadowCascadeData?,
        fogColor: Color,
        fogDensity: Float,
        debugView: GpuDebugView,
        exposure: Float,
    ): Int {
        val cascadeFloats = shadowCascades?.let { it.viewProjections.size * (MAT4_FLOATS + CASCADE_SCALARS) } ?: 0
        val size = MAT4_FLOATS + FRAME_SCALARS + lightPayload.size + cascadeFloats
        if (next.size != size) next = FloatArray(size)
        viewProjection.data.copyInto(next, 0)
        var at = MAT4_FLOATS
        at = next.put(at, cameraEye.x, cameraEye.y, cameraEye.z, cameraForward.x, cameraForward.y, cameraForward.z)
        at = next.put(at, fogColor.r, fogColor.g, fogColor.b, fogColor.a, fogDensity, exposure)
        at = next.put(at, debugView.code.toFloat(), debugView.depthRange, debugView.layer.toFloat())
        at = next.put(at, if (shadowCascades == null) 0f else 1f)
        lightPayload.copyInto(next, at)
        at += lightPayload.size
        shadowCascades?.let { cascades ->
            for (index in cascades.viewProjections.indices) {
                cascades.viewProjections[index].data.copyInto(next, at)
                at = next.put(
                    at + MAT4_FLOATS,
                    cascades.splitDistances[index],
                    cascades.depthScales[index],
                    cascades.worldExtents[index],
                    cascades.blendStartDistances[index],
                )
            }
        }
        if (!next.sameBitsAs(last)) {
            version += 1
            val swap = last
            last = next
            next = swap
        }
        return version
    }
}

/**
 * Everything one draw's uniform block is packed from, besides the frame's share, as it was the last
 * time a backend packed it into one uniform slot.
 *
 * A backend keeps one per slot: when [matches] says the draw now in the slot is packed from the same
 * inputs, under the same [FrameUniformInputs.version], the slot already holds its block and neither
 * packing nor uploading is needed. The clock counts only for a draw it animates, as the packers write
 * it (see `vertexAnimationClock` and `textureAnimationClock`).
 */
class DrawUniformInputs {
    private var frame = NO_FRAME
    private var format: VertexFormat? = null
    private val model = FloatArray(MAT4_FLOATS)
    private var extras = FloatArray(0)
    private val scalars = FloatArray(DRAW_SCALARS)
    private val scratch = FloatArray(DRAW_SCALARS)

    /**
     * Whether [draw], packed under [frameVersion] for a block of [materialUniformFloatCount] floats,
     * through a pipeline that does or does not cover alpha to coverage, gives the block last [record]ed.
     * [kind] tells apart the ways one slot can be packed, such as plain and instanced.
     */
    fun matches(draw: RenderDrawCommand, frameVersion: Int, materialUniformFloatCount: Int, alphaToCoverage: Boolean, kind: Int = 0): Boolean {
        // A negative version stands for a frame no FrameUniformInputs tracked: never the same as any.
        if (frameVersion < 0 || frameVersion != frame) return false
        draw.fillScalars(scratch, materialUniformFloatCount, alphaToCoverage, kind)
        return draw.mesh.format == format &&
            scratch.sameBitsAs(scalars) &&
            draw.model.data.sameBitsAs(model) &&
            draw.extraUniformFloats.sameBitsAs(extras)
    }

    /** Remembers [draw]'s inputs as the ones the slot's block was just packed from. */
    fun record(draw: RenderDrawCommand, frameVersion: Int, materialUniformFloatCount: Int, alphaToCoverage: Boolean, kind: Int = 0) {
        frame = frameVersion
        format = draw.mesh.format
        draw.fillScalars(scalars, materialUniformFloatCount, alphaToCoverage, kind)
        draw.model.data.copyInto(model)
        val source = draw.extraUniformFloats
        if (extras.size != source.size) extras = FloatArray(source.size)
        source.copyInto(extras)
    }

    /** Forgets what was recorded, so the next draw packs whatever it is. */
    fun clear() {
        frame = NO_FRAME
    }
}

/** Every per-draw scalar a packer reads, in a fixed order, into [out]. */
// Index by index rather than through a helper taking varargs: this runs once per draw per frame, and
// a vararg call allocates its array each time.
@Suppress("MagicNumber") // The slots of [DRAW_SCALARS], in order.
private fun RenderDrawCommand.fillScalars(out: FloatArray, materialUniformFloatCount: Int, alphaToCoverage: Boolean, kind: Int) {
    out[0] = vertexAnimation.x
    out[1] = vertexAnimation.y
    out[2] = vertexAnimation.z
    out[3] = vertexAnimationClock(vertexAnimation, timeSeconds)
    out[4] = textureClock()
    out[5] = 0f // The mesh's format, compared on its own.
    out[6] = materialUniformFloatCount.toFloat()
    out[7] = alphaMode.ordinal.toFloat()
    out[8] = alphaCutoff
    out[9] = if (transparent) 1f else 0f
    out[10] = if (additive) 1f else 0f
    out[11] = fadeOpacity
    out[12] = if (alphaToCoverage) 1f else 0f
    out[13] = kind.toFloat()
}

/** The clock the textured packer writes for these extras: the time only when their animation moves. */
internal fun RenderDrawCommand.textureClock(): Float {
    val layout = MaterialUniformLayouts.PbrTexturedMaterial
    val values = extraUniformFloats
    if (values.size < layout.total) return 0f
    val frames = layout.offsetOf(UniformFields.TextureFrames)
    val scroll = layout.offsetOf(UniformFields.TextureScroll)
    return textureAnimationClock(values[frames], values[frames + 1], values[frames + 2], values[scroll], values[scroll + 1], timeSeconds)
}

/** Once a frame only: the vararg array this allocates is not per draw. */
private fun FloatArray.put(at: Int, vararg values: Float): Int {
    values.copyInto(this, at)
    return at + values.size
}

/**
 * Bit for bit, so `-0` and `0`, and NaNs, are told apart as the GPU would see them. By index:
 * `indices.all { }` built a range and its iterator for every draw's three comparisons, garbage on
 * Kotlin/Wasm, which has no escape analysis to remove them (#1555).
 */
private fun FloatArray.sameBitsAs(other: FloatArray): Boolean {
    var index = 0
    val same = size == other.size
    while (same && index < size && this[index].toRawBits() == other[index].toRawBits()) index++
    return same && index == size
}

private const val MAT4_FLOATS = 16

/** Eye and forward (6), fog colour, density and exposure (6), debug view (3), whether there are cascades (1). */
private const val FRAME_SCALARS = 16

/** Each cascade's split, depth scale, world extent and blend start. */
private const val CASCADE_SCALARS = 4

/** See [fillScalars]. */
private const val DRAW_SCALARS = 14
private const val NO_FRAME = -1
