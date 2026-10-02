/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * [InputConnection] fed to the IME by `VulkanView` (awake:engine:platform)
 * .onCreateInputConnection -- a `SurfaceView` has no text field of its own for the IME to edit,
 * so this forwards committed text/deletes straight into [input] instead of maintaining an
 * `Editable`.
 *
 * @param targetView Target view owning this input connection.
 * @param input Target Awake [Input] accumulator receiving text events.
 */
class AwakeInputConnection(targetView: View, private val input: Input) : BaseInputConnection(targetView, false) {

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        input.pushTypedText(text.toString())
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength) { input.pushEditAction(TextEditAction.Backspace) }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> input.pushEditAction(TextEditAction.Enter)
                KeyEvent.KEYCODE_DEL -> input.pushEditAction(TextEditAction.Backspace)
            }
        }
        return super.sendKeyEvent(event)
    }
}

/**
 * Creates an [InputConnection] configuring [outAttrs] and forwarding IME actions into [input].
 *
 * While [Input.textInputPassword] is set the editor is declared a password field, so the keyboard
 * neither learns the text nor offers suggestions for it -- what `android:inputType="textPassword"`
 * gives a real `EditText`.
 *
 * @param outAttrs Attributes describing the editor configuration.
 * @param input Target Awake [Input] accumulator receiving text events.
 * @return An [InputConnection] bound to this view and the input accumulator.
 */
fun View.createAwakeInputConnection(outAttrs: EditorInfo, input: Input): InputConnection {
    outAttrs.inputType = awakeInputType(password = input.textInputPassword)
    outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE
    return AwakeInputConnection(this, input)
}

/** The `EditorInfo.inputType` for a plain or a password field. */
internal fun awakeInputType(password: Boolean): Int = if (password) {
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
} else {
    InputType.TYPE_CLASS_TEXT
}

/**
 * Polls [Input.textInputFocused] once per frame and shows/hides the soft keyboard on its
 * rising/falling edge.
 *
 * Also watches [Input.textInputPassword]: the IME reads `inputType` only when it (re)connects, so
 * focus moving from a plain field to a password field without the keyboard closing would otherwise
 * keep suggesting and learning the password. A change restarts the input connection.
 *
 * @param view The hosting Android [View].
 * @param input The [Input] instance whose focus state is monitored.
 */
class AndroidSoftKeyboardBridge(private val view: View, private val input: Input) {
    private var wasFocused = false

    // What the live connection told the IME. The view's first connection is a plain one.
    private var connectedAsPassword = false

    /** Synchronizes soft keyboard visibility based on current text input focus state. */
    fun syncSoftKeyboardVisibility() {
        val focused = input.textInputFocused
        val password = focused && input.textInputPassword
        if (focused == wasFocused && (!focused || password == connectedAsPassword)) return
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (focused && password != connectedAsPassword) {
            // Reconnects, which is what makes the IME read the new inputType.
            imm.restartInput(view)
            connectedAsPassword = password
        }
        if (focused != wasFocused) {
            wasFocused = focused
            if (focused) {
                view.requestFocus()
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            } else {
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }
}
