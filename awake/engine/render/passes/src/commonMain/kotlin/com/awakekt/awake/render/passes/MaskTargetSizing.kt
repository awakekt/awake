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

/** Packs a rectangular depth target's [width] and [height] into one allocation-free [Long]. */
fun packMaskExtent(width: Int, height: Int): Long =
    (width.toLong() shl 32) or (height.toLong() and 0xFFFF_FFFFL)

/** Extracts the width from a packed extent returned by [MaskTargetSizing.presentedExtent] or [MaskTargetSizing.offscreenExtent]. */
fun maskExtentWidth(extent: Long): Int = (extent ushr 32).toInt()

/** Extracts the height from a packed extent returned by [MaskTargetSizing.presentedExtent] or [MaskTargetSizing.offscreenExtent]. */
fun maskExtentHeight(extent: Long): Int = extent.toInt()

/**
 * Sizes the scene-depth and mask targets to the scene they are drawn and read over (#1709). Both are
 * read by texture coordinate, so each axis only needs as many texels as the scene has pixels on that
 * side, stepped in [MASK_TARGET_MIN_SIZE] increments up to [MASK_TARGET_MAX_SIZE]: at 1093x480 that
 * is 1152x512 (0.59M texels), under a sixth of the original 2048 square (4.19M) and 2.25x smaller
 * than an 1152 square (1.33M).
 *
 * One instance per renderer. An offscreen render needs its own size now, so each axis grows for it
 * at once and never shrinks; a presented frame then keeps the larger of its own size and any
 * offscreen render's since the last presented frame. A view rendered offscreen every frame at
 * another size thus settles on one size instead of reallocating, and waiting for the device, every
 * frame.
 */
class MaskTargetSizing {
    private var offscreenEdge = 0
    private var offscreenWidth = 0
    private var offscreenHeight = 0

    /** The square edge a presented frame of a [width] x [height] scene uses, given the [current] one. */
    fun presented(current: Int, width: Int, height: Int): Int {
        val needed = maxOf(edgeFor(width, height), offscreenEdge)
        offscreenEdge = 0
        return settleAxis(current, needed)
    }

    /** The square edge an offscreen render of a [width] x [height] scene uses, given the [current] one: never smaller. */
    fun offscreen(current: Int, width: Int, height: Int): Int {
        val needed = edgeFor(width, height)
        offscreenEdge = maxOf(offscreenEdge, needed)
        return maxOf(current, needed)
    }

    /**
     * The rectangular extent (packed via [packMaskExtent]) a presented frame of a [width] x [height]
     * scene uses, given [currentWidth] x [currentHeight]. Each axis settles independently with
     * [MASK_TARGET_SLACK] hysteresis so small resizes do not reallocate.
     */
    fun presentedExtent(currentWidth: Int, currentHeight: Int, width: Int, height: Int): Long {
        val neededW = maxOf(stepAxis(width), offscreenWidth)
        val neededH = maxOf(stepAxis(height), offscreenHeight)
        offscreenWidth = 0
        offscreenHeight = 0
        return packMaskExtent(settleAxis(currentWidth, neededW), settleAxis(currentHeight, neededH))
    }

    /**
     * The rectangular extent (packed via [packMaskExtent]) an offscreen render of [width] x [height]
     * uses, given [currentWidth] x [currentHeight]: never shrinks either axis.
     */
    fun offscreenExtent(currentWidth: Int, currentHeight: Int, width: Int, height: Int): Long {
        val neededW = stepAxis(width)
        val neededH = stepAxis(height)
        offscreenWidth = maxOf(offscreenWidth, neededW)
        offscreenHeight = maxOf(offscreenHeight, neededH)
        return packMaskExtent(maxOf(currentWidth, neededW), maxOf(currentHeight, neededH))
    }

    private fun settleAxis(current: Int, needed: Int): Int =
        if (current in needed..needed + MASK_TARGET_SLACK) current else needed

    private fun edgeFor(width: Int, height: Int): Int = stepAxis(maxOf(width, height, 1))

    private fun stepAxis(pixels: Int): Int {
        val clamped = maxOf(pixels, 1)
        val stepped = (clamped + MASK_TARGET_MIN_SIZE - 1) / MASK_TARGET_MIN_SIZE * MASK_TARGET_MIN_SIZE
        return stepped.coerceAtMost(MASK_TARGET_MAX_SIZE)
    }
}
