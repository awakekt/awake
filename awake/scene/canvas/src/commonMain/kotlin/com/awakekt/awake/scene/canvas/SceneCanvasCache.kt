/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasScaleScope
import com.awakekt.awake.ui.canvas.CanvasScaler

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
 * again only on a frame where that order could have changed. Each frame checks the elements are in
 * the same order, then checks each node on their paths once, however many elements sit below it: its
 * [Transform] where the world stored it at the last sort, and the same parent. Unchanged parents make
 * every element's path the same, so the sort would come out as it did, and the items are made in that
 * order from the elements and parents they have now; when anything differs, they are sorted afresh.
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

    /**
     * Each node on any path, once: where the world stored its [Transform] at the last sort (-1 for none),
     * and its parent then. A node shared by many elements' paths is checked once a frame, not once each.
     */
    private var nodeEntities = LongArray(0)
    private var nodeSlots = IntArray(0)
    private var nodeParents = LongArray(0)
    private var nodeHasParent = BooleanArray(0)

    /** The items' collected positions, in the order the last sort left them. */
    private var sorted = IntArray(0)

    private var count = -1
    private var elements = arrayOfNulls<CanvasElement>(0)

    // Type ids resolved once per world generation and stores taken once per [items]: `world.get` hashes
    // its type's key on every call, and the walk below makes one per path node of every element, every frame.
    private var typesGeneration = -1
    private var transformType: ComponentTypeId? = null
    private var elementType: ComponentTypeId? = null
    private var scalerType: ComponentTypeId? = null
    private var transformStore: ComponentStore<Transform>? = null
    private var elementStore: ComponentStore<CanvasElement>? = null
    private var scalerStore: ComponentStore<CanvasScaler>? = null

    /** This frame's items, in hierarchy order. */
    fun items(): List<CanvasItem> {
        takeStores()
        if (!unchanged()) sort()
        val items = ArrayList<CanvasItem>(count)
        for (position in 0 until count) {
            val index = sorted[position]
            items += CanvasItem(Entity(entities[index]).id, checkNotNull(elements[index]), canvasParent(index), subtreeScaler(index))
        }
        return items
    }

    private fun takeStores() {
        if (typesGeneration != world.generation) {
            transformType = world.typeId(Transform::class)
            elementType = world.typeId(CanvasElement::class)
            scalerType = world.typeId(CanvasScaler::class)
            typesGeneration = world.generation
        }
        transformStore = world.componentStore(checkNotNull(transformType))
        elementStore = world.componentStore(checkNotNull(elementType))
        scalerStore = world.componentStore(checkNotNull(scalerType))
    }

    /** [node]'s component in this store, as `world.get` finds it: none for a node that is no longer alive. */
    private fun <T : Any> ComponentStore<T>?.of(node: Entity): T? = if (this != null && world.isAlive(node)) get(node) else null

    /** Whether the elements, and every node on their paths and where it is stored, are as the last sort found them. */
    private fun unchanged(): Boolean {
        val family = world.family<CanvasElement>()
        if (family.size != count) return false
        if (elements.size < count) elements = arrayOfNulls(count)
        var index = 0
        var same = true
        family.forEach { entity, element ->
            if (!same) return@forEach
            elements[index] = element
            same = entity.packed == entities[index]
            index++
        }
        return same && nodesUnchanged()
    }

    private fun nodesUnchanged(): Boolean {
        val transforms = world.family<Transform>()
        for (at in nodeEntities.indices) if (!nodeUnchanged(at, transforms)) return false
        return true
    }

    /** Whether node [at] has its [Transform] stored where it was at the last sort, or still none, and the same parent. */
    private fun nodeUnchanged(at: Int, transforms: Family1<Transform>): Boolean {
        val transform = transformStore.of(Entity(nodeEntities[at]))
        val slot = nodeSlots[at]
        if (slot < 0) return transform == null
        val stored = transform != null && slot < transforms.size && transforms.componentAt(slot) === transform
        val parent = transform?.parent
        return stored && nodeHasParent[at] == (parent != null) && (parent == null || parent.packed == nodeParents[at])
    }

    private fun sort() {
        val placed = ArrayList<PlacedItem>()
        world.family<CanvasElement>().forEach { entity, element ->
            val path = world.hierarchyPath(entity)
            placed += PlacedItem(CanvasItem(entity.id, element, world.canvasParent(path), world.subtreeScaler(path)), path)
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
        var at = 0
        placed.forEachIndexed { index, item ->
            pathStarts[index] = at
            // The entity first, then up to the root, as each frame walks it.
            for (step in item.path.indices.reversed()) {
                pathNodes[at] = item.path[step].packed
                at++
            }
        }
        pathStarts[count] = at
        rememberNodes(wanted)
        val order = world.sortPlaced(placed, siblingOrder = null)
        sorted = IntArray(count) { collected.getValue(order[it]) }
    }

    /** Records each path node in [wanted], with where its [Transform] is stored, and its parent. */
    private fun rememberNodes(wanted: Map<Entity, Int>) {
        val size = wanted.size
        nodeEntities = LongArray(size)
        nodeSlots = IntArray(size)
        nodeParents = LongArray(size)
        nodeHasParent = BooleanArray(size)
        var at = 0
        wanted.forEach { (node, slot) ->
            val parent = if (slot >= 0) transformStore.of(node)?.parent else null
            nodeEntities[at] = node.packed
            nodeSlots[at] = slot
            nodeHasParent[at] = parent != null
            nodeParents[at] = parent?.packed ?: 0L
            at++
        }
    }

    /** The element of the nearest node above item [index]'s entity that has one, as [canvasParent] finds it. */
    private fun canvasParent(index: Int): CanvasElement? {
        for (at in pathStarts[index] + 1 until pathStarts[index + 1]) {
            elementStore.of(Entity(pathNodes[at]))?.let { return it }
        }
        return null
    }

    /** The nearest subtree [CanvasScaler] on item [index]'s path below its [canvasParent], as [World.subtreeScaler] finds it. */
    private fun subtreeScaler(index: Int): CanvasScaler? {
        val start = pathStarts[index]
        for (at in start until pathStarts[index + 1]) {
            val node = Entity(pathNodes[at])
            if (at > start && elementStore.of(node) != null) break
            scalerStore.of(node)?.takeIf { it.scope == CanvasScaleScope.Subtree }?.let { return it }
        }
        return null
    }
}

/**
 * Returns a map of live [CanvasElement]s keyed by their entity [Name.value], filtered to names
 * starting with [prefix] (or all named canvas elements when [prefix] is empty).
 *
 * @param prefix Optional name prefix filter (for example, a window's root name).
 * @return A map from entity name to its [CanvasElement].
 */
fun World.canvasElements(prefix: String = ""): Map<String, CanvasElement> = buildMap {
    syncCanvasElements()
    family<Name, CanvasElement>().forEach { _, name, element ->
        if (prefix.isEmpty() || name.value.startsWith(prefix)) {
            put(name.value, element)
        }
    }
}
