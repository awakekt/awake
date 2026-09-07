/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Intermediate node handle produced during scene document instantiation.
 *
 * @param T Target node type.
 * @property name Node name identifier if present.
 * @property value Created node value object.
 * @property children Child node handles.
 */
data class SceneNodeHandle<T>(
    val name: String?,
    val value: T,
    val children: List<SceneNodeHandle<T>>,
)

/**
 * Adapter interface allowing custom instantiation targets for scene documents.
 *
 * @param Node Created node type (e.g. [Entity]).
 * @param Instance Completed scene instance type (e.g. [Scene]).
 */
interface SceneInstantiationAdapter<Node, Instance> {
    /** Creates a new node instance. */
    fun createNode(node: SceneNode, parent: Node?): Node

    /** Attaches a name identifier onto [node]. */
    fun attachName(node: Node, name: String)

    /** Attaches a transform descriptor onto [node]. */
    fun attachTransform(node: Node, transform: SceneTransform, parent: Node?)

    /** Attaches a component descriptor onto [node]. */
    fun attachComponent(node: Node, component: SceneComponent)

    /** Completes scene instantiation and returns the final [Instance]. */
    fun complete(roots: List<SceneNodeHandle<Node>>): Instance
}

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
