/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.contains

/**
 * [items] in the order [CanvasOverlay] draws them, the first drawn first, so the last is on top: a
 * root, then everything inside it, before the next root. The roots, and the children of one element,
 * go by [CanvasElement.order], lowest first, and those of the same order as [items] lists them, but
 * for roots brought to the front, [CanvasElement.bringToFront], which go last of their order, the
 * latest last. An
 * element that [CanvasElement.follow]s a node is a root, whatever its [CanvasItem.parent].
 *
 * This is where each element would draw if it were shown: a hidden or touch-only element, and what
 * is inside it, keep their place. An item whose parent is not in [items] is never drawn, so it is
 * left out.
 */
fun canvasDrawOrder(items: List<CanvasItem>): List<CanvasItem> {
    val hierarchy = CanvasHierarchy(items)
    val drawn = ArrayList<CanvasItem>(items.size)
    fun visit(item: CanvasItem) {
        drawn += item
        hierarchy.childrenOf(item.element).forEach(::visit)
    }
    hierarchy.roots.forEach(::visit)
    return drawn
}

/**
 * The item [CanvasOverlay] last drew topmost at ([x], [y]), or null when none was drawn there: a
 * child over its parent, and of two that overlap the one [canvasDrawOrder] puts later, whether or
 * not it takes taps. It is what an editor picks; a tap in play goes to the topmost element under it
 * that takes taps.
 *
 * The point is in the canvas's own units, those of [CanvasElement.screenBounds], which is where each
 * element was placed last frame: an element that was not drawn, being hidden, touch-only, inside a
 * hidden element or a follower with nowhere to land, is never found. A rotated element is found by
 * its unrotated box, where it takes the pointer. Pass [items] as the last frame's [CanvasOverlay]
 * was given them.
 */
fun canvasItemAt(items: List<CanvasItem>, x: Float, y: Float): CanvasItem? {
    val drawn = canvasDrawOrder(items)
    for (index in drawn.indices.reversed()) {
        val item = drawn[index]
        if (item.element.screenBounds?.contains(x, y) == true) return item
    }
    return null
}

/**
 * [items], each under the nearest element above it, as [CanvasOverlay] draws them: a follower is a
 * root, and the roots and each element's children are sorted by [CanvasElement.order], keeping the
 * order of [items] among equals; roots of the same order then go by when each was last brought to
 * the front, never first.
 */
internal class CanvasHierarchy(items: List<CanvasItem>) {
    val roots = ArrayList<CanvasItem>()
    private val children = HashMap<CanvasElement, ArrayList<CanvasItem>>()

    init {
        for (item in items) {
            val parent = if (item.element.follow.isEmpty()) item.parent else null
            (if (parent == null) roots else children.getOrPut(parent) { ArrayList() }) += item
        }
        // Stable sorts, so elements of the same order keep the order the host listed them in.
        roots.sortWith(ROOT_DRAW_ORDER)
        for (siblings in children.values) siblings.sortBy { it.element.order }
    }

    fun childrenOf(element: CanvasElement): List<CanvasItem> = children[element].orEmpty()
}

/** Roots by order, then by when each was brought to the front: a comparator made once, as it sorts every frame. */
private val ROOT_DRAW_ORDER = Comparator<CanvasItem> { a, b ->
    val byOrder = a.element.order.compareTo(b.element.order)
    if (byOrder != 0) byOrder else a.element.pointer.raisedAt.compareTo(b.element.pointer.raisedAt)
}
