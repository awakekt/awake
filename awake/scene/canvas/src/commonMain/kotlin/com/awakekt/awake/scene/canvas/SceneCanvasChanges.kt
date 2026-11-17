/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasControls
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasFollowLimit
import com.awakekt.awake.ui.canvas.CanvasScaler
import com.awakekt.awake.ui.canvas.CanvasViewWidget
import com.awakekt.awake.ui.canvas.contentRevision
import kotlin.reflect.KClass

/**
 * What [SceneCanvas] draws from in [world], as one value a host that skips unchanged frames polls
 * (#1512): it answers a new object whenever anything changed since it last answered, and the same
 * one otherwise. Reading it allocates nothing unless something changed.
 *
 * It sees:
 * - each element's [contentRevision]: a write through the element or straight to a component;
 * - which elements there are and in what order, element by element, so two swaps that keep the
 *   count and every revision still count, as an element added, removed or replaced does;
 * - a canvas component, widget, scaler, control set or follow limit added, replaced or removed on
 *   an entity, and a node's parent or sibling place changing, through [order];
 * - the [images] and [fonts] gaining entries, and the [locale] the text is shown in.
 *
 * Some elements show what changes with no write at all, so while one shows, every poll is a
 * change and the canvas composes each frame, as it does with skipping off: one that follows a node
 * (the camera moves it), reads a binding (game state), shows a camera's picture, or is being held,
 * dragged, moved or typed into.
 */
internal class SceneCanvasChanges(private val world: World, private val order: CanvasItemOrder) : PolledState<Any>() {
    private val stores: Array<ComponentStore<Any>> = Array(WATCHED_TYPES.size) { world.store(WATCHED_TYPES[it]) }
    private val storeRevisions = IntArray(stores.size) { -1 }
    private var entities = LongArray(0)
    private var elements = arrayOfNulls<CanvasElement>(0)
    private var revisions = IntArray(0)
    private var count = -1
    private var token = Any()

    /** Whether [order] is used: false while the host passes its own sibling order, which this cannot watch. */
    var ordered = true

    var images: Map<*, *> = emptyMap<Any, Any>()
    var fonts: Map<*, *> = emptyMap<Any, Any>()
    var locale: Any? = null
    private var imageCount = -1
    private var fontCount = -1
    private var seenLocale: Any? = null

    override fun read(): Any {
        if (changed()) token = Any()
        return token
    }

    /** Whether anything changed since the last call; it records what it saw either way. */
    private fun changed(): Boolean {
        var changed = !ordered || !order.isCurrent()
        for (index in stores.indices) {
            val revision = stores[index].revision
            if (revision != storeRevisions[index]) changed = true
            storeRevisions[index] = revision
        }
        if (images.size != imageCount || fonts.size != fontCount || locale != seenLocale) changed = true
        imageCount = images.size
        fontCount = fonts.size
        seenLocale = locale
        return elementsChanged() || changed
    }

    private fun elementsChanged(): Boolean {
        val family = world.family<CanvasElement>()
        val size = family.size
        var changed = size != count
        if (entities.size < size) grow(size)
        var index = 0
        family.forEach { entity, element ->
            val revision = element.contentRevision
            if (entities[index] != entity.packed || elements[index] !== element || revisions[index] != revision || volatile(element)) {
                changed = true
            }
            entities[index] = entity.packed
            elements[index] = element
            revisions[index] = revision
            index++
        }
        for (stale in size until count.coerceAtLeast(0)) elements[stale] = null
        count = size
        return changed
    }

    private fun grow(size: Int) {
        val capacity = maxOf(size, entities.size * 2)
        entities = entities.copyOf(capacity)
        elements = elements.copyOf(capacity)
        revisions = revisions.copyOf(capacity)
    }

    /** Whether [element], while it shows, changes what it draws with no write to it. */
    private fun volatile(element: CanvasElement): Boolean = element.visible && (
        element.follow.isNotEmpty() || element.bind != null || (element.widget as? CanvasViewWidget)?.camera?.isNotEmpty() == true ||
            element.isHeld || element.pointer.dragging || element.moving || element.focused
        )

    private companion object {
        /** Every store whose additions, replacements and removals change what the canvas draws. */
        @Suppress("UNCHECKED_CAST") // Each store holds its own type.
        val WATCHED_TYPES: Array<KClass<Any>> = (
            CANVAS_COMPONENT_TYPES.toList() + CANVAS_WIDGET_TYPES.toList() + CanvasElement::class +
                CanvasScaler::class + CanvasControls::class + CanvasFollowLimit::class
            ).toTypedArray() as Array<KClass<Any>>
    }
}
