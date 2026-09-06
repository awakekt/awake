/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.focus.focusTarget
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.input.key.onPreviewKeyEvent
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object KeyType

private val keyStack = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: 0
    val h = placeables.maxOfOrNull { it.height } ?: 0
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun k(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(KeyType, modifier = modifier, measurePolicy = keyStack, content = content)

private fun down(key: Key, shift: Boolean = false, ctrl: Boolean = false) =
    KeyEvent(key, KeyEventType.Down, isShiftPressed = shift, isCtrlPressed = ctrl)

/** One frame carrying [keys]. */
private fun ComposeHost.press(vararg keys: KeyEvent, content: context(Composer) () -> Unit) =
    frame(FrameInput(viewportWidth = 100, viewportHeight = 100, keyEvents = keys.toList()), content)

class KeyInputTest {

    @Test
    fun aFocusedNodeReceivesKeys() {
        val seen = mutableListOf<String>()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(
                Modifier.size(50.dp).focusTarget().onKeyEvent {
                    seen += it.key.name
                    true
                },
            )
        }
        host.press(content = content)
        host.focusOwner.requestFocus(host.root, host.root.children[0])
        host.press(down(Key.Escape), content = content)

        assertEquals(listOf("Escape"), seen)
    }

    @Test
    fun modifiersArriveWithTheKey() {
        // The whole reason keys are a separate channel from typedText: "Ctrl+S" produces no
        // character at all, so a shortcut cannot be built from the text stream.
        var shortcut = false
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(
                Modifier.size(50.dp).focusTarget().onKeyEvent { event ->
                    if (event.key == Key.S && event.isCtrlPressed) shortcut = true
                    shortcut
                },
            )
        }
        host.press(content = content)
        host.focusOwner.requestFocus(host.root, host.root.children[0])
        host.press(down(Key.S, ctrl = true), content = content)

        assertTrue(shortcut)
    }

    @Test
    fun anUnfocusedTreeStillDeliversToTheRoot() {
        // A global shortcut must not need a focused widget to fire.
        val seen = mutableListOf<String>()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(Modifier.size(50.dp))
        }
        host.press(content = content)
        host.press(down(Key.Escape), content = content)

        assertTrue(seen.isEmpty(), "nothing declared a handler, so nothing should have run")
    }

    @Test
    fun anAncestorCanTakeAKeyBeforeTheFocusedNodeSeesIt() {
        // Preview is why a dialog can close on Escape even though the text field inside it would
        // otherwise read Escape as "clear selection" and consume it first.
        val order = mutableListOf<String>()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(
                Modifier.onPreviewKeyEvent {
                    order += "dialog"
                    true
                },
            ) {
                k(
                    Modifier.size(50.dp).focusTarget().onKeyEvent {
                        order += "field"
                        true
                    },
                )
            }
        }
        host.press(content = content)
        host.focusOwner.requestFocus(host.root, host.root.children[0].children[0])
        host.press(down(Key.Escape), content = content)

        assertEquals(listOf("dialog"), order, "the focused node saw a key its ancestor claimed")
    }

    @Test
    fun anUnclaimedKeyReachesTheFocusedNodeThenItsAncestors() {
        val order = mutableListOf<String>()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(
                Modifier.onKeyEvent {
                    order += "outer"
                    false
                },
            ) {
                k(
                    Modifier.size(50.dp).focusTarget().onKeyEvent {
                        order += "inner"
                        false
                    },
                )
            }
        }
        host.press(content = content)
        host.focusOwner.requestFocus(host.root, host.root.children[0].children[0])
        host.press(down(Key.Escape), content = content)

        assertEquals(listOf("inner", "outer"), order, "Main runs leaf to root")
    }
}

/**
 * The tab ring, driven the way an app drives it.
 *
 * `FocusOwner.moveFocus` was implemented and fully tested, and had **twenty call sites, every one of
 * them in a test**. Nothing in the engine or any host called it, because there was no Tab key to
 * call it with -- so the ring worked and was unreachable in a running app.
 */
class TabTraversalTest {

    private fun twoFocusables(): Pair<
        ComposeHost,
        context(Composer)
        () -> Unit,
        > {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(Modifier.size(20.dp).focusTarget())
            k(Modifier.size(20.dp).focusTarget())
        }
        host.press(content = content)
        return host to content
    }

    @Test
    fun tabMovesFocusForward() {
        val (host, content) = twoFocusables()
        host.focusOwner.requestFocus(host.root, host.root.children[0])

        host.press(down(Key.Tab), content = content)

        assertEquals(host.root.children[1], host.focusOwner.focused)
    }

    @Test
    fun shiftTabMovesItBack() {
        val (host, content) = twoFocusables()
        host.focusOwner.requestFocus(host.root, host.root.children[1])

        host.press(down(Key.Tab, shift = true), content = content)

        assertEquals(host.root.children[0], host.focusOwner.focused)
    }

    @Test
    fun aNodeThatWantsTabKeepsIt() {
        // An editor inserting an indent. Traversal runs only on an unconsumed Tab, so the ring
        // stays put rather than yanking focus out from under the thing being typed into.
        var handled = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            k(
                Modifier.size(20.dp).focusTarget().onKeyEvent {
                    handled++
                    true
                },
            )
            k(Modifier.size(20.dp).focusTarget())
        }
        host.press(content = content)
        host.focusOwner.requestFocus(host.root, host.root.children[0])

        host.press(down(Key.Tab), content = content)

        assertEquals(1, handled)
        assertEquals(host.root.children[0], host.focusOwner.focused, "focus moved anyway")
    }
}
