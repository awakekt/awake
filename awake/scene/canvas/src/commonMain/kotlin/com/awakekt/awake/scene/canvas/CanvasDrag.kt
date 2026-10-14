/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.gestures.dragAfterThreshold
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * [hovered] with a drag added when this element is [CanvasElement.draggable], ahead of a Button's
 * own `clickable` so a real drag's release never also reaches it; a press that stays under the
 * threshold leaves the release for `clickable` to turn into a click.
 */
internal fun CanvasElement.draggedModifier(hovered: Modifier, tree: CanvasTree, density: Float): Modifier =
    if (!draggable) {
        hovered
    } else {
        hovered.dragAfterThreshold(
            onDrag = { x, y -> dragCanvasElement(this, density, x, y) },
            onDragEnd = { x, y -> dropCanvasElement(this, tree, density, x, y) },
        )
    }

/**
 * Marks [element] dragging and moves its drag point to local ([x], [y]) pixels on its own, still
 * unmoved, box -- [CanvasElement.screenBounds] plus the pointer's offset into it, in canvas units.
 * Does nothing before the element has ever been placed.
 */
private fun dragCanvasElement(element: CanvasElement, density: Float, x: Int, y: Int) {
    val bounds = element.screenBounds ?: return
    element.dragging = true
    element.dragPointCanvasX = bounds.x + x / density
    element.dragPointCanvasY = bounds.y + y / density
}

/**
 * Ends [element]'s drag at local ([x], [y]), and reports it to whichever [CanvasElement.dropTarget]
 * sits under that point, or to [element] itself as dropped outside when none does.
 */
private fun dropCanvasElement(element: CanvasElement, tree: CanvasTree, density: Float, x: Int, y: Int) {
    val bounds = element.screenBounds
    val px = (bounds?.x ?: element.dragPointCanvasX) + x / density
    val py = (bounds?.y ?: element.dragPointCanvasY) + y / density
    element.dragging = false
    val target = tree.dropTargetAt(px, py, exclude = element)
    if (target != null) target.droppedSource = element else element.droppedOutsideAt = Vec2(px, py)
}

/** True inside a dragged element's ghost: drawn as the element is, with nothing to press, hover or place. */
internal val LocalCanvasGhost = compositionLocalOf { false }

/** A picture of every dragging element, at its drag point, for the pointer to drag visibly. */
context(_: Composer)
internal fun Ghosts(tree: CanvasTree) {
    CompositionLocalProvider(LocalCanvasGhost provides true) {
        for (item in tree.items) {
            val element = item.element
            if (!element.dragging) continue
            key(element) {
                // Its own look and its children's, dimmed and centred on the drag point.
                Box(Modifier.offset((element.dragPointCanvasX - element.width / 2f).dp, (element.dragPointCanvasY - element.height / 2f).dp).alpha(GHOST_ALPHA)) {
                    CanvasElementView(element, Modifier.size(element.width.dp, element.height.dp), tree, tree.childrenOf(element))
                }
            }
        }
    }
}

/** [element] in a ghost: its box, its words where its kind has them, and its children. */
context(_: Composer)
internal fun GhostElementView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    val words = if (element.kind in WORDED) tree.textOf(element) else ""
    val align = element.textAlign ?: if (element.kind == CanvasElementKind.Button) CanvasAnchor.Center else CanvasAnchor.TopLeft
    Box(styled, contentAlignment = align.alignment) {
        if (words.isNotEmpty()) CanvasText(element, words, textStyle)
        Children(element, children, tree)
    }
}

private const val GHOST_ALPHA = 0.6f
private val WORDED = setOf(CanvasElementKind.Text, CanvasElementKind.Button, CanvasElementKind.TextField)
