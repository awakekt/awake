/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.math2d.contains
import com.awakekt.awake.core.text.font.UiFont

/**
 * One drawn [CanvasElement], carrying what [CanvasOverlay] needs but a `World` would otherwise hold:
 * [id] for its `canvas-element-<id>` test tag, and [parent], the nearest element above it in the
 * scene hierarchy the host found there, or null for a root. A follower is always a root regardless
 * of [parent]: it is placed on what it follows, not inside a box.
 */
internal class CanvasItem(val id: Int, val element: CanvasElement, val parent: CanvasElement? = null)

/**
 * What [CanvasOverlay] asks its host for: a [CanvasElement.follow]ed node's projected point or
 * bounds, a View's camera picture, and a [CanvasElement.bind]'s field text. Every member answers
 * null, so a host that implements none of what an element asks for still draws it, minus that one
 * thing.
 */
internal interface CanvasResolver {
    /** The followed node named [target]'s screen point, in frame pixels, offset by [offset] in world space. */
    fun point(target: String, offset: Vec3f): Vec2? = null

    /** The frame rectangle the followed node named [target] covers. */
    fun bounds(target: String): Rectangle? = null

    /** A View's camera picture: what the camera named [camera] sees, at [width] by [height] pixels. */
    fun texture(element: CanvasElement, camera: String, width: Int, height: Int): Any? = null

    /** A bound field's raw text on the node named [node], addressed as `component.field`. */
    fun field(node: String, path: String): String? = null

    companion object {
        /** A resolver with nothing behind it: every member answers null. */
        val None: CanvasResolver = object : CanvasResolver {}
    }
}

/**
 * [items], each under the nearest element above it in the scene hierarchy. One that follows a node
 * is placed against the screen wherever it sits in the scene, by where [resolver] puts the node in
 * the frame less where the canvas sits in it, [origin].
 */
internal class CanvasTree(
    val items: List<CanvasItem>,
    private val showTouchControls: Boolean,
    val images: Map<String, ImageBitmap>,
    val resolver: CanvasResolver,
    val origin: CanvasOrigin = CanvasOrigin(),
    private val fonts: Map<String, UiFont> = emptyMap(),
) {
    val roots = ArrayList<CanvasItem>()
    private val children = HashMap<CanvasElement, ArrayList<CanvasItem>>()

    init {
        for (item in items) {
            // Cleared up front so an element not composed this frame -- hidden, or a follower with
            // nowhere to land -- reports null rather than where it last was.
            item.element.screenBounds = null
            val parent = if (item.element.follow.isEmpty()) item.parent else null
            (if (parent == null) roots else children.getOrPut(parent) { ArrayList() }) += item
        }
        roots.sortBy { it.element.order }
        for (siblings in children.values) siblings.sortBy { it.element.order }
    }

    fun childrenOf(element: CanvasElement): List<CanvasItem> = children[element].orEmpty()

    fun shows(element: CanvasElement): Boolean = element.visible && (showTouchControls || !element.touchOnly)

    /** The font an element's words draw in: its loaded file or built-in family, or null for the default. */
    fun fontOf(element: CanvasElement): UiFont? = canvasFont(element.font, fonts)

    /** A Bar's fill: what its binding reads, or its own value when it has none or it reads nothing. */
    fun valueOf(element: CanvasElement): Float {
        val bind = element.bind?.takeIf { it.value.isNotBlank() } ?: return element.value
        val value = resolver.numberField(bind.node, bind.value)
        val max = if (bind.max.isBlank()) 1f else resolver.numberField(bind.node, bind.max)
        return if (value == null || max == null || max <= 0f) element.value else (value / max).coerceIn(0f, 1f)
    }

    /** An element's words: its binding's text filled in, or its own text when it has none. */
    fun textOf(element: CanvasElement): String {
        val bind = element.bind?.takeIf { it.text.isNotBlank() } ?: return element.text
        return resolver.textField(bind.node, bind.text)
    }

    /**
     * The drop target, other than [exclude], drawn topmost at canvas point ([x], [y]) by its last
     * placed box, or null: a child over its parent, a later sibling over an earlier one.
     */
    fun dropTargetAt(x: Float, y: Float, exclude: CanvasElement): CanvasElement? = dropTargetIn(roots, x, y, exclude)

    private fun dropTargetIn(entries: List<CanvasItem>, x: Float, y: Float, exclude: CanvasElement): CanvasElement? {
        var found: CanvasElement? = null
        for (item in entries) {
            val element = item.element
            if (!shows(element)) continue
            val here = element !== exclude && element.dropTarget && element.screenBounds?.contains(x, y) == true
            found = dropTargetIn(childrenOf(element), x, y, exclude) ?: (if (here) element else found)
        }
        return found
    }
}

/**
 * Where a following [element] goes in the canvas, in dp at [density] pixels each: on its node's
 * screen box grown by its outset, or with its anchor point on the node's screen point, nudged inward
 * by its offsets. The resolver reports the frame's pixels, so the canvas's own corner in the frame
 * comes off first. Null hides it: no resolver answer, no such node, or the node behind the camera.
 */
internal fun CanvasTree.placeFollower(element: CanvasElement, density: Float): Rectangle? {
    val node = element.follow
    val box = if (element.followBounds) resolver.bounds(node) else null
    return if (box != null) {
        Rectangle((box.x - origin.x) / density, (box.y - origin.y) / density, box.width / density, box.height / density).outset(element.followOutset)
    } else {
        placeOnPoint(element, node, density)
    }
}

/** This rectangle grown by [by] on each side, or shrunk for a negative [by], about its centre and never below nothing. */
private fun Rectangle.outset(by: Float): Rectangle {
    val grownWidth = (width + 2 * by).coerceAtLeast(0f)
    val grownHeight = (height + 2 * by).coerceAtLeast(0f)
    return Rectangle(x + (width - grownWidth) / 2, y + (height - grownHeight) / 2, grownWidth, grownHeight)
}

/** [element] with its anchor point on [node]'s screen point, or null when the node is behind the camera. */
private fun CanvasTree.placeOnPoint(element: CanvasElement, node: String, density: Float): Rectangle? {
    val offset = element.followOffset
    val point = resolver.point(node, Vec3f(offset.x, offset.y, offset.z)) ?: return null
    val column = element.anchor.column
    val row = element.anchor.row
    val x = (point.x - origin.x) / density - column / 2f * element.width + if (column == 2) -element.offsetX else element.offsetX
    val y = (point.y - origin.y) / density - row / 2f * element.height + if (row == 2) -element.offsetY else element.offsetY
    return Rectangle(x, y, element.width, element.height)
}
