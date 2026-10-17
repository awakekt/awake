/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasItem

/**
 * A world's nodes by [Name], kept from frame to frame for [SceneCanvasResolver]. A follower, a View
 * or a bind looks its node up every frame, and building the index walks every named node in the
 * world, so it is built again only when a lookup finds it out of date: the name is not in it, or the
 * node it holds has gone or is named otherwise now. That happens at most once per [nextFrame]; a name
 * still missing after that is missing for the rest of the frame.
 *
 * A name several nodes share resolves to the first of them in the world's order when the index was
 * built, for as long as that node keeps the name.
 */
internal class CanvasNodeNames(private val world: World) {
    private val byName = HashMap<String, Entity>()
    private var built = false
    private var builtThisFrame = false

    /** Starts a frame, in which the index may be built once more. */
    fun nextFrame() {
        builtThisFrame = false
    }

    /** The node named [name], or null when none is. */
    operator fun get(name: String): Entity? {
        if (!built) build()
        val found = byName[name]?.takeIf { isNamed(it, name) }
        if (found != null || builtThisFrame) return found
        build()
        return byName[name]
    }

    private fun isNamed(entity: Entity, name: String): Boolean = world.isAlive(entity) && world.get<Name>(entity)?.value == name

    private fun build() {
        byName.clear()
        world.family<Name>().forEach { entity, name -> byName.getOrPut(name.value) { entity } }
        built = true
        builtThisFrame = true
    }
}

/**
 * [canvasItems] for one world, kept from frame to frame: the elements are sorted into hierarchy order
 * again only on a frame where that order could have changed. Each frame walks the path above every
 * element and checks it against the last sort's: the elements in the same order, each with the same
 * nodes above it, and each of those nodes' [Transform] where the world stored it then. When all of it
 * holds, the sort would come out as it did, so the items are made in that order from the elements
 * and parents they have now; when any of it doesn't, they are sorted afresh.
 *
 * Storage moves a node when one stored after it is removed, so a node despawned elsewhere can, rarely,
 * cost a sort; nodes added only ever append.
 */
internal class CanvasItemOrder(private val world: World) {
    /** The elements' entities in the order the world stores their [CanvasElement]s, at the last sort. */
    private var entities = LongArray(0)

    /** Item `i`'s path, from its entity up to its root, is `pathNodes[pathStarts[i] until pathStarts[i + 1]]`. */
    private var pathStarts = IntArray(1)
    private var pathNodes = LongArray(0)

    /** Where the world stored each path node's [Transform] at the last sort, or -1 for a node with none. */
    private var stored = IntArray(0)

    /** The items' collected positions, in the order the last sort left them. */
    private var sorted = IntArray(0)

    private var count = -1
    private var elements = arrayOfNulls<CanvasElement>(0)

    /** This frame's items, in hierarchy order. */
    fun items(): List<CanvasItem> {
        if (!unchanged()) sort()
        val items = ArrayList<CanvasItem>(count)
        for (position in 0 until count) {
            val index = sorted[position]
            items += CanvasItem(Entity(entities[index]).id, checkNotNull(elements[index]), canvasParent(index))
        }
        return items
    }

    /** Whether the elements, the paths above them and where their nodes are stored are as the last sort found them. */
    private fun unchanged(): Boolean {
        val family = world.family<CanvasElement>()
        if (family.size != count) return false
        if (elements.size < count) elements = arrayOfNulls(count)
        val transforms = world.family<Transform>()
        var index = 0
        var same = true
        family.forEach { entity, element ->
            if (!same) return@forEach
            elements[index] = element
            same = entity.packed == entities[index] && samePath(index, entity, transforms)
            index++
        }
        return same
    }

    private fun samePath(index: Int, entity: Entity, transforms: Family1<Transform>): Boolean {
        val start = pathStarts[index]
        val end = pathStarts[index + 1]
        var node: Entity? = entity
        var at = start
        while (at < end && isPathNode(node, at, transforms)) {
            node = node?.let { world.get<Transform>(it)?.parent }
            at++
        }
        // Where the last sort's path ended, the walk ends too: at a root, or where a parent link loops back.
        val next = node
        return at == end && (next == null || (start until end).any { pathNodes[it] == next.packed })
    }

    /** Whether [node] is the last sort's path node [at], with its [Transform] stored where it was then, or still none. */
    private fun isPathNode(node: Entity?, at: Int, transforms: Family1<Transform>): Boolean {
        if (node == null || node.packed != pathNodes[at]) return false
        val transform = world.get<Transform>(node)
        val slot = stored[at]
        return if (slot < 0) transform == null else transform != null && slot < transforms.size && transforms.componentAt(slot) === transform
    }

    private fun sort() {
        val placed = ArrayList<PlacedItem>()
        world.family<CanvasElement>().forEach { entity, element ->
            val path = world.hierarchyPath(entity)
            placed += PlacedItem(CanvasItem(entity.id, element, world.canvasParent(path)), path)
        }
        count = placed.size
        val collected = HashMap<PlacedItem, Int>(count * 2)
        placed.forEachIndexed { index, item -> collected[item] = index }
        val wanted = HashMap<Entity, Int>()
        placed.forEach { item -> item.path.forEach { wanted[it] = -1 } }
        var slot = 0
        world.family<Transform>().forEach { entity, _ ->
            if (entity in wanted) wanted[entity] = slot
            slot++
        }
        entities = LongArray(count) { placed[it].entity.packed }
        elements = Array(count) { placed[it].item.element }
        pathStarts = IntArray(count + 1)
        val nodes = placed.sumOf { it.path.size }
        pathNodes = LongArray(nodes)
        stored = IntArray(nodes)
        var at = 0
        placed.forEachIndexed { index, item ->
            pathStarts[index] = at
            // The entity first, then up to the root, as each frame walks it.
            for (step in item.path.indices.reversed()) {
                pathNodes[at] = item.path[step].packed
                stored[at] = wanted.getValue(item.path[step])
                at++
            }
        }
        pathStarts[count] = at
        val order = world.sortPlaced(placed, siblingOrder = null)
        sorted = IntArray(count) { collected.getValue(order[it]) }
    }

    /** The element of the nearest node above item [index]'s entity that has one, as [canvasParent] finds it. */
    private fun canvasParent(index: Int): CanvasElement? {
        for (at in pathStarts[index] + 1 until pathStarts[index + 1]) {
            world.get<CanvasElement>(Entity(pathNodes[at]))?.let { return it }
        }
        return null
    }
}
