/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.abs

/**
 * How far, in pixels, a press travels before it is a drag rather than a click: one figure for
 * [CanvasInput.draggable] and [CanvasInput.moveHandle], about the slop a touch screen gives a tap.
 */
internal const val CANVAS_DRAG_THRESHOLD_PX = 8

/** No pointer holds a [CanvasMove]. */
private const val NO_POINTER = Long.MIN_VALUE

/**
 * A [CanvasInput.moveHandle]'s move of its window, while one pointer holds it: where the window sat
 * at the press and how far the pointer has travelled since. Kept on the handle's
 * [CanvasPointerState], since the modifier that feeds it is rebuilt every pass.
 */
internal class CanvasMove {
    /** The pointer moving the window, or [NO_POINTER]. */
    var pointer: Long = NO_POINTER
        private set

    /** The root element being moved; null while no pointer holds the handle. */
    var window: CanvasElement? = null
        private set

    /** Whether the pointer has travelled past [CANVAS_DRAG_THRESHOLD_PX], so this is a move, not a click. */
    var started: Boolean = false
        private set

    private var travelX = 0f
    private var travelY = 0f

    /** The window's offsets and placed box, in canvas units, as the press found them. */
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    var box: Rectangle = Rectangle(0f, 0f, 0f, 0f)
        private set

    val held: Boolean get() = pointer != NO_POINTER

    /** Whether [pointerId] may start a move: none is under way, or this pointer's own last one never ended. */
    fun accepts(pointerId: Long): Boolean = pointer == NO_POINTER || pointer == pointerId

    fun holds(pointerId: Long): Boolean = pointer != NO_POINTER && pointer == pointerId

    fun begin(pointerId: Long, window: CanvasElement, box: Rectangle) {
        pointer = pointerId
        this.window = window
        this.box = box
        offsetX = window.offsetX
        offsetY = window.offsetY
        started = false
        travelX = 0f
        travelY = 0f
    }

    /**
     * Adds [dx], [dy] pixels to the travel, and answers whether the move is under way: once it has
     * gone [CANVAS_DRAG_THRESHOLD_PX] along either axis, the same test a draggable element's drag uses.
     */
    fun travel(dx: Int, dy: Int): Boolean {
        travelX += dx
        travelY += dy
        if (!started && (abs(travelX) >= CANVAS_DRAG_THRESHOLD_PX || abs(travelY) >= CANVAS_DRAG_THRESHOLD_PX)) started = true
        return started
    }

    /** How far the pointer has travelled since the press, in canvas units of [units] pixels each. */
    fun travelX(units: Float): Float = travelX / units

    fun travelY(units: Float): Float = travelY / units

    fun end() {
        pointer = NO_POINTER
        window = null
        started = false
    }
}

/**
 * Where a window's box may start along its X axis so [strip] of its [size] stays on a canvas
 * [extent] wide: [start], held to that range. A window wider than the canvas keeps its left part.
 */
internal fun keepOnCanvasX(start: Float, size: Float, extent: Float, strip: Float): Float {
    val visible = strip.coerceIn(0f, size)
    return start.coerceAtMost(extent - visible).coerceAtLeast(visible - size)
}

/**
 * Where a window's box may start along its Y axis: never above the canvas's top, so its top edge,
 * the title bar, stays reachable, and never so low that less than [strip] of it shows.
 */
internal fun keepOnCanvasY(start: Float, size: Float, extent: Float, strip: Float): Float {
    val visible = strip.coerceIn(0f, size)
    return start.coerceAtMost(extent - visible).coerceAtLeast(0f)
}

/**
 * The anchor offset that moves a window's box from [start] to [target] along one axis, from
 * [offset] at [start], for an anchor at [edge] of that axis: 0 the near edge, 1 the centre, 2 the
 * far edge, as [CanvasAnchor.column] and [CanvasAnchor.row] count them. [ratio] is canvas units per
 * window dp.
 *
 * An edge offset moves the box inward and a negative one is held at the edge, so it starts from 0
 * at the least and stays there: the box never goes past the edge it is anchored to. A centred
 * offset moves the box by itself, either way.
 */
internal fun movedOffset(edge: Int, offset: Float, start: Float, target: Float, ratio: Float): Float {
    val shift = (target - start) / (ratio.takeIf { it > 0f && it.isFinite() } ?: 1f)
    return when (edge) {
        0 -> (offset.coerceAtLeast(0f) + shift).coerceAtLeast(0f)
        2 -> (offset.coerceAtLeast(0f) - shift).coerceAtLeast(0f)
        else -> offset + shift
    }
}

/**
 * Sets [window]'s offsets so its [box], from the offsets [offsetX], [offsetY] it was placed at,
 * moves by ([dx], [dy]) canvas units, held so [strip] canvas units of it stay on a canvas
 * [canvasWidth] by [canvasHeight]. [ratio] is canvas units per window dp.
 */
@Suppress("LongParameterList") // One plain value for each thing the placement needs, so it stays testable.
internal fun placeWindow(
    window: CanvasElement,
    offsetX: Float,
    offsetY: Float,
    box: Rectangle,
    dx: Float,
    dy: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    strip: Float,
    ratio: Float,
) {
    val x = keepOnCanvasX(box.x + dx, box.width, canvasWidth, strip)
    val y = keepOnCanvasY(box.y + dy, box.height, canvasHeight, strip)
    val anchor = window.anchor
    window.offsetX = movedOffset(anchor.column, offsetX, box.x, x, ratio)
    window.offsetY = movedOffset(anchor.row, offsetY, box.y, y, ratio)
}

private var raiseStamp = 0L

/** Stamps [element] as brought to the front just now; see [CanvasElement.bringToFront]. */
internal fun raiseCanvasElement(element: CanvasElement) {
    element.pointer.raisedAt = ++raiseStamp
}
