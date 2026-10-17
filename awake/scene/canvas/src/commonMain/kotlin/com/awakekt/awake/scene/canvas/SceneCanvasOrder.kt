/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.canvasItemAt
import com.awakekt.awake.ui.canvas.canvasDrawOrder as itemsInDrawOrder

/**
 * The entities of this world's [CanvasElement]s in the order [SceneCanvas] draws them with the same
 * [siblingOrder], the first drawn first, so the last is on top: an element, then everything drawn
 * inside it, before the next. The elements drawn on the canvas itself, and those drawn inside one
 * element, go by [CanvasElement.order], lowest first, and those of the same order in hierarchy order.
 * An editor that lists the canvas, or picks from it, reads it here rather than sorting again.
 *
 * Every element is listed where it would draw if it were shown, hidden and touch-only ones included.
 *
 * @param siblingOrder The order [SceneCanvas] is given, or null for the world's own.
 */
fun World.canvasDrawOrder(siblingOrder: Comparator<Entity>? = null): List<Entity> {
    val placed = placedCanvasItems(siblingOrder)
    val entities = entitiesOf(placed)
    return itemsInDrawOrder(placed.map { it.item }).map { entities.getValue(it) }
}

/**
 * The entity whose [CanvasElement] [SceneCanvas] last drew topmost at ([x], [y]), or null when none
 * was drawn there: a child over its parent, and of two that overlap the one drawn later, whether or
 * not it takes taps. It is what an editor's click on the canvas picks.
 *
 * The point is in the canvas's own units, those of [CanvasElement.screenBounds], which is where each
 * element was drawn last frame: one that was not drawn, being hidden, touch-only or inside a hidden
 * element, is never found. Pass the [siblingOrder] [SceneCanvas] was given, or null for the world's
 * own.
 */
fun World.canvasElementAt(x: Float, y: Float, siblingOrder: Comparator<Entity>? = null): Entity? {
    val placed = placedCanvasItems(siblingOrder)
    return canvasItemAt(placed.map { it.item }, x, y)?.let { entitiesOf(placed).getValue(it) }
}

// A CanvasItem has no equals of its own, so each item is its own key.
private fun entitiesOf(placed: List<PlacedItem>): Map<CanvasItem, Entity> = placed.associate { it.item to it.entity }
