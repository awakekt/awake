/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.node

/**
 * The modal layer that currently owns the frame, or null when none is open.
 *
 * The **last** one in paint order wins, so a dialog opened on top of a dialog owns the frame rather
 * than handing it back to the one underneath.
 *
 * Shared by focus and by pointer input deliberately. They have to agree on which modal is open: a
 * dialog that traps Tab but lets a click through to the page behind it is worse than one that traps
 * neither, because the keyboard and the mouse then disagree about what is interactive.
 *
 * Walks the tree on each call and allocates nothing. If a profile ever shows the walk mattering, the
 * upgrade is to compute it once per frame in `ComposeHost` and hand it down -- input already runs
 * against the previous frame's placed tree, so a value computed at the end of a frame is exactly as
 * fresh as the geometry it would be used against.
 */
internal fun LayoutNode.activeModalLayer(): LayoutNode? {
    var found: LayoutNode? = null
    for (i in children.indices) {
        children[i].activeModalLayer()?.let { found = it }
    }
    for (i in layers.indices) {
        val layer = layers[i]
        if (layer.isModal) found = layer
        layer.activeModalLayer()?.let { found = it }
    }
    return found
}

/**
 * The topmost layer that owns an outside press, or null when none is open.
 *
 * Separate from [activeEscapeDismissLayer] because the two are not the same question. An alert
 * dialog closes on Escape and deliberately does *not* close when the backdrop is clicked -- that is
 * upstream's stated difference between it and an ordinary dialog, and one flag serving both could
 * not express it.
 */
internal fun LayoutNode.activeDismissableLayer(): LayoutNode? =
    topmostLayer { it.dismissOnOutsideClick }

/** The topmost layer that owns Escape, or null when none is open. */
internal fun LayoutNode.activeEscapeDismissLayer(): LayoutNode? =
    topmostLayer { it.dismissOnEscape }

private fun LayoutNode.topmostLayer(wants: (LayoutNode) -> Boolean): LayoutNode? {
    var found: LayoutNode? = null
    for (i in children.indices) {
        children[i].topmostLayer(wants)?.let { found = it }
    }
    for (i in layers.indices) {
        val layer = layers[i]
        if (wants(layer) && layer.onDismissRequest != null) found = layer
        layer.topmostLayer(wants)?.let { found = it }
    }
    return found
}
