/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasChanges
import com.awakekt.awake.ui.canvas.CanvasControls
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasFollowLimit
import com.awakekt.awake.ui.canvas.CanvasScaler
import com.awakekt.awake.ui.canvas.CanvasViewWidget
import kotlin.reflect.KClass

/**
 * What [SceneCanvas] draws from in [world], as one value a host that skips unchanged frames polls
 * (#1512): it answers a new object whenever anything changed since it last answered, and the same
 * one otherwise. Reading it allocates nothing unless something changed, and costs the same whatever
 * the elements hold: every element reports its changes to one [CanvasChanges].
 *
 * It sees:
 * - a write that changes what an element shows, through the element or straight to a component,
 *   by the world's one [CanvasChanges], which [refresh] gives every element, so canvases that draw
 *   the same world share it;
 * - an element, or a canvas component, widget, scaler, control set or follow limit, added, replaced
 *   or removed on an entity, by the revisions of their stores: two elements swapped between two
 *   entities are two replacements, however alike they are;
 * - a node's parent or sibling place changing, through [order];
 * - the [images] and [fonts] gaining entries, and the [locale] the text is shown in.
 *
 * Some elements show what changes with no write at all, so while one shows, every poll is a change
 * and the canvas composes each frame, as it does with skipping off: one that follows a node (the
 * camera moves it), reads a binding (game state), shows a camera's picture, or is being held,
 * dragged, moved or typed into. [refresh] finds them, on each frame the canvas composes.
 */
internal class SceneCanvasChanges(private val world: World, private val order: CanvasItemOrder) : PolledState<Any>() {
    private val stores: Array<ComponentStore<Any>> = Array(WATCHED_TYPES.size) { world.store(WATCHED_TYPES[it]) }
    private val storeRevisions = IntArray(stores.size) { -1 }
    private var counter = world.canvasChanges()
    private var counted = -1
    private var volatile = false
    private var token = Any()

    /** Whether [order] is used: false while the host passes its own sibling order, which this cannot watch. */
    var ordered = true

    var images: Map<*, *> = emptyMap<Any, Any>()
    var fonts: Map<*, *> = emptyMap<Any, Any>()
    var locale: Any? = null
    private var imageCount = -1
    private var fontCount = -1
    private var seenLocale: Any? = null

    /** Gives every element the counter and finds whether one shows what changes on its own: a walk, run when composing. */
    fun refresh() {
        counter = world.canvasChanges()
        var found = false
        world.family<CanvasElement>().forEach { _, element ->
            if (element.changes !== counter) element.changes = counter
            if (!found && volatile(element)) found = true
        }
        volatile = found
    }

    override fun read(): Any {
        if (changed()) token = Any()
        return token
    }

    /** Whether anything changed since the last call; it records what it saw either way. */
    private fun changed(): Boolean {
        var changed = volatile || !ordered || !order.isCurrent() || counter.revision != counted
        counted = counter.revision
        for (index in stores.indices) {
            val revision = stores[index].revision
            if (revision != storeRevisions[index]) changed = true
            storeRevisions[index] = revision
        }
        if (images.size != imageCount || fonts.size != fontCount || locale != seenLocale) changed = true
        imageCount = images.size
        fontCount = fonts.size
        seenLocale = locale
        return changed
    }

    /** Whether [element], while it shows, changes what it draws with no write to it. */
    private fun volatile(element: CanvasElement): Boolean {
        if (!element.visible) return false
        // Not `?.camera?.isNotEmpty() == true`: a Boolean? that Kotlin/Wasm can box.
        val view = element.widget as? CanvasViewWidget
        return element.follow.isNotEmpty() || element.bind != null || (view != null && view.camera.isNotEmpty()) ||
            element.isHeld || element.pointer.dragging || element.moving || element.focused
    }

    private companion object {
        /** Every store whose additions, replacements and removals change what the canvas draws. */
        @Suppress("UNCHECKED_CAST") // Each store holds its own type.
        val WATCHED_TYPES: Array<KClass<Any>> = (
            CANVAS_COMPONENT_TYPES.toList() + CANVAS_WIDGET_TYPES.toList() + CanvasElement::class +
                CanvasScaler::class + CanvasControls::class + CanvasFollowLimit::class
            ).toTypedArray() as Array<KClass<Any>>
    }
}

/** The [CanvasChanges] every canvas element in this world reports to, held as the one component of an entity of its own. */
internal fun World.canvasChanges(): CanvasChanges {
    val held = family<CanvasChanges>()
    return if (held.size > 0) held.componentAt(0) else CanvasChanges().also { add(create(), it) }
}
