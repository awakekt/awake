/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.input.ScrollSource

private const val GLFW_KEY_SPACE = 32
private const val GLFW_KEY_0 = 48
private const val GLFW_KEY_A = 65
private const val GLFW_KEY_ESCAPE = 256
private const val GLFW_KEY_ENTER = 257
private const val GLFW_KEY_TAB = 258
private const val GLFW_KEY_BACKSPACE = 259
private const val GLFW_KEY_DELETE = 261
private const val GLFW_KEY_RIGHT = 262
private const val GLFW_KEY_LEFT = 263
private const val GLFW_KEY_DOWN = 264
private const val GLFW_KEY_UP = 265
private const val GLFW_KEY_PAGE_UP = 266
private const val GLFW_KEY_PAGE_DOWN = 267
private const val GLFW_KEY_HOME = 268
private const val GLFW_KEY_END = 269
private const val GLFW_KEY_F1 = 290
private const val GLFW_KEY_KP_ENTER = 335
private const val GLFW_KEY_LEFT_SHIFT = 340
private const val GLFW_KEY_LEFT_CONTROL = 341
private const val GLFW_KEY_LEFT_ALT = 342
private const val GLFW_KEY_LEFT_SUPER = 343
private const val GLFW_KEY_RIGHT_SHIFT = 344
private const val GLFW_KEY_RIGHT_CONTROL = 345
private const val GLFW_KEY_RIGHT_ALT = 346
private const val GLFW_KEY_RIGHT_SUPER = 347
private const val GLFW_MOUSE_BUTTON_LEFT = 0
private const val GLFW_MOUSE_BUTTON_RIGHT = 1
private const val GLFW_MOUSE_BUTTON_MIDDLE = 2
private const val GLFW_MOUSE_BUTTON_4 = 3
private const val GLFW_MOUSE_BUTTON_5 = 4

/**
 * Default mapping of GLFW key codes to Awake [Key] values: every [Key] but [Key.Unknown], so a game
 * reads any key it binds, a window's hotkey or a skill slot's digit as much as W/A/S/D. Shift,
 * Ctrl, Alt and Meta have a key on each side; both map to the one [Key], which is down while
 * either is ([pollGlfwInput]), and the keypad's Enter is [Key.Enter].
 */
val DefaultGlfwGameplayKeys: Map<Int, Key> = linkedMapOf<Int, Key>().apply {
    for (letter in 'A'..'Z') {
        put(GLFW_KEY_A + (letter - 'A'), Key.valueOf(letter.toString()))
    }
    for (digit in 0..9) {
        put(GLFW_KEY_0 + digit, Key.valueOf("Digit$digit"))
    }
    for (n in 1..12) {
        put(GLFW_KEY_F1 + n - 1, Key.valueOf("F$n"))
    }
    put(GLFW_KEY_UP, Key.ArrowUp)
    put(GLFW_KEY_DOWN, Key.ArrowDown)
    put(GLFW_KEY_LEFT, Key.ArrowLeft)
    put(GLFW_KEY_RIGHT, Key.ArrowRight)
    put(GLFW_KEY_SPACE, Key.Space)
    put(GLFW_KEY_ESCAPE, Key.Escape)
    put(GLFW_KEY_ENTER, Key.Enter)
    put(GLFW_KEY_KP_ENTER, Key.Enter)
    put(GLFW_KEY_TAB, Key.Tab)
    put(GLFW_KEY_BACKSPACE, Key.Backspace)
    put(GLFW_KEY_DELETE, Key.Delete)
    put(GLFW_KEY_HOME, Key.Home)
    put(GLFW_KEY_END, Key.End)
    put(GLFW_KEY_PAGE_UP, Key.PageUp)
    put(GLFW_KEY_PAGE_DOWN, Key.PageDown)
    put(GLFW_KEY_LEFT_SHIFT, Key.Shift)
    put(GLFW_KEY_RIGHT_SHIFT, Key.Shift)
    put(GLFW_KEY_LEFT_CONTROL, Key.Ctrl)
    put(GLFW_KEY_RIGHT_CONTROL, Key.Ctrl)
    put(GLFW_KEY_LEFT_ALT, Key.Alt)
    put(GLFW_KEY_RIGHT_ALT, Key.Alt)
    put(GLFW_KEY_LEFT_SUPER, Key.Meta)
    put(GLFW_KEY_RIGHT_SUPER, Key.Meta)
}

/**
 * Polls a GLFW window's keyboard, pointer, and wheel state into Awake's shared [Input].
 *
 * The pointer coordinates are normalized into framebuffer-pixel space so the result matches
 * the coordinates the UI runtime and renderer use on HiDPI displays.
 */
fun pollGlfwInput(
    window: Long,
    input: Input,
    keys: Map<Int, Key> = DefaultGlfwGameplayKeys,
): Unit = pollGlfwInput(glfwWindowInput(window), input, keys)

/** Testable core: takes the [GlfwWindowInput] seam instead of a raw window handle, so a
 * desktopTest can fake key/pointer/scroll state and assert the resulting [Input] calls. */
internal fun pollGlfwInput(
    reader: GlfwWindowInput,
    input: Input,
    keys: Map<Int, Key> = DefaultGlfwGameplayKeys,
) {
    // A Key two GLFW keys map to (a left and a right Shift) is down while either is: clear every
    // Key, then set those any of their keys holds, so the second key's state can't overwrite the
    // first's. Two passes over the map, so a frame allocates nothing for it.
    keys.forEach { (_, key) -> input.setKeyDown(key, false) }
    keys.forEach { (glfwKey, key) ->
        if (reader.isKeyDown(glfwKey)) input.setKeyDown(key, true)
    }

    val pointerX = reader.cursorX().toFloat() * reader.framebufferScaleX()
    val pointerY = reader.cursorY().toFloat() * reader.framebufferScaleY()
    input.setPointer(down = reader.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT), x = pointerX, y = pointerY)
    // Right button drives context menus (shadcnContextMenu gates on secondaryPointerDown).
    input.setSecondaryPointer(reader.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT))
    // The rest carry no dedicated snapshot field and are read from `buttonsDown`. Middle is what a
    // viewport pans with; back/forward exist because GLFW reports them and dropping them here is
    // the kind of gap that is only noticed by whoever needed one.
    input.setButton(PointerButton.Middle, reader.isMouseButtonDown(GLFW_MOUSE_BUTTON_MIDDLE))
    input.setButton(PointerButton.Back, reader.isMouseButtonDown(GLFW_MOUSE_BUTTON_4))
    input.setButton(PointerButton.Forward, reader.isMouseButtonDown(GLFW_MOUSE_BUTTON_5))
    // Accumulate the hardware deltas until the runtime snapshots them. Sideways is a trackpad's
    // two-finger swipe (or a tilt wheel).
    input.scrollDeltaX += reader.consumeScrollDeltaX().toFloat()
    input.scrollDeltaY += reader.consumeScrollDeltaY().toFloat()
    // A frame polled more than once keeps what an earlier poll learned.
    reader.consumeScrollSource().takeIf { it != ScrollSource.Unknown }?.let { input.scrollSource = it }
    // A macOS trackpad pinch zooms about the cursor, which is where AppKit reports it.
    val pinchScale = reader.consumePinchScale()
    if (pinchScale != 1.0) input.addPinch(pinchScale.toFloat(), pointerX, pointerY)
}
