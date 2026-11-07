/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent

/**
 * The [Applier] that builds a composition's nodes as canvas entities, so `:awake:compose:runtime`
 * reconciles canvas content the way it reconciles `LayoutNode`s: matching, keys and reuse stay the
 * runtime's, and only the tree operations are canvas ones.
 *
 * - **Insert** makes an entity with a [Transform] whose parent is the current node's entity. The
 *   declaration's update then writes its canvas components through [write].
 * - **Move** keeps the entity, and writes the new order of the moved node's siblings to their
 *   [CanvasSiblingIndex], which canvas ordering reads ahead of storage order.
 * - **Remove** destroys the entity, every entity under it that this applier made, and those a node
 *   owns, such as a placed prefab's. Disposing the composition removes everything but the root.
 *
 * It writes what changed and nothing else: a pass that declares what the last one did writes no
 * component, which [writes] counts.
 *
 * Canvas layout, follow, binding and drawing stay the canvas systems': this only makes and arranges
 * the entities they read.
 *
 * @property world The world the entities are made in.
 * @param root The entity the content goes under, such as a HUD root. It is not destroyed with the content.
 */
class CanvasEntityApplier(val world: World, root: Entity) : Applier {
    /** The node for [root]: what a composition passes as its root, so the top level can remember. */
    val root: CanvasContentNode = CanvasContentNode(this, root, RootNode, null)

    private val stack = ArrayList<CanvasContentNode>()
    private var current = this.root

    /** How many canvas components and sibling places this applier has written or removed. */
    var writes: Long = 0L
        private set

    /** How many entities this applier has made for a declaration. */
    var created: Long = 0L
        private set

    /** How many entities this applier has destroyed. */
    var destroyed: Long = 0L
        private set

    override fun childCount(slot: Slot): Int = if (slot == Slot.Children) current.children.size else 0

    override fun typeAt(slot: Slot, index: Int): Any = current.childrenIn(slot)[index].type

    override fun keyAt(slot: Slot, index: Int): Any? = current.childrenIn(slot)[index].key

    override fun nodeAt(slot: Slot, index: Int): Any = current.childrenIn(slot)[index]

    override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any {
        val siblings = current.childrenIn(slot)
        val entity = world.create()
        world.add(entity, Transform(parent = current.entity))
        val node = CanvasContentNode(this, entity, type, key)
        val at = index.coerceAtMost(siblings.size)
        siblings.add(at, node)
        created++
        // An append takes its place without moving any other; an insert moves the ones after it.
        if (at < siblings.lastIndex) current.reordered = true else writes += world.placeSibling(node, at)
        return node
    }

    override fun moveTo(slot: Slot, from: Int, to: Int) {
        val siblings = current.childrenIn(slot)
        siblings.add(to, siblings.removeAt(from))
        current.reordered = true
    }

    override fun truncateFrom(slot: Slot, index: Int) {
        // Every pass also drops the layers it did not declare, of which canvas content has none.
        if (slot != Slot.Children) return
        val siblings = current.children
        while (siblings.size > index) destroyed += world.destroyCanvasNode(siblings.removeAt(siblings.lastIndex))
    }

    override fun down(slot: Slot, index: Int) {
        stack += current
        current = current.childrenIn(slot)[index]
    }

    override fun up() {
        writes += world.placeChildren(current)
        current = stack.removeAt(stack.lastIndex)
    }

    /**
     * Writes [parts], a declaration's canvas components, to [node]'s entity: the ones that changed
     * since the last write, and the removal of the ones it stopped having.
     */
    fun write(node: CanvasContentNode, parts: List<SceneComponent>) {
        writes += world.writeCanvasParts(node, parts)
    }

    /**
     * Ends a pass: writes the top level's sibling places when they changed, as [up] does for every
     * node below. A host calls it after each pass over [root].
     */
    fun finishPass() {
        writes += world.placeChildren(root)
    }

    /** The type the content root is declared as; never matched by a declaration. */
    private object RootNode
}
