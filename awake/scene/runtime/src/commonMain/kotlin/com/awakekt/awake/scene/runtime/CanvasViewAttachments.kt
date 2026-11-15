/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.intersects
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshBounds

/**
 * The renderables attached since the last frame's Views were drawn, so a View that keeps its picture
 * ([com.awakekt.awake.ui.canvas.CanvasViewRefresh.OnRequest]) draws again when a model that arrived
 * late, such as a portrait's character, attaches inside its view (#1621).
 *
 * Records only while [recording], which the View store sets when it has a View that keeps its
 * picture, so a streaming world costs nothing without one. At most [CAPACITY] a frame are kept; past
 * that every such View draws again, as if each had been met. Cleared every frame by [clear].
 */
internal class CanvasViewAttachments {
    private val worlds = ArrayList<World>(CAPACITY)
    private val entities = ArrayList<Entity>(CAPACITY)
    private var overflowed = false

    /** Whether attaches are kept; false while no View keeps its picture. */
    var recording = false

    /** Notes that [entity] in [world] was given a renderer. */
    fun record(world: World, entity: Entity) {
        if (!recording) return
        if (entities.size == CAPACITY) {
            overflowed = true
            return
        }
        worlds += world
        entities += entity
    }

    /**
     * Whether a renderable attached in [world] this frame can be in [lens]'s view at [aspect]: its
     * world bounds meet the view's frustum. One without bounds, or without a transform, is taken as
     * met, since nothing says it is outside.
     */
    fun meets(world: World, lens: Lens, aspect: Float): Boolean {
        var met = overflowed
        planes = null
        var index = 0
        while (!met && index < entities.size) {
            met = worlds[index] === world && meets(world, entities[index], lens, aspect)
            index += 1
        }
        planes = null
        return met
    }

    // The view's planes, built once per [meets] and only when an attach there has bounds to test.
    private var planes: List<Plane>? = null

    private fun meets(world: World, entity: Entity, lens: Lens, aspect: Float): Boolean {
        val transform = world.get<Transform>(entity)
        val bounds = world.get<MeshBounds>(entity)
        return when {
            !world.isAlive(entity) -> false
            transform == null || bounds == null -> true
            else -> (planes ?: Frustum.planes(lens, aspect).also { planes = it }).intersects(bounds.worldBounds(transform))
        }
    }

    /** Forgets this frame's attaches. */
    fun clear() {
        worlds.clear()
        entities.clear()
        overflowed = false
    }

    private companion object {
        const val CAPACITY = 256
    }
}
