/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.AwakeWorldSceneAdapter
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneInstantiationAdapter
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode

/**
 * Instantiates [node] into [world] as `SceneDocument.instantiate` does, but all or nothing: when
 * instantiation fails part way, such as a component binding that throws or a node link that names no
 * node, every entity made so far is destroyed before the failure reaches the caller (#1410).
 */
internal fun instantiateOrDiscard(node: SceneNode, world: World, componentRegistry: SceneComponentRegistry): Scene {
    val adapter = TrackingSceneAdapter(AwakeWorldSceneAdapter(world, componentRegistry))
    var instantiated = false
    try {
        return SceneLoader.instantiate(SceneDocument(nodes = listOf(node)), adapter).also { instantiated = true }
    } finally {
        // Children first, as Scene.destroy goes: the order they were made in, reversed.
        if (!instantiated) adapter.created.asReversed().forEach(world::destroy)
    }
}

/** [delegate], keeping each entity it makes, so a failed instantiation can take them out again. */
private class TrackingSceneAdapter(
    private val delegate: AwakeWorldSceneAdapter,
) : SceneInstantiationAdapter<Entity, Scene> by delegate {
    val created = ArrayList<Entity>()

    override fun createNode(node: SceneNode, parent: Entity?): Entity = delegate.createNode(node, parent).also(created::add)
}
