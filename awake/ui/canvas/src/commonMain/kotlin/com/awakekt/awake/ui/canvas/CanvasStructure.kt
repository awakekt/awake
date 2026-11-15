/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * A canvas's items as [CanvasOverlay] draws them, kept across frames: the roots and each element's
 * children in draw order, the move-handle windows, and each item's layout density for this pass.
 *
 * A host hands a new item list each frame, almost always of the same elements under the same
 * parents. The hierarchy is built again, into new lists, only when an item's element, parent,
 * scaler or id changed, or one's follow, move handle, order or raise time did. Otherwise the same
 * lists are handed out, so a frame neither sorts nor fills a map, and a composable that received a
 * list never sees it change under it.
 *
 * Each element carries its index in the items ([CanvasPointerState.treeSlot]), so finding its
 * children or its density reads an array instead of hashing the element. An element listed by two
 * canvases at once is found by a scan instead.
 */
internal class CanvasStructure {
    private var size = 0
    private var ids = IntArray(0)
    private var elements = arrayOfNulls<CanvasElement>(0)
    private var parents = arrayOfNulls<CanvasElement>(0)
    private var scalers = arrayOfNulls<CanvasScaler>(0)
    private var orders = IntArray(0)
    private var raised = LongArray(0)
    private var flags = IntArray(0)
    private var items: List<CanvasItem> = emptyList()

    var roots: List<CanvasItem> = emptyList()
        private set
    private var children = HashMap<CanvasElement, ArrayList<CanvasItem>>()
    private var childrenBySlot: Array<List<CanvasItem>> = emptyArray()

    /** The frame's [CanvasInput.moveHandle]s and the windows they move. */
    var windows = CanvasWindows(emptyList())
        private set

    /** Full builds, for the gate that an unchanged canvas keeps its hierarchy. */
    internal var builds = 0
        private set

    var box: CanvasBoxMetrics = CanvasBoxMetrics()
        private set
    var canvasDensity: Float = 1f
        private set
    private var densities = FloatArray(0)

    /** Takes this frame's [items], building the hierarchy again only when it changed. */
    fun update(items: List<CanvasItem>) {
        if (!holds(items)) build(items)
        this.items = items
        for (slot in items.indices) items[slot].element.pointer.treeSlot = slot
    }

    fun childrenOf(element: CanvasElement): List<CanvasItem> {
        val slot = slotOf(element)
        return if (slot < 0) children[element].orEmpty() else childrenBySlot[slot]
    }

    /** The density [element] is laid out at this pass: its own subtree scaler's, its parent's, or the canvas's. */
    fun densityOf(element: CanvasElement): Float {
        val slot = slotOf(element)
        val density = if (slot < 0) Float.NaN else densities[slot]
        // Before this pass is measured, as a tree that was never measured answers.
        return if (density.isNaN()) canvasDensity else density
    }

    /** Works out every item's density in [box], for a canvas drawn at [canvasDensity] pixels per dp. */
    fun measure(box: CanvasBoxMetrics, canvasDensity: Float) {
        this.box = box
        this.canvasDensity = canvasDensity
        densities.fill(Float.NaN, 0, size)
        for (slot in 0 until size) resolve(slot)
    }

    private fun resolve(slot: Int): Float {
        val known = densities[slot]
        if (!known.isNaN()) return known
        // Seeded before following the parent, so a caller-supplied parent cycle ends.
        densities[slot] = canvasDensity
        val scaler = scalers[slot]
        val parent = parents[slot]?.let(::slotOf) ?: -1
        val resolved = when {
            scaler != null -> box.densityOf(scaler)
            parent >= 0 -> resolve(parent)
            else -> canvasDensity
        }
        densities[slot] = resolved
        return resolved
    }

    private fun slotOf(element: CanvasElement): Int {
        val slot = element.pointer.treeSlot
        if (slot in 0 until size && elements[slot] === element) return slot
        return elements.indexOfFirst { it === element }
    }

    private fun holds(items: List<CanvasItem>): Boolean {
        var held = builds > 0 && items.size == size
        var slot = 0
        while (held && slot < size) {
            held = holdsAt(slot, items[slot])
            slot += 1
        }
        return held
    }

    private fun holdsAt(slot: Int, item: CanvasItem): Boolean {
        val element = item.element
        return element === elements[slot] && item.parent === parents[slot] && item.id == ids[slot] &&
            item.scaler == scalers[slot] && flagsOf(element) == flags[slot] &&
            element.order == orders[slot] && element.pointer.raisedAt == raised[slot]
    }

    private fun build(items: List<CanvasItem>) {
        builds += 1
        size = items.size
        ids = IntArray(size) { items[it].id }
        elements = Array(size) { items[it].element }
        parents = Array(size) { items[it].parent }
        scalers = Array(size) { items[it].scaler }
        orders = IntArray(size) { items[it].element.order }
        raised = LongArray(size) { items[it].element.pointer.raisedAt }
        flags = IntArray(size) { flagsOf(items[it].element) }
        densities = FloatArray(size) { Float.NaN }
        val hierarchy = CanvasHierarchy(items)
        roots = hierarchy.roots
        children = hierarchy.children
        childrenBySlot = Array(size) { hierarchy.childrenOf(items[it].element) }
        windows = CanvasWindows(items)
    }

    private fun flagsOf(element: CanvasElement): Int =
        (if (element.follow.isEmpty()) 0 else FOLLOWS) or (if (element.moveHandle) MOVE_HANDLE else 0)

    private companion object {
        const val FOLLOWS = 1
        const val MOVE_HANDLE = 2
    }
}
