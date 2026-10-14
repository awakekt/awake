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

/** One [CanvasItem] a frame for every [CanvasElement] in this world, its parent the nearest canvas element above it. */
internal fun World.canvasItems(): List<CanvasItem> {
    val items = ArrayList<CanvasItem>()
    family<CanvasElement>().forEach { entity, element -> items += CanvasItem(entity.id, element, canvasParent(entity)) }
    return items
}

/** The [CanvasElement] of the nearest ancestor of [entity] through [Transform] that has one, or null. */
private fun World.canvasParent(entity: Entity): CanvasElement? {
    var node = get<Transform>(entity)?.parent
    while (node != null) {
        get<CanvasElement>(node)?.let { return it }
        node = get<Transform>(node)?.parent
    }
    return null
}
