/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/** `KeyboardEvent.key` -> [TextEditAction] for the same discrete edit set
 * [GlfwTextInputBridge]/[AwakeUIKitTextInputBridge] push on desktop/iOS. */
internal val DomEditKeys: Map<String, TextEditAction> =
    mapOf(
        "Backspace" to TextEditAction.Backspace,
        "Delete" to TextEditAction.Delete,
        "Enter" to TextEditAction.Enter,
        "ArrowLeft" to TextEditAction.ArrowLeft,
        "ArrowRight" to TextEditAction.ArrowRight,
        "ArrowUp" to TextEditAction.ArrowUp,
        "ArrowDown" to TextEditAction.ArrowDown,
        "Home" to TextEditAction.Home,
        "End" to TextEditAction.End,
    )

/**
 * The browser's keyboard target while an Awake text field has focus: a hidden `<input>`.
 *
 * A canvas is not editable, so without a real input the browser has nothing to raise a mobile
 * keyboard for, no element to run IME composition in, no field to tell a password manager or
 * keyboard "this is a password", and no target for the clipboard events copy and paste arrive
 * as. This element is all four, and none of it is visible: it is transparent, one pixel, and
 * ignores the pointer.
 *
 * [sync] runs once per frame and mirrors the UI: the element is focused while
 * [Input.textInputFocused] holds, and is `type=password` while [Input.textInputPassword] does,
 * which is what stops keyboards learning and suggesting the text and makes the browser refuse
 * native copy from it.
 *
 * Text is read from `input` events rather than `keydown`, so dead keys, IME commits, mobile
 * keyboards and autocorrect replacements all arrive as the characters the user meant. The
 * element's value is emptied after each read: the UI's `TextFieldState` owns the text, this only
 * relays it. A keyboard that edits that value in place (some autocorrects) is not mirrored back --
 * the value only ever holds what arrived since the last read.
 *
 * Mobile browsers only raise a keyboard for a focus made inside a user gesture, and the UI
 * decides focus a frame after the tap. A tap that lands while a field already has focus refocuses
 * inside the gesture; the first tap into a field on iOS Safari may need a second tap.
 */
class DomTextInputBridge(private val input: Input) {
    private val element: HTMLInputElement = (document.createElement("input") as HTMLInputElement).apply {
        type = "text"
        tabIndex = -1
        setAttribute("autocomplete", "off")
        setAttribute("autocapitalize", "off")
        setAttribute("autocorrect", "off")
        setAttribute("spellcheck", "false")
        setAttribute("aria-hidden", "true")
        setAttribute("data-awake-text-input", "")
        // 16px keeps iOS Safari from zooming the page when the element takes focus.
        style.cssText = "position:fixed;left:0;top:0;width:1px;height:1px;opacity:0;" +
            "pointer-events:none;border:0;padding:0;margin:0;font-size:16px;"
    }

    private var composing = false

    init {
        document.body?.appendChild(element)
        element.addEventListener("keydown", ::onKeyDown)
        element.addEventListener("input") { readInput(it) }
        element.addEventListener("compositionstart") { composing = true }
        element.addEventListener("compositionupdate") { event ->
            input.setImeComposition(ImeComposition(compositionData(event) ?: element.value))
        }
        element.addEventListener("compositionend") { event ->
            composing = false
            // Read the event's data, not the value: Safari fires this before the final `input`,
            // Chrome after, and only the data is the committed text in both.
            input.commitImeText(compositionData(event) ?: element.value)
            element.value = ""
        }
        element.addEventListener("paste") { event ->
            event.preventDefault()
            pastedText(event)?.takeIf { it.isNotEmpty() }?.let(input::pushTypedText)
        }
        // Copy and cut need the UI's answer, which comes a frame later; the default action would
        // only copy this element's empty selection, so it is cancelled and [sync] writes the answer.
        element.addEventListener("copy") { event ->
            event.preventDefault()
            input.pushClipboardCommand(ClipboardCommand.Copy)
        }
        element.addEventListener("cut") { event ->
            event.preventDefault()
            input.pushClipboardCommand(ClipboardCommand.Cut)
        }
        // A tap is a user gesture: focusing inside it is what lets a mobile browser raise its
        // keyboard. Only while a field already holds focus, so a tap elsewhere never flashes one.
        document.addEventListener("pointerup") { if (input.textInputFocused) focus() }
    }

    /** Mirrors this frame's text focus, password state and clipboard answer onto the page. */
    fun sync() {
        val wantedType = if (input.textInputPassword) "password" else "text"
        if (element.type != wantedType) element.type = wantedType
        val focused = document.activeElement == element
        if (input.textInputFocused && !focused) {
            focus()
        } else if (!input.textInputFocused && focused) {
            element.blur()
            element.value = ""
            composing = false
        }
        input.takeClipboardWrite()?.let(::writeClipboardText)
    }

    private fun focus() = focusWithoutScrolling(element)

    private fun onKeyDown(event: Event) {
        val key = event as KeyboardEvent
        // While an IME composes, keys belong to it; the result arrives through composition events.
        if (composing || isComposing(key)) return
        val shortcut = key.ctrlKey || key.metaKey
        when {
            shortcut && key.key.equals("a", ignoreCase = true) -> {
                key.preventDefault()
                input.pushEditAction(TextEditAction.SelectAll)
            }
            // Ctrl/Cmd+C, X and V arrive as copy, cut and paste events; other chords are the page's.
            shortcut -> Unit
            else -> DomEditKeys[key.key]?.let { action ->
                // Cancelled so arrows do not scroll the page and Backspace never edits the value.
                key.preventDefault()
                input.pushEditAction(action)
            }
        }
    }

    private fun readInput(event: Event) {
        if (composing || isComposing(event)) return
        val text = element.value
        element.value = ""
        if (text.isNotEmpty()) input.pushTypedText(text)
    }
}

@JsFun("(element) => element.focus({ preventScroll: true })")
private external fun focusWithoutScrolling(element: HTMLInputElement)

@JsFun("(event) => event.isComposing === true || event.keyCode === 229")
private external fun isComposing(event: Event): Boolean

@JsFun("(event) => typeof event.data === 'string' ? event.data : null")
private external fun compositionData(event: Event): String?

@JsFun("(event) => event.clipboardData ? event.clipboardData.getData('text/plain') : null")
private external fun pastedText(event: Event): String?

// Async and permission-gated: a page without clipboard access (an insecure origin, a denied
// permission) keeps its old clipboard rather than failing the frame.
@JsFun(
    "(text) => { if (navigator.clipboard && navigator.clipboard.writeText) " +
        "navigator.clipboard.writeText(text).catch((e) => console.warn('Awake: clipboard write failed', e)); }",
)
private external fun writeClipboardText(text: String)
