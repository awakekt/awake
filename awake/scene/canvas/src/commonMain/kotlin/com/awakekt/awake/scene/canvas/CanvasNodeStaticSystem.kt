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
 * world matrix of what it follows. Follow targets are checked every frame, by comparing each node's
 * follow with the last frame's: a node that becomes a target is unmarked, so its matrix is built
 * again from where it is, and one no longer followed is marked again.
 *
 * Membership is looked at again only when a node joined or left, by the family's change count, and
 * the follows only compared, so a frame that changes neither costs a string comparison a node.
 * Run it before `TransformSystem`; the scene runtime's infrastructure systems include it.
 */
class CanvasNodeStaticSystem : System {
    private var nodes: Family2<CanvasElement, Transform>? = null
    private var changes = 0
    private var follows = emptyArray<String>()
    private val marking = ArrayList<Entity>()
    private val unmarking = ArrayList<Entity>()
    private val followed = HashSet<String>()

    override fun update(world: World, delta: Float) {
        val family = world.family<CanvasElement, Transform>()
        if (family === nodes && family.changes == changes && !followsChanged(family)) return
        reconcile(world, family)
        nodes = family
        changes = family.changes
    }

    /** Whether any node's follow differs from the last reconcile's, in the family's order. */
    private fun followsChanged(family: Family2<CanvasElement, Transform>): Boolean {
        var index = 0
        var changed = family.size != follows.size
        family.forEach { _, element, _ ->
            if (!changed && element.follow != follows[index]) changed = true
            index += 1
        }
        return changed
    }

    /** Marks every node no element follows and unmarks every node one does, recording the follows. */
    private fun reconcile(world: World, family: Family2<CanvasElement, Transform>) {
        follows = Array(family.size) { "" }
        var index = 0
        family.forEach { _, element, _ ->
            follows[index++] = element.follow
            if (element.follow.isNotEmpty()) followed += element.follow
        }
        // Collected first: marking a node changes the world's families, which must not happen mid-walk.
        family.forEach { entity, _, _ ->
            val target = world.get<Name>(entity)?.value in followed
            val marked = world.has<StaticTransform>(entity)
            if (!target && !marked) {
                marking += entity
            } else if (target && marked) {
                unmarking += entity
            }
        }
        for (index in 0 until marking.size) world.add(marking[index], StaticTransform)
        for (index in 0 until unmarking.size) world.remove<StaticTransform>(unmarking[index])
        marking.clear()
        unmarking.clear()
        followed.clear()
    }
}
