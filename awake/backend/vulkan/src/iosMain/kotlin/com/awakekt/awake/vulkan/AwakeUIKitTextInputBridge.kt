/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction
import platform.UIKit.UIView
import platform.UIKit.reloadInputViews

/**
 * Wires [VulkanMetalView]'s `UIKeyInput` conformance (declared on the view itself, see its
 * class doc) to [Input] -- `UIKeyInput` rather than the full `UITextInput` protocol
 * deliberately: no selection ranges/marked text/autocomplete UI needed for a v1 that only
 * needs typed text + Backspace + Enter (see the task that added this file).
 */
fun UIView.syncAwakeTextInsert(text: String, input: Input) {
    if (text == "\n") {
        input.pushEditAction(TextEditAction.Enter)
    } else {
        input.pushTypedText(text)
    }
}

fun UIView.syncAwakeTextDeleteBackward(input: Input) {
    input.pushEditAction(TextEditAction.Backspace)
}

/**
 * Polls [Input.textInputFocused] and calls `becomeFirstResponder`/`resignFirstResponder` on
 * the rising/falling edge only -- [wasFocused] is the caller's own previous-value slot (see
 * [VulkanMetalView]) so repeated calls while focus state is unchanged don't spam UIKit with
 * redundant first-responder churn every frame.
 */
fun UIView.syncAwakeTextInputFocus(wasFocused: Boolean, input: Input): Boolean {
    val isFocused = input.textInputFocused
    if (isFocused != wasFocused) {
        if (isFocused) becomeFirstResponder() else resignFirstResponder()
    }
    return isFocused
}

/**
 * Whether the keyboard should treat what is typed as a password: what the view's
 * `isSecureTextEntry` answers, so iOS neither learns, suggests nor autocorrects it.
 */
fun awakeSecureTextEntry(input: Input): Boolean = input.textInputFocused && input.textInputPassword

/**
 * Reloads the keyboard when [awakeSecureTextEntry] changes.
 *
 * UIKit reads text input traits when a responder becomes first responder, so focus moving between
 * a plain field and a password field with the keyboard up would otherwise keep the old traits.
 * Reloading a view that is not first responder does nothing, so the edge alone gates it.
 * [wasSecure] is the caller's previous-value slot, as [syncAwakeTextInputFocus]'s is.
 */
fun UIView.syncAwakeSecureTextEntry(wasSecure: Boolean, input: Input): Boolean {
    val isSecure = awakeSecureTextEntry(input)
    // A UIResponder category method, so Kotlin/Native sees an imported extension, not a member.
    if (isSecure != wasSecure) reloadInputViews()
    return isSecure
}
