/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Gives a [MeshRenderer] with no [MeshBounds] the bounds of its mesh, so it is culled like any
 * other renderer (#1621).
 *
 * A scene document's renderers get their bounds when they are attached, but a renderer added
 * any other way, by a cell streamer, the authoring DSL or a game's own code, had none, and an
 * entity without bounds is drawn by every view: a portrait that sees one face drew every
 * streamed cell of the town behind the camera. A mesh that knows no bounds (its
 * [com.awakekt.awake.render.mesh.Mesh.localBounds] is null) is still never culled.
 *
 * Bounds an entity already has are its own and are left alone. Runs before anything reads
 * bounds in a frame, the spatial index included, so an entity is never indexed a frame late and
 * culled by a stale index.
 */
internal class MeshBoundsFallback {
    private var world: World? = null
    private var generation = -1
    private var unbounded: Family? = null
    private val pending = ArrayList<Entity>()

    fun attach(world: World) {
        val family = familyFor(world)
        if (family.size == 0) return
        pending.clear()
        // A mesh that knows no bounds stays in the family every frame, so it is passed over here
        // rather than listed: listing boxes each entity, garbage every frame for nothing.
        family.forEach { entity -> if (world.get<MeshRenderer>(entity)?.mesh?.localBounds != null) pending += entity }
        if (pending.isEmpty()) return
        // Added after the walk: adding a component moves the entity out of the family walked.
        for (index in 0 until pending.size) {
            val entity = pending[index]
            val bounds = world.get<MeshRenderer>(entity)?.mesh?.localBounds ?: continue
            world.add(entity, MeshBounds(bounds))
        }
        pending.clear()
    }

    private fun familyFor(world: World): Family {
        val cached = unbounded
        if (cached != null && this.world === world && generation == world.generation) return cached
        return world.family {
            all(Transform::class, MeshRenderer::class)
            exclude(MeshBounds::class)
        }.also {
            this.world = world
            generation = world.generation
            unbounded = it
        }
    }
}
