/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key

/**
 * The modifier [Key]s a DOM `WheelEvent` reports held, from its `ctrlKey`, `shiftKey`, `altKey` and
 * `metaKey` flags.
 *
 * A browser sends a trackpad pinch as a wheel with `ctrlKey` set, though no Ctrl key was pressed: the
 * same event shape as Ctrl+wheel on a desktop window, where Ctrl is a held [Key.Ctrl] beside the
 * scroll delta. This is how the browser's pinch reaches a consumer written for that.
 */
internal fun wheelModifierKeys(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Set<Key> =
    buildSet {
        if (ctrl) add(Key.Ctrl)
        if (shift) add(Key.Shift)
        if (alt) add(Key.Alt)
        if (meta) add(Key.Meta)
    }

/**
 * Holds the modifiers a wheel arrived with in [input] for the frame that reads that wheel.
 *
 * The browser reports modifiers on the wheel event, not as key events, and a pinch has no key event
 * at all, so there is nothing to release them. [afterFrame] runs its action once the frame loop has
 * taken its snapshot (a host passes `requestAnimationFrame`, whose callbacks run in the order they
 * were registered, after the loop's own), and the keys this class pressed come up then.
 *
 * A key that was already down, from a keyboard binding, is left alone: it is not this class's to release.
 */
internal class WheelModifiers(
    private val input: Input,
    private val afterFrame: (action: () -> Unit) -> Unit,
) {
    private val pressed = mutableSetOf<Key>()
    private var releaseScheduled = false

    /** Holds [keys] down until the frame that reads this wheel has passed. */
    fun hold(keys: Set<Key>) {
        if (keys.isEmpty()) return
        for (key in keys) {
            if (!input.isKeyDown(key)) {
                input.setKeyDown(key, true)
                pressed += key
            }
        }
        if (pressed.isNotEmpty() && !releaseScheduled) {
            releaseScheduled = true
            afterFrame(::release)
        }
    }

    private fun release() {
        releaseScheduled = false
        pressed.forEach { input.setKeyDown(it, false) }
        pressed.clear()
    }
}
