/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.applyTo
import com.awakekt.awake.ui.canvas.CanvasElement

/**
 * Hands out entities that follow a node with a [template] element, and takes them back, so a game
 * that shows and hides many followers, such as damage numbers or name plates over a crowd, reuses
 * the same entities and elements instead of creating and destroying them.
 *
 * A released entity stays in [world] with its element hidden and following nothing, so a hidden
 * follower costs no drawing and no projection; [acquire] hands the oldest released one out again,
 * set back to [template] first, and makes a new one only when none is free. The pool never destroys
 * what it made: [clear] does, when the game no longer needs any of them.
 *
 * @property world The world the followers live in.
 * @property template What every follower the pool hands out looks like before the game sets it.
 */
class CanvasFollowerPool(val world: World, val template: SceneCanvasElement) {
    private val free = ArrayDeque<Entity>()
    private val inUse = LinkedHashSet<Entity>()

    /** How many followers are handed out now. */
    val active: Int get() = inUse.size

    /** How many entities the pool has made, handed out or free. */
    val size: Int get() = inUse.size + free.size

    /**
     * A follower of the node named [target], set to [template] and then by [configure], such as its
     * text: a released one when there is one, or a new entity.
     *
     * @param target The name of the node it follows.
     * @param configure Sets what this follower shows beyond the template.
     * @return The follower's entity; give it back with [release].
     */
    fun acquire(target: String, configure: CanvasElement.() -> Unit = {}): Entity {
        val entity = free.removeFirstOrNull() ?: world.create()
        val element = world.get<CanvasElement>(entity) ?: CanvasElement().also { world.add(entity, it) }
        template.applyTo(element)
        element.follow = target
        element.visible = true
        element.configure()
        inUse += entity
        return entity
    }

    /**
     * Hides [entity]'s follower and keeps it for the next [acquire]. One the pool did not hand out,
     * or already took back, is left as it is.
     *
     * @return Whether it was taken back.
     */
    fun release(entity: Entity): Boolean {
        if (!inUse.remove(entity)) return false
        world.get<CanvasElement>(entity)?.let {
            it.visible = false
            it.follow = ""
        }
        free.addLast(entity)
        return true
    }

    /** Destroys every entity the pool made, handed out or free, and starts empty. */
    fun clear() {
        for (entity in inUse) world.destroy(entity)
        for (entity in free) world.destroy(entity)
        inUse.clear()
        free.clear()
    }
}
