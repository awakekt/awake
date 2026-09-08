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
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneVec3

/**
 * Exports a live active ECS [world] into a serializable [SceneDocument].
 *
 * @param world Active ECS world containing live scene entities.
 * @param name Optional scene title to set on the exported document.
 * @param componentRegistry Registry containing component bindings to export.
 * @param extraComponents Optional callback exporting custom components for an entity.
 * @return The exported [SceneDocument].
 */
fun SceneLoader.fromWorld(
    world: World,
    name: String? = null,
    componentRegistry: SceneComponentRegistry = SceneComponentRegistry(),
    extraComponents: ((Entity) -> List<SceneComponent>)? = null,
): SceneDocument {
    val transforms = linkedMapOf<Entity, Transform>()
    world.queryEach(Transform::class) { entity, transform -> transforms[entity] = transform }

    transforms.forEach { (entity, transform) ->
        val parent = transform.parent
        require(parent == null || parent in transforms) {
            "Cannot export $entity: parent $parent has no Transform and cannot be a SceneNode."
        }
    }

    val childrenByParent = linkedMapOf<Entity?, MutableList<Entity>>()
    transforms.forEach { (entity, transform) ->
        childrenByParent.getOrPut(transform.parent) { mutableListOf() }.add(entity)
    }

    val visiting = mutableSetOf<Entity>()
    val exported = mutableSetOf<Entity>()
    fun exportNode(entity: Entity): SceneNode {
        check(visiting.add(entity)) { "Cannot export World: Transform parent links contain a cycle at $entity." }
        val transform = checkNotNull(transforms[entity])
        val node = SceneNode(
            name = world.get<Name>(entity)?.value,
            transform = transform.toSceneTransform(),
            components = world.sceneComponents(entity, componentRegistry, extraComponents),
            children = childrenByParent[entity].orEmpty().map(::exportNode),
        )
        visiting.remove(entity)
        exported += entity
        return node
    }

    val roots = childrenByParent[null].orEmpty().map(::exportNode)
    check(exported.size == transforms.size) {
        "Cannot export World: Transform parent links contain a cycle with no root."
    }
    return SceneDocument(name = name, nodes = roots)
}

private fun World.sceneComponents(
    entity: Entity,
    componentRegistry: SceneComponentRegistry,
    extraComponents: ((Entity) -> List<SceneComponent>)?,
): List<SceneComponent> = buildList {
    addAll(componentRegistry.exportComponents(this@sceneComponents, entity))
    extraComponents?.invoke(entity)?.let { addAll(it) }
}

/** Converts a live ECS [Transform] component into a serializable [SceneTransform]. */
fun Transform.toSceneTransform(): SceneTransform = SceneTransform(
    position = position.toSceneVec3(),
    rotation = rotation.toSceneVec3(),
    scale = scale.toSceneVec3(),
)

/** Converts a math [Vec3f] into a serializable [SceneVec3]. */
fun Vec3f.toSceneVec3(): SceneVec3 = SceneVec3(x, y, z)
