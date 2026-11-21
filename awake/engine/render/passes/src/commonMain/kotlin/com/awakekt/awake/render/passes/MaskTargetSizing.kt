/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/** The largest mask target edge: the fixed size every mask target had before it followed the scene (#1709). */
const val MASK_TARGET_MAX_SIZE: Int = 2048

/**
 * The smallest mask target edge, and the step its edges take, so a few pixels of resize reallocate
 * nothing. A mask target starts at it: the first frame grows it to the scene.
 */
const val MASK_TARGET_MIN_SIZE: Int = 64

/** How far past what the scene needs a mask target may stay before a presented frame shrinks it. */
private const val MASK_TARGET_SLACK = 256

/**
 * The edge of the square mask target, for the scene it is drawn and read over (#1709). The mask is
 * read by texture coordinate, so it needs only as many texels as the scene has pixels across its
 * longer side: at 1093x480 that is a 1152 edge, a third of the 2048 square's texels to clear, store
 * and hold. Never more than [MASK_TARGET_MAX_SIZE].
 *
 * One instance per renderer. An offscreen render needs its own size now, so the target grows for it at
 * once and never shrinks; a presented frame then keeps the larger of its own size and any offscreen
 * render's since the last presented frame. A view rendered offscreen every frame at another size thus
 * settles on one size instead of reallocating, and waiting for the device, every frame.
 */
class MaskTargetSizing {
    private var offscreenEdge = 0

    /** The edge a presented frame of a [width] x [height] scene uses, given the [current] one. */
    fun presented(current: Int, width: Int, height: Int): Int {
        val needed = maxOf(edgeFor(width, height), offscreenEdge)
        offscreenEdge = 0
        return if (current in needed..needed + MASK_TARGET_SLACK) current else needed
    }

    /** The edge an offscreen render of a [width] x [height] scene uses, given the [current] one: never smaller. */
    fun offscreen(current: Int, width: Int, height: Int): Int {
        val needed = edgeFor(width, height)
        offscreenEdge = maxOf(offscreenEdge, needed)
        return maxOf(current, needed)
    }

    private fun edgeFor(width: Int, height: Int): Int {
        val longer = maxOf(width, height, 1)
        val stepped = (longer + MASK_TARGET_MIN_SIZE - 1) / MASK_TARGET_MIN_SIZE * MASK_TARGET_MIN_SIZE
        return stepped.coerceAtMost(MASK_TARGET_MAX_SIZE)
    }
}
