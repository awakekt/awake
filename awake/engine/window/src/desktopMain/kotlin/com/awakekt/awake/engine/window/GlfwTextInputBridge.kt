/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction

private const val GLFW_KEY_LEFT_CONTROL = 341
private const val GLFW_KEY_RIGHT_CONTROL = 345
private const val GLFW_KEY_LEFT_SUPER = 343
private const val GLFW_KEY_RIGHT_SUPER = 347
private const val GLFW_KEY_LEFT_ALT = 342
private const val GLFW_KEY_RIGHT_ALT = 346
private const val GLFW_KEY_A = 65
private const val GLFW_KEY_C = 67
private const val GLFW_KEY_V = 86
private const val GLFW_KEY_X = 88
private const val GLFW_KEY_ENTER = 257
private const val GLFW_KEY_RIGHT = 262
private const val GLFW_KEY_LEFT = 263
private const val GLFW_KEY_DOWN = 264
private const val GLFW_KEY_UP = 265
private const val GLFW_KEY_HOME = 268
private const val GLFW_KEY_END = 269
private const val GLFW_KEY_BACKSPACE = 259
private const val GLFW_KEY_DELETE = 261

private const val REPEAT_INITIAL_DELAY_SECONDS = 0.5
private const val REPEAT_INTERVAL_SECONDS = 0.05

/** The keys of the clipboard and select-all shortcuts: Ctrl (Cmd on macOS) with A, C, V or X. */
private val ShortcutKeys = intArrayOf(GLFW_KEY_A, GLFW_KEY_C, GLFW_KEY_V, GLFW_KEY_X)

private val EditKeys: Map<Int, TextEditAction> = mapOf(
    GLFW_KEY_BACKSPACE to TextEditAction.Backspace,
    GLFW_KEY_DELETE to TextEditAction.Delete,
    GLFW_KEY_ENTER to TextEditAction.Enter,
    GLFW_KEY_LEFT to TextEditAction.ArrowLeft,
    GLFW_KEY_RIGHT to TextEditAction.ArrowRight,
    GLFW_KEY_UP to TextEditAction.ArrowUp,
    GLFW_KEY_DOWN to TextEditAction.ArrowDown,
    GLFW_KEY_HOME to TextEditAction.Home,
    GLFW_KEY_END to TextEditAction.End,
)

private class KeyRepeatState {
    var wasDown = false
    var heldSeconds = 0.0
    var nextRepeatAt = REPEAT_INITIAL_DELAY_SECONDS
}

// Known limit: module-level mutable state (one host process/window), matches the rest of this
// file's polling style -- promote to an instance if multi-window ever lands.
private val repeatStates = HashMap<Int, KeyRepeatState>()

/** Test-only: [repeatStates] is shared module state, so a desktopTest exercising hold-to-repeat
 * across multiple cases must clear it between them or an earlier test's held key leaks in. */
internal fun resetGlfwTextInputRepeatStateForTest() {
    repeatStates.clear()
}

/**
 * Feeds a GLFW window's typing to Awake's shared text-input API ([Input.pushTypedText] and
 * [Input.pushEditAction]) each frame -- separate from [pollGlfwInput], which drives the gameplay
 * [com.awakekt.awake.core.input.Key] set.
 *
 * Text is what the platform typed, from GLFW's character callback: the keyboard layout, Shift,
 * Caps Lock, AltGr and dead keys are already applied, so any layout types the characters on its
 * keys. Edit keys and the clipboard shortcuts are polled, with a hold-to-repeat cadence per key.
 */
fun pollGlfwTextInput(window: Long, input: Input, deltaSeconds: Double = 1.0 / 60.0): Unit =
    pollGlfwTextInput(glfwWindowInput(window), input, deltaSeconds)

/** Cmd on macOS, Ctrl everywhere else: the modifier the platform's own copy and paste use. */
private val ShortcutUsesSuper: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

/** Testable core: takes the [GlfwWindowInput] seam instead of a raw window handle, so a
 * desktopTest can fake typed text and key-hold state across frames and assert the resulting
 * [Input.pushTypedText]/[Input.pushEditAction] calls, including hold-to-repeat.
 *
 * Also the clipboard: Ctrl+C/X/V/A (Cmd on macOS) become copy and cut requests, the clipboard's
 * text typed in, and select-all, and the UI's answer to last frame's copy or cut is written out.
 * Ctrl with Alt is AltGr on Windows, which types a character rather than taking a shortcut. */
internal fun pollGlfwTextInput(
    reader: GlfwWindowInput,
    input: Input,
    deltaSeconds: Double = 1.0 / 60.0,
    shortcutUsesSuper: Boolean = ShortcutUsesSuper,
) {
    input.takeClipboardWrite()?.let { reader.clipboardText = it }

    reader.consumeTypedText().takeIf { it.isNotEmpty() }?.let(input::pushTypedText)

    val shortcutDown = isShortcutModifierDown(reader, shortcutUsesSuper)
    val wordJumpDown = isWordJumpModifierDown(reader, shortcutUsesSuper)

    for (glfwKey in ShortcutKeys) {
        pollKey(reader, glfwKey, deltaSeconds) { if (shortcutDown) fireShortcut(glfwKey, reader, input) }
    }
    EditKeys.forEach { (glfwKey, action) ->
        pollKey(reader, glfwKey, deltaSeconds) {
            input.pushEditAction(resolveEditAction(glfwKey, action, wordJumpDown))
        }
    }
}

private fun isShortcutModifierDown(reader: GlfwWindowInput, shortcutUsesSuper: Boolean): Boolean {
    if (shortcutUsesSuper) {
        return reader.isKeyDown(GLFW_KEY_LEFT_SUPER) || reader.isKeyDown(GLFW_KEY_RIGHT_SUPER)
    }
    val ctrlDown = reader.isKeyDown(GLFW_KEY_LEFT_CONTROL) || reader.isKeyDown(GLFW_KEY_RIGHT_CONTROL)
    val altDown = reader.isKeyDown(GLFW_KEY_LEFT_ALT) || reader.isKeyDown(GLFW_KEY_RIGHT_ALT)
    return ctrlDown && !altDown
}

private fun isWordJumpModifierDown(reader: GlfwWindowInput, shortcutUsesSuper: Boolean): Boolean {
    val ctrlDown = reader.isKeyDown(GLFW_KEY_LEFT_CONTROL) || reader.isKeyDown(GLFW_KEY_RIGHT_CONTROL)
    val altDown = reader.isKeyDown(GLFW_KEY_LEFT_ALT) || reader.isKeyDown(GLFW_KEY_RIGHT_ALT)
    val superDown = reader.isKeyDown(GLFW_KEY_LEFT_SUPER) || reader.isKeyDown(GLFW_KEY_RIGHT_SUPER)
    return if (shortcutUsesSuper) altDown && !ctrlDown && !superDown else ctrlDown && !altDown && !superDown
}

private fun resolveEditAction(glfwKey: Int, defaultAction: TextEditAction, wordJumpDown: Boolean): TextEditAction {
    if (!wordJumpDown) return defaultAction
    return when (glfwKey) {
        GLFW_KEY_LEFT -> TextEditAction.WordLeft
        GLFW_KEY_RIGHT -> TextEditAction.WordRight
        else -> defaultAction
    }
}

private fun fireShortcut(glfwKey: Int, reader: GlfwWindowInput, input: Input) {
    when (glfwKey) {
        GLFW_KEY_C -> input.pushClipboardCommand(ClipboardCommand.Copy)
        GLFW_KEY_X -> input.pushClipboardCommand(ClipboardCommand.Cut)
        GLFW_KEY_V -> reader.clipboardText?.takeIf { it.isNotEmpty() }?.let(input::pushTypedText)
        GLFW_KEY_A -> input.pushEditAction(TextEditAction.SelectAll)
    }
}

private inline fun pollKey(reader: GlfwWindowInput, glfwKey: Int, deltaSeconds: Double, fire: () -> Unit) {
    val state = repeatStates.getOrPut(glfwKey) { KeyRepeatState() }
    val down = reader.isKeyDown(glfwKey)
    if (!down) {
        state.wasDown = false
        state.heldSeconds = 0.0
        state.nextRepeatAt = REPEAT_INITIAL_DELAY_SECONDS
        return
    }
    if (!state.wasDown) {
        state.wasDown = true
        state.heldSeconds = 0.0
        state.nextRepeatAt = REPEAT_INITIAL_DELAY_SECONDS
        fire()
        return
    }
    state.heldSeconds += deltaSeconds
    if (state.heldSeconds >= state.nextRepeatAt) {
        state.nextRepeatAt += REPEAT_INTERVAL_SECONDS
        fire()
    }
}
