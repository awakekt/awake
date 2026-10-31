/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * Which drawn elements are [CanvasInput.moveHandle]s, and the window, the root element, each moves.
 * A handle inside a follower moves nothing: a follower is placed on what it follows. Built with the
 * frame's [CanvasTree]; a canvas with no handle allocates nothing more.
 */
internal class CanvasWindows(items: List<CanvasItem>) {
    private var windowByHandle: HashMap<CanvasElement, CanvasElement>? = null
    private var handleByWindow: HashMap<CanvasElement, CanvasElement>? = null

    init {
        var parents: HashMap<CanvasElement, CanvasElement>? = null
        for (item in items) {
            if (item.element.moveHandle) {
                val up = parents ?: parentsOf(items).also { parents = it }
                val window = rootOf(item.element, up, items.size)
                if (window.follow.isEmpty()) add(item.element, window)
            }
        }
    }

    private fun add(handle: CanvasElement, window: CanvasElement) {
        (windowByHandle ?: HashMap<CanvasElement, CanvasElement>().also { windowByHandle = it })[handle] = window
        (handleByWindow ?: HashMap<CanvasElement, CanvasElement>().also { handleByWindow = it }).getOrPut(window) { handle }
    }

    /** The window [handle] moves, or null when it is no handle. */
    fun windowOf(handle: CanvasElement): CanvasElement? = windowByHandle?.get(handle)

    /** The first handle that moves [window], or null when it is no window. */
    fun handleOf(window: CanvasElement): CanvasElement? = handleByWindow?.get(window)

    /** Calls [action] with each window and its first handle. */
    fun forEachWindow(action: (window: CanvasElement, handle: CanvasElement) -> Unit) {
        handleByWindow?.forEach { (window, handle) -> action(window, handle) }
    }

    private fun parentsOf(items: List<CanvasItem>): HashMap<CanvasElement, CanvasElement> {
        val parents = HashMap<CanvasElement, CanvasElement>(items.size * 2)
        for (item in items) {
            val parent = item.parent
            if (parent != null && item.element.follow.isEmpty()) parents[item.element] = parent
        }
        return parents
    }

    // Bounded by the item count, so a host's parent cycle ends rather than spinning.
    private fun rootOf(element: CanvasElement, parents: Map<CanvasElement, CanvasElement>, limit: Int): CanvasElement {
        var root = element
        var steps = 0
        while (steps++ <= limit) root = parents[root] ?: break
        return root
    }
}
