/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasRect
import kotlin.reflect.KClass

/**
 * Brings the canvas elements a player can see or touch in step with their entities, and does it only
 * when something changed: what [SceneCanvas] and the input and action systems call each frame in place
 * of [syncCanvasElements], which reconciles every element every time it is called.
 *
 * A frame in which nothing changed costs one read-only pass over the canvas entities; a frame in which
 * something did reconciles once, however many of those callers run. A control added or changed by code
 * mid-frame is a change and so is reconciled by whichever caller runs next. Elements inside a hidden
 * window, or hidden themselves, are left as they are until they show again, which is itself a change.
 */
fun World.syncActiveCanvasElements() {
    val gates = family<CanvasSyncGate>()
    if (gates.size == 0 && family<CanvasElement>().size == 0 && family<CanvasRect>().size == 0) return
    val gate = if (gates.size > 0) gates.componentAt(0) else CanvasSyncGate(this).also { add(create(), it) }
    gate.sync(this)
}

/**
 * What [syncActiveCanvasElements] remembers between calls, held in the world as the one component of
 * an entity of its own: the revision of every store an element is made of, and which entities were
 * hidden when it last looked. The world has no other place for state that several systems share.
 */
internal class CanvasSyncGate(world: World) {
    private val stores: Array<ComponentStore<Any>> = Array(WATCHED_TYPES.size) { world.store(WATCHED_TYPES[it]) }
    private val seen = IntArray(stores.size) { -1 }

    /** Per entity id, how this pass found it: [UNKNOWN], [SHOWN] or [HIDDEN]; [before] is the last pass's. */
    private var now = ByteArray(0)
    private var before = ByteArray(0)
    private val climb = IntArray(MAX_DEPTH)

    /** Whether [entity] is hidden, itself or by a node above it, as the last pass found. */
    fun isHidden(entity: Entity): Boolean = entity.id < now.size && now[entity.id] == HIDDEN

    fun sync(world: World) {
        if (!changed(world)) return
        world.reconcileCanvasElements(this)
        for (index in stores.indices) seen[index] = stores[index].revision
    }

    /**
     * Whether anything an element is made of changed since the last reconcile: a component of any
     * type added, replaced or removed, an element changed through its own API, or an element shown
     * or hidden. It never stops early, since the reconcile that follows reads every entity's visibility.
     */
    private fun changed(world: World): Boolean {
        var changed = false
        for (index in stores.indices) if (stores[index].revision != seen[index]) changed = true
        val last = now
        now = before
        before = last
        now.fill(UNKNOWN)
        world.family<CanvasRect>().forEach { entity, _ ->
            val hidden = hide(world, entity)
            if (entity.id >= before.size || before[entity.id] != now[entity.id]) changed = true
            if (!hidden && !changed) {
                val element = world.get<CanvasElement>(entity)
                val link = world.get<CanvasElementLink>(entity)
                if (element != null && link != null && element.componentsVersion != link.version) changed = true
            }
        }
        return changed
    }

    /** Finds whether [entity] is hidden and records it, with each node above it that this pass had not yet. */
    private fun hide(world: World, entity: Entity): Boolean {
        // A packed handle, not an Entity?: a nullable entity is boxed, and this runs for every element.
        var node = entity.packed
        var depth = 0
        var hidden = false
        // The walk stops at a node already found, a hidden one, the root, or after MAX_DEPTH nodes,
        // where a parent link loops and nothing above is known to hide it.
        while (node != NONE && depth < MAX_DEPTH) {
            val at = Entity(node)
            grow(at.id)
            val known = now[at.id]
            val hides = known == UNKNOWN && world.get<CanvasRect>(at)?.visible == false
            if (hides) now[at.id] = HIDDEN
            if (known != UNKNOWN || hides) {
                hidden = known == HIDDEN || hides
                node = NONE
            } else {
                climb[depth++] = at.id
                node = world.get<Transform>(at)?.parent?.packed ?: NONE
            }
        }
        val mark = if (hidden) HIDDEN else SHOWN
        for (step in 0 until depth) now[climb[step]] = mark
        return hidden
    }

    private fun grow(id: Int) {
        if (id < now.size) return
        now = now.copyOf(maxOf(id + 1, now.size * 2))
        if (before.size < now.size) before = before.copyOf(now.size)
    }

    private companion object {
        const val UNKNOWN: Byte = 0
        const val SHOWN: Byte = 1
        const val HIDDEN: Byte = 2
        const val MAX_DEPTH = 256
        const val NONE = -1L

        /** Every store an element's state lives in: its components, its element and its link. */
        @Suppress("UNCHECKED_CAST") // Each store holds its own type.
        val WATCHED_TYPES: Array<KClass<Any>> =
            (CANVAS_COMPONENT_TYPES.toList() + CANVAS_WIDGET_TYPES.toList() + CanvasElement::class + CanvasElementLink::class)
                .toTypedArray() as Array<KClass<Any>>
    }
}
