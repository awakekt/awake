/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasResolver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The [CanvasResolver] a world answers: nodes by [Name], its projector for a follower's point or
 * bounds, its views for a View's camera picture, and a node's component fields, exported once per
 * frame through its scene bindings, for [CanvasElement.bind] to read.
 *
 * The node index and a field's export are both built lazily, so a frame with no follower, View or
 * bind never walks the world or touches the binding registry.
 */
internal class SceneCanvasResolver(
    private val world: World,
    private val projector: CanvasProjector?,
    private val views: CanvasViews?,
    private val bindings: List<SceneComponentBinding<*, *>>?,
) : CanvasResolver {
    private val named: Map<String, Entity> by lazy {
        HashMap<String, Entity>().also { names -> world.family<Name>().forEach { entity, name -> names.getOrPut(name.value) { entity } } }
    }

    private val byName by lazy {
        (bindings ?: globalCanvasBindings()).mapNotNull { binding -> binding.serializer?.descriptor?.serialName?.let { it to binding } }.toMap()
    }

    private val exported = HashMap<Pair<Entity, String>, JsonObject?>()

    override fun point(target: String, offset: Vec3f): Vec2? {
        val matrix = named[target]?.let { world.get<Transform>(it) }?.worldMatrix ?: return null
        return projector?.project(matrix.m03 + offset.x, matrix.m13 + offset.y, matrix.m23 + offset.z)
    }

    override fun bounds(target: String): Rectangle? {
        val node = named[target] ?: return null
        return projector?.bounds(node)
    }

    override fun texture(element: CanvasElement, camera: String, width: Int, height: Int): Any? {
        val node = named[camera] ?: return null
        return views?.texture(element, node, width, height)
    }

    override fun field(node: String, path: String): String? {
        val entity = named[node] ?: return null
        val component = path.substringBefore('.')
        val fields = path.substringAfter('.', "").split('.')
        val json = exported.getOrPut(entity to component) { export(entity, component) }
        var element: JsonElement? = json
        for (field in fields) element = (element as? JsonObject)?.get(field)
        return (element as? JsonPrimitive)?.content.also { if (it == null) warnOnce(path) }
    }

    // Safe: a binding's serializer is the serializer of its own schema class, which export returns.
    @Suppress("UNCHECKED_CAST")
    private fun export(entity: Entity, component: String): JsonObject? {
        val binding = byName[component]
        val serializer = binding?.serializer as? KSerializer<SceneComponent>
        val saved = if (serializer != null) binding.exportFrom(world, entity) else null
        return try {
            if (serializer != null && saved != null) json.encodeToJsonElement(serializer, saved) as? JsonObject else null
        } catch (unwritable: SerializationException) {
            warnOnce(component, "it could not be read: ${unwritable.message}")
            null
        }
    }

    private companion object {
        // Defaults kept: a field at its default is still a value to show. A NaN or an infinity is a
        // value too, and the scene format's strict default would refuse the whole component.
        val json = Json {
            encodeDefaults = true
            allowSpecialFloatingPointValues = true
        }

        val log = Logger("scene-canvas")

        // Paths a scene's bindings name: as many as the scenes write, so it stays small.
        val warned = HashSet<String>()

        fun warnOnce(path: String, why: String = "no node's component has it", result: String = "the element shows its own value") {
            if (warned.add(path)) log.warn { "A canvas binding reads '$path', but $why; $result" }
        }
    }
}

/**
 * One [CanvasItem] a frame for every [CanvasElement] in this world, its parent the nearest canvas
 * element above it, in hierarchy order: a node before the nodes under it, and an earlier sibling,
 * with everything under it, before a later one. `CanvasOverlay` sorts by [CanvasElement.order]
 * keeping this order among equals, so of two elements with the same order, the later in the
 * hierarchy draws on top and takes the pointer.
 *
 * Siblings are ordered by [siblingOrder], and those it calls equal, or every sibling when it is
 * null, as the world stores their [Transform]s: the order a scene saves them in, and for a loaded
 * scene the order its document lists them. A root with no [Transform] comes after those with one.
 */
internal fun World.canvasItems(siblingOrder: Comparator<Entity>? = null): List<CanvasItem> = placedCanvasItems(siblingOrder).map { it.item }

/** [canvasItems] with the path down to each one's entity from its root, the entity last. */
internal fun World.placedCanvasItems(siblingOrder: Comparator<Entity>? = null): List<PlacedItem> {
    val placed = ArrayList<PlacedItem>()
    family<CanvasElement>().forEach { entity, element ->
        val path = hierarchyPath(entity)
        placed += PlacedItem(CanvasItem(entity.id, element, canvasParent(path)), path)
    }
    if (placed.size > 1) {
        val stored = StoredOrder(this, placed)
        val siblings = siblingOrder?.then(stored) ?: stored
        placed.sortWith { a, b -> comparePaths(a.path, b.path, siblings) }
    }
    return placed
}

/** A [CanvasItem] and the path down to its entity from its root. */
internal class PlacedItem(val item: CanvasItem, val path: List<Entity>) {
    /** The entity the item's element is on. */
    val entity: Entity get() = path.last()
}

/**
 * Siblings in the order the world stores their [Transform]s; a node with none after those with one,
 * by the order its [CanvasElement] was collected in, then by id. The whole order is total, so a sort
 * by it is well defined.
 *
 * Where each node sits is found on the first comparison that needs it, in one pass over the world's
 * [Transform]s that looks up only the nodes on [placed]'s paths.
 */
private class StoredOrder(private val world: World, private val placed: List<PlacedItem>) : Comparator<Entity> {
    private val collected: Map<Entity, Int> by lazy {
        HashMap<Entity, Int>().also { at -> placed.forEachIndexed { index, item -> at[item.path.last()] = index } }
    }

    private val stored: Map<Entity, Int> by lazy {
        val wanted = HashSet<Entity>().also { nodes -> placed.forEach { nodes += it.path } }
        val at = HashMap<Entity, Int>(wanted.size * 2)
        var index = 0
        world.family<Transform>().forEach { entity, _ ->
            if (entity in wanted) at[entity] = index
            index++
        }
        at
    }

    override fun compare(a: Entity, b: Entity): Int {
        val byStorage = (stored[a] ?: Int.MAX_VALUE).compareTo(stored[b] ?: Int.MAX_VALUE)
        if (byStorage != 0) return byStorage
        val byCollection = (collected[a] ?: Int.MAX_VALUE).compareTo(collected[b] ?: Int.MAX_VALUE)
        return if (byCollection != 0) byCollection else a.packed.compareTo(b.packed)
    }
}

/**
 * Where [a] and [b] part, the two siblings they go through there, compared by [siblings]; when one
 * path holds the other, the shorter one, the node above, comes first.
 */
private fun comparePaths(a: List<Entity>, b: List<Entity>, siblings: Comparator<Entity>): Int {
    for (index in 0 until minOf(a.size, b.size)) {
        if (a[index] != b[index]) return siblings.compare(a[index], b[index])
    }
    return a.size.compareTo(b.size)
}

/**
 * [entity] and its ancestors through [Transform], from its root down to [entity] itself. A parent
 * link that loops back stops the walk where it would revisit a node.
 */
private fun World.hierarchyPath(entity: Entity): List<Entity> {
    val path = ArrayList<Entity>()
    var node: Entity? = entity
    while (node != null && node !in path) {
        path += node
        node = get<Transform>(node)?.parent
    }
    path.reverse()
    return path
}

/** The [CanvasElement] of the nearest ancestor on [path], an entity's [hierarchyPath], that has one, or null. */
private fun World.canvasParent(path: List<Entity>): CanvasElement? {
    for (index in path.size - 2 downTo 0) get<CanvasElement>(path[index])?.let { return it }
    return null
}
