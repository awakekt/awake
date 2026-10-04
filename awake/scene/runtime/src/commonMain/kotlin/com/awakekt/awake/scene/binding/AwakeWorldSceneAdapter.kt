/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneInstantiationAdapter
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneNodeHandle
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneVec3

/**
 * Standard scene instantiation adapter creating live ECS entities in a [World].
 *
 * @param world Target active [World].
 * @param componentRegistry Associated component registry.
 */
class AwakeWorldSceneAdapter(
    private val world: World = World(),
    private val componentRegistry: SceneComponentRegistry = SceneComponentRegistry(),
) : SceneInstantiationAdapter<Entity, Scene> {
    private val requests = ArrayList<Any>()
    private val entityLinks = ArrayList<EntityLink>()

    // Names are scoped as `SceneValidator` scopes them: the document, and each prefab instance's
    // node. A scope is keyed by its instance node; null is the document.
    private val prefabInstances = HashSet<Entity>()
    private val scopeOf = HashMap<Entity, Entity?>()
    private val namesByScope = HashMap<Entity?, HashMap<String, Entity>>()

    override fun createNode(node: SceneNode, parent: Entity?): Entity = world.create().also { entity ->
        scopeOf[entity] = parent?.let { if (it in prefabInstances) it else scopeOf[it] }
    }

    override fun attachName(node: Entity, name: String) {
        world.add(node, Name(name))
        namesByScope.getOrPut(scopeOf[node]) { HashMap() }.getOrPut(name) { node }
    }

    override fun attachTransform(node: Entity, transform: SceneTransform, parent: Entity?) {
        world.add(node, transform.toComponent(parent))
    }

    override fun attachComponent(node: Entity, component: SceneComponent) {
        if (component is ScenePrefabLink) prefabInstances += node
        val context = object : SceneResolutionContext {
            override val world: World get() = this@AwakeWorldSceneAdapter.world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) {
                entityLinks += EntityLink(targetNodeName, scopeOf[node], onResolved)
            }

            override fun recordRequest(request: Any) {
                requests += request
            }
        }
        componentRegistry.resolve(world, node, component, context)
    }

    override fun complete(roots: List<SceneNodeHandle<Entity>>): Scene {
        entityLinks.forEach { link ->
            val target = requireNotNull(resolve(link.name, link.scope)) {
                "Cannot load scene: a component references node \"${link.name}\", which does not exist."
            }
            link.assign(target)
        }
        return Scene(
            world = world,
            roots = roots.map { it.toSceneNodeInstance() },
            requests = requests.toList(),
        )
    }

    /** [name] in [scope], else in the scopes around it: a prefab instance's own nodes come first. */
    private fun resolve(name: String, scope: Entity?): Entity? {
        var current = scope
        while (true) {
            namesByScope[current]?.get(name)?.let { return it }
            if (current == null) return null
            current = scopeOf[current]
        }
    }

    private class EntityLink(val name: String, val scope: Entity?, val assign: (Entity) -> Unit)
}

/** Converts a [SceneTransform] into a live ECS [Transform] component. */
fun SceneTransform.toComponent(parent: Entity?): Transform = Transform(
    position = position.toVec3(),
    rotation = rotation.toVec3(),
    scale = scale.toVec3(),
    parent = parent,
)

/** Converts a [SceneVec3] into a math [Vec3f]. */
fun SceneVec3.toVec3(): Vec3f = Vec3f(x, y, z)

private fun SceneNodeHandle<Entity>.toSceneNodeInstance(): SceneNodeInstance = SceneNodeInstance(
    name = name,
    entity = value,
    children = children.map { it.toSceneNodeInstance() },
)
