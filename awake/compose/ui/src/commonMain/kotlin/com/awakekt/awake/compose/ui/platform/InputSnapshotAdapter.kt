/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerModifiers
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton

/**
 * Turns the engine's polled input into a frame the UI can take.
 *
 * **Nothing new is polled or accumulated here.** `Input` is already level-based -- `setKeyDown` in,
 * `keysDown`/`keysPressed`/`keysReleased` out -- and it already computes the edges once, centrally,
 * because every consumer that hand-rolled that diff eventually forgot to refresh it on an
 * early-returned frame. This reads those edges; it does not recompute them.
 *
 * So the UI's event-shaped `keyEvents` and the engine's poll-shaped snapshot are the same data seen
 * twice, and this is the one place that converts. A host keeps polling exactly as it does today.
 */
fun InputSnapshot.toFrameInput(
    viewportWidth: Int,
    viewportHeight: Int,
    deltaSeconds: Float = 1f / 60f,
): FrameInput = FrameInput(
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    pointerX = pointerX.toInt(),
    pointerY = pointerY.toInt(),
    pointerDown = pointerDown,
    pointerPressed = pointerPressed,
    pointerReleased = pointerReleased,
    secondaryPointerPressed = PointerButton.Secondary in buttonsPressed,
    pointerModifiers = pointerModifiers(),
    scrollDeltaY = scrollDeltaY,
    typedText = typedText,
    imeComposition = imeComposition,
    imeCommit = imeCommit,
    editActions = editActions,
    keyEvents = keyEvents(),
    deltaSeconds = deltaSeconds,
)

/**
 * This frame's key transitions, derived from the edges the snapshot already carries.
 *
 * Returns the shared empty list when nothing changed, which is the overwhelmingly common frame: a
 * fast typist produces well under one transition per frame at 60 fps, and holding a key produces
 * none at all after the first. So the steady-state cost of this is zero allocation, and the
 * allocating case is bounded by how many keys a hand can move at once.
 *
 * Modifiers are read from [InputSnapshot.keysDown] rather than tracked separately -- they are keys,
 * and the held-set already answers "is Ctrl down" exactly.
 */
fun InputSnapshot.keyEvents(): List<KeyEvent> {
    if (keysPressed.isEmpty() && keysReleased.isEmpty()) return emptyList()
    val ctrl = Key.Ctrl in keysDown
    val shift = Key.Shift in keysDown
    val alt = Key.Alt in keysDown
    val meta = Key.Meta in keysDown
    val out = ArrayList<KeyEvent>(keysPressed.size + keysReleased.size)
    for (key in keysPressed) {
        out += KeyEvent(key, KeyEventType.Down, ctrl, shift, alt, meta)
    }
    for (key in keysReleased) {
        out += KeyEvent(key, KeyEventType.Up, ctrl, shift, alt, meta)
    }
    return out
}

/**
 * Modifier keys currently held, for this frame's pointer events.
 *
 * Read from [InputSnapshot.keysDown] like `keyEvents` does, and for the same reason: modifiers are
 * keys, and the held-set already answers "is Shift down". Unlike `keyEvents` this cannot early-out
 * on an empty transition set -- a shift-click holds Shift from an earlier frame and produces no
 * transition on the frame the click lands.
 */
fun InputSnapshot.pointerModifiers(): PointerModifiers {
    val ctrl = Key.Ctrl in keysDown
    val shift = Key.Shift in keysDown
    val alt = Key.Alt in keysDown
    val meta = Key.Meta in keysDown
    // The shared instance when nothing is held, which is nearly every frame -- the same reason
    // `keyEvents` returns the shared empty list. Allocating here put a new object on every frame of
    // every app and tripped the input adapter's allocation probe.
    val anyHeld = ctrl || shift || alt || meta
    if (!anyHeld) return PointerModifiers.None
    return PointerModifiers(ctrl, shift, alt, meta)
}
