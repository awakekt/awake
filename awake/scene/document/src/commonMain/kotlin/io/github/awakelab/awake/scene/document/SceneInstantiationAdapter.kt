/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.core.transform.Transform

data class SceneNodeHandle<T>(
    val name: String?,
    val value: T,
    val children: List<SceneNodeHandle<T>>,
)

interface SceneInstantiationAdapter<Node, Instance> {
    fun createNode(node: SceneNode, parent: Node?): Node
    fun attachName(node: Node, name: String)
    fun attachTransform(node: Node, transform: SceneTransform, parent: Node?)
    fun attachComponent(node: Node, component: SceneComponent)
    fun complete(roots: List<SceneNodeHandle<Node>>): Instance
}

class AwakeWorldSceneAdapter(
    private val world: World = World(),
    private val componentRegistry: SceneComponentRegistry = SceneComponentRegistry(),
) : SceneInstantiationAdapter<Entity, Scene> {
    private val renderableRequests = ArrayList<SceneRenderableRequest>()
    private val entitiesByName = HashMap<String, Entity>()
    private val entityLinks = ArrayList<EntityLink>()

    override fun createNode(node: SceneNode, parent: Entity?): Entity = world.create()

    override fun attachName(node: Entity, name: String) {
        world.add(node, Name(name))
        entitiesByName.getOrPut(name) { node }
    }

    override fun attachTransform(node: Entity, transform: SceneTransform, parent: Entity?) {
        world.add(node, transform.toComponent(parent))
    }

    override fun attachComponent(node: Entity, component: SceneComponent) {
        val context = object : SceneResolutionContext {
            override val world: World get() = this@AwakeWorldSceneAdapter.world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) {
                entityLinks += EntityLink(targetNodeName, onResolved)
            }

            override fun requestRenderable(entity: Entity, component: SceneMeshRenderer) {
                renderableRequests += SceneRenderableRequest(entity, component)
            }
        }
        componentRegistry.resolve(world, node, component, context)
    }

    override fun complete(roots: List<SceneNodeHandle<Entity>>): Scene {
        entityLinks.forEach { link ->
            val target = requireNotNull(entitiesByName[link.name]) {
                "Cannot load scene: a component references node \"${link.name}\", which does not exist."
            }
            link.assign(target)
        }
        return Scene(
            world = world,
            roots = roots.map { it.toSceneNodeInstance() },
            renderableRequests = renderableRequests.toList(),
        )
    }

    private class EntityLink(val name: String, val assign: (Entity) -> Unit)
}

fun SceneTransform.toComponent(parent: Entity?): Transform = Transform(
    position = position.toVec3(),
    rotation = rotation.toVec3(),
    scale = scale.toVec3(),
    parent = parent,
)

fun SceneVec3.toVec3(): Vec3f = Vec3f(x, y, z)

private fun SceneNodeHandle<Entity>.toSceneNodeInstance(): SceneNodeInstance = SceneNodeInstance(
    name = name,
    entity = value,
    children = children.map { it.toSceneNodeInstance() },
)
