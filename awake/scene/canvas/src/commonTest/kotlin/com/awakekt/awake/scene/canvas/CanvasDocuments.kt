/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneNode

// What the scene runtime's loader and exporter do with canvas nodes, small enough for this module's
// tests, which cannot reach `awake:scene:runtime`: each node an entity under its parent, its
// components attached through the canvas bindings, and saved back through them.

/** The canvas bindings, the older `canvas_element`'s included. */
internal val canvasRegistry: SceneComponentRegistry = SceneComponentRegistry(bindings = canvasElementBindings)

/** A world holding [nodes] and everything under them, each node's entity named after it. */
internal fun loadCanvas(nodes: List<SceneNode>): World = World().also { world -> nodes.forEach { world.load(it, parent = null) } }

private fun World.load(node: SceneNode, parent: Entity?): Entity {
    val entity = create()
    add(entity, Transform(parent = parent))
    node.name?.let { add(entity, Name(it)) }
    val context = object : SceneResolutionContext {
        override val world: World = this@load

        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }
    for (component in node.components) canvasRegistry.resolve(this, entity, component, context)
    for (child in node.children) load(child, entity)
    return entity
}

/** The nodes this world's canvas saves as, roots first, each with what the canvas bindings export. */
internal fun World.saveCanvas(): List<SceneNode> {
    val children = LinkedHashMap<Entity?, MutableList<Entity>>()
    queryEach(Transform::class) { entity, transform -> children.getOrPut(transform.parent) { ArrayList() } += entity }
    fun save(entity: Entity): SceneNode = SceneNode(
        name = get<Name>(entity)?.value,
        components = canvasRegistry.exportComponents(this, entity),
        children = children[entity].orEmpty().map(::save),
    )
    return children[null].orEmpty().map(::save)
}
