/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneNode

/**
 * The `canvas_element` views of recently read nodes, so reading an unchanged node again builds
 * nothing (#1621).
 *
 * A node saved in the component form has no element of its own: its view is folded from its
 * `canvas_rect` and every other canvas component, a `copy()` of the element's 49 fields per
 * component. A HUD that read it each frame for each slot built about 130 KB a frame. A node is
 * immutable, so a write makes a new node and the view of a node never goes stale; nodes are told
 * apart by identity, since their own `hashCode` would hash every field on every read.
 *
 * [CAPACITY] views, replaced in turn: a lookup compares that many references at most, and a node
 * read again before that many others keeps its view. Each slot holds one immutable entry, so a
 * reader on another thread sees a whole entry, never one node's view paired with another node.
 */
internal object CanvasElementViews {
    private class Entry(val node: SceneNode, val view: SceneCanvasElement)

    private val entries = arrayOfNulls<Entry>(CAPACITY)
    private var next = 0

    /** [node]'s view folded from its components, built once while it stays among the last [CAPACITY] read. */
    fun of(node: SceneNode, build: (SceneNode) -> SceneCanvasElement): SceneCanvasElement {
        for (index in entries.indices) {
            val entry = entries[index]
            if (entry != null && entry.node === node) return entry.view
        }
        val view = build(node)
        val slot = next
        entries[slot] = Entry(node, view)
        next = (slot + 1) % CAPACITY
        return view
    }

    private const val CAPACITY = 128
}
