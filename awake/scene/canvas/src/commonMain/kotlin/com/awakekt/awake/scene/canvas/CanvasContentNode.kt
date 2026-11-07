/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasWidget
import kotlin.reflect.KClass

/**
 * One node of declarative canvas content: the entity a [CanvasEntityApplier] made for a declaration,
 * and the canvas components it last wrote there, so the next pass writes only what changed.
 *
 * The node keeps its declaration's `remember` slots, so they live exactly as long as its entity.
 *
 * @property applier The applier that made the node, which writes to it.
 * @property entity The entity this node is. The content root's is the caller's; every other is made
 * by the applier and destroyed with the node.
 * @property type What the node was declared as, which decides its reuse.
 * @property key The `key(...)` it was declared under, or null.
 */
class CanvasContentNode internal constructor(
    internal val applier: CanvasEntityApplier,
    val entity: Entity,
    internal val type: Any,
    internal val key: Any?,
) : RememberHolder {
    internal val children = ArrayList<CanvasContentNode>()
    private val slots = ArrayList<Any?>()
    override val rememberSlots: List<Any?> get() = slots

    /** The canvas components last written to [entity], in saved order; empty before the first write. */
    internal var parts: List<SceneComponent> = emptyList()

    /** The place among its siblings last written to [entity]'s [CanvasSiblingIndex]; -1 before the first. */
    internal var siblingIndex = -1

    /** Whether a child was inserted before another or moved this pass, so the children's places need writing. */
    internal var reordered = false

    /** Entities under this node the applier did not declare, such as a placed prefab's, destroyed with it. */
    internal var owned: List<Entity> = emptyList()
}

/**
 * Writes [next], an element's canvas components, to [node]'s entity: only the ones that differ from
 * what was written last, and the removal of the ones it no longer has.
 *
 * @return How many components were written or removed; 0 when [next] equals the last write.
 */
internal fun World.writeCanvasParts(node: CanvasContentNode, next: List<SceneComponent>): Int {
    val last = node.parts
    if (next == last) return 0
    val element = get<CanvasElement>(node.entity) ?: emptyCanvasElement().also { addCanvasElement(node.entity, it) }
    var writes = 0
    for (part in next) {
        val before = last.firstOrNull { it::class == part::class }
        if (before != part) {
            element.set(part.toCanvasPart())
            writes++
        }
    }
    for (part in last) {
        if (next.none { it::class == part::class }) {
            val type = liveSlotOf(part)
            // A widget replaced by one of another kind went with the set above.
            if (type != CANVAS_WIDGET_TYPE || next.none { liveSlotOf(it) == CANVAS_WIDGET_TYPE }) element.remove(type)
            writes++
        }
    }
    node.parts = next
    syncCanvasElement(node.entity)
    return writes
}

/** The type a live element keeps this saved part under: one slot for every widget. */
private fun liveSlotOf(part: SceneComponent): KClass<out Any> {
    val live = part.toCanvasPart()
    return if (live is CanvasWidget) CANVAS_WIDGET_TYPE else live::class
}

/** Destroys [node]'s entity, every child node's and the entities it [CanvasContentNode.owned]; returns how many went. */
internal fun World.destroyCanvasNode(node: CanvasContentNode): Int {
    var count = 0
    for (index in node.children.indices.reversed()) count += destroyCanvasNode(node.children[index])
    node.children.clear()
    for (index in node.owned.indices.reversed()) if (destroy(node.owned[index])) count++
    node.owned = emptyList()
    if (destroy(node.entity)) count++
    return count
}

/** This node's children in [slot]: canvas content has no layers. */
internal fun CanvasContentNode.childrenIn(slot: Slot): ArrayList<CanvasContentNode> {
    require(slot == Slot.Children) { "canvas content has no layers: a popup over the canvas is a canvas element" }
    return children
}

/** Writes each of [node]'s children's place, when a child was inserted or moved; returns how many were written. */
internal fun World.placeChildren(node: CanvasContentNode): Int {
    if (!node.reordered) return 0
    node.reordered = false
    var writes = 0
    for (index in node.children.indices) writes += placeSibling(node.children[index], index)
    return writes
}

/** Writes [node]'s place among its siblings when it changed; returns 1 when it did. */
internal fun World.placeSibling(node: CanvasContentNode, index: Int): Int {
    if (node.siblingIndex == index) return 0
    node.siblingIndex = index
    add(node.entity, CanvasSiblingIndex(index))
    return 1
}
