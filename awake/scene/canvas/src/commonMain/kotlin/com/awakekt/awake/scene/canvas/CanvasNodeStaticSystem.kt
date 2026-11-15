/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.StaticTransform
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement

/**
 * Marks every scene canvas node [StaticTransform], so `TransformSystem` neither compares nor builds
 * its world matrix each frame.
 *
 * Nothing reads a canvas node's world matrix: an element is placed in canvas space by the canvas,
 * its [Transform] is read only for its parent and its order among its siblings, and a follower is
 * placed by the world matrix of the entity it follows, not its own. So a node that is moved,
 * re-parented or follows a moving entity is placed as it was unmarked; only its unused world matrix
 * stays where it was first built.
 *
 * A node another element follows by name is left unmarked, since a follower is placed by the
 * world matrix of what it follows. A node that only becomes a follow target after it was marked
 * keeps the world matrix it was first built with; remove its [StaticTransform] to follow it.
 *
 * It looks again only when a node joined or left, by the family's change count, so a frame that
 * adds no node costs one comparison. Run it before `TransformSystem`; the scene runtime's
 * infrastructure systems include it.
 */
class CanvasNodeStaticSystem : System {
    private var nodes: Family2<CanvasElement, Transform>? = null
    private var changes = 0
    private val unmarked = ArrayList<Entity>()
    private val followed = HashSet<String>()

    override fun update(world: World, delta: Float) {
        val family = world.family<CanvasElement, Transform>()
        if (family === nodes && family.changes == changes) return
        // Collected first: marking a node changes the world's families, which must not happen mid-walk.
        family.forEach { _, element, _ -> if (element.follow.isNotEmpty()) followed += element.follow }
        family.forEach { entity, _, _ ->
            if (!world.has<StaticTransform>(entity) && world.get<Name>(entity)?.value !in followed) unmarked += entity
        }
        for (index in 0 until unmarked.size) world.add(unmarked[index], StaticTransform)
        unmarked.clear()
        followed.clear()
        nodes = family
        changes = family.changes
    }
}
