/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.getValue
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.setValue
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.TextEditAction
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnInputGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnPagination
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextarea
import com.awakekt.awake.ui.shadcn.components.ShadcnToggle
import com.awakekt.awake.ui.shadcn.components.ShadcnToggleGroup
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retained-host (`ComposeHost`) regression suite for shared button and input recipe semantics,
 * keyboard activation, `onSubmit`, disabled-input blocking, and `Tab` / `Shift+Tab` focus traversal.
 */
class ShadcnFormRecipeContractsTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun List<SemanticsNode>.byTag(tag: String): SemanticsNode =
        firstNotNullOfOrNull { findByTag(it, tag) } ?: error("no semantics node with tag '$tag'")

    private fun findByTag(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { findByTag(it, tag) }

    private fun List<SemanticsNode>.byLabel(label: String): SemanticsNode =
        firstNotNullOfOrNull { findByLabel(it, label) } ?: error("no semantics node with label '$label'")

    private fun findByLabel(node: SemanticsNode, label: String): SemanticsNode? =
        if (node.label == label) node else node.children.firstNotNullOfOrNull { findByLabel(it, label) }

    private fun clickNode(
        host: ComposeHost,
        node: SemanticsNode,
        width: Int = 320,
        height: Int = 240,
        content: context(Composer) () -> Unit,
    ) {
        val x = node.x + node.width / 2
        val y = node.y + node.height / 2
        host.frame(FrameInput(width, height, pointerX = x, pointerY = y, pointerDown = true), content)
        host.frame(FrameInput(width, height, pointerX = x, pointerY = y), content)
    }

    private fun pressKey(
        host: ComposeHost,
        key: Key,
        width: Int = 320,
        height: Int = 240,
        shift: Boolean = false,
        ctrl: Boolean = false,
        content: context(Composer) () -> Unit,
    ) {
        host.frame(
            FrameInput(
                viewportWidth = width,
                viewportHeight = height,
                keyEvents = listOf(
                    KeyEvent(
                        key = key,
                        type = KeyEventType.Down,
                        isShiftPressed = shift,
                        isCtrlPressed = ctrl,
                    ),
                ),
            ),
            content,
        )
        host.frame(
            FrameInput(
                viewportWidth = width,
                viewportHeight = height,
                keyEvents = listOf(
                    KeyEvent(
                        key = key,
                        type = KeyEventType.Up,
                        isShiftPressed = shift,
                        isCtrlPressed = ctrl,
                    ),
                ),
            ),
            content,
        )
    }

    @Test
    fun buttonPublishesRoleLabelAndFiresOncePerPointerEnterAndSpaceActivation() {
        var clicks = 0
        val requester = FocusRequester()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                ShadcnButton(
                    "Action",
                    Modifier.testTag("button").focusRequester(requester),
                    onClick = { clicks++ },
                )
            }
        }

        val first = host.frame(FrameInput(viewportWidth = 320, viewportHeight = 120), content)
        val button = first.semantics.byTag("button")
        assertEquals(SemanticsRole.Button, button.role)
        assertEquals("Action", button.label)
        assertNull(button.config[SemanticsProperties.Disabled])

        clickNode(host, button, height = 120, content = content)
        assertEquals(1, clicks, "pointer click must fire onClick once")

        // Reset focus first by recreating focus via FocusRequester and test Enter Down + Up.
        requester.freeFocus()
        host.frame(FrameInput(viewportWidth = 320, viewportHeight = 120), content)
        requester.requestFocus()
        host.frame(
            FrameInput(
                viewportWidth = 320,
                viewportHeight = 120,
                keyEvents = listOf(KeyEvent(Key.Enter, KeyEventType.Down)),
            ),
            content,
        )
        assertEquals(2, clicks, "Enter key-down on focused ShadcnButton must fire onClick immediately")
        host.frame(
            FrameInput(
                viewportWidth = 320,
                viewportHeight = 120,
                keyEvents = listOf(KeyEvent(Key.Enter, KeyEventType.Up)),
            ),
            content,
        )
        assertEquals(2, clicks, "Enter key-up must not fire onClick a second time")

        pressKey(host, Key.Space, height = 120, content = content)
        assertEquals(3, clicks, "Space press+release must fire onClick once")

        pressKey(host, Key.Enter, height = 120, ctrl = true, content = content)
        assertEquals(3, clicks, "Ctrl+Enter must not falsely activate a button")
    }

    @Test
    fun disabledButtonRejectsFocusPointerAndKeyboardActivation() {
        var clicks = 0
        val requester = FocusRequester()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                ShadcnButton(
                    "Disabled Action",
                    Modifier.testTag("button").focusRequester(requester),
                    enabled = false,
                    onClick = { clicks++ },
                )
            }
        }

        val first = host.frame(FrameInput(viewportWidth = 320, viewportHeight = 120), content)
        val button = first.semantics.byTag("button")
        assertEquals(SemanticsRole.Button, button.role)
        assertEquals("Disabled Action", button.label)
        assertEquals(true, button.config[SemanticsProperties.Disabled])

        clickNode(host, button, height = 120, content = content)
        requester.requestFocus()
        pressKey(host, Key.Enter, height = 120, content = content)
        pressKey(host, Key.Space, height = 120, content = content)

        assertEquals(0, clicks, "disabled ShadcnButton must never fire onClick")
    }

    @Test
    fun contentButtonButtonGroupAndPaginationSupportSemanticsAndKeyboardActivation() {
        var contentClicks = 0
        var groupApplyClicks = 0
        var groupLockedClicks = 0
        var page = 1
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                Column {
                    ShadcnButton(modifier = Modifier.testTag("content-btn"), onClick = { contentClicks++ }) {
                        ShadcnText("Custom Trigger")
                    }
                    ShadcnButtonGroup {
                        button("Apply", modifier = Modifier.testTag("group-apply"), onClick = { groupApplyClicks++ })
                        button(
                            "Locked",
                            modifier = Modifier.testTag("group-locked"),
                            enabled = false,
                            onClick = { groupLockedClicks++ },
                        )
                    }
                    ShadcnPagination(currentPage = page, totalPages = 2, onPageChange = { page = it })
                }
            }
        }

        val initial = host.frame(FrameInput(360, 220), content)
        assertEquals(SemanticsRole.Button, initial.semantics.byTag("content-btn").role)
        assertEquals("Custom Trigger", initial.semantics.byTag("content-btn").label)
        assertEquals(SemanticsRole.Button, initial.semantics.byTag("group-apply").role)
        assertEquals(SemanticsRole.Button, initial.semantics.byTag("group-locked").role)
        assertEquals(true, initial.semantics.byTag("group-locked").config[SemanticsProperties.Disabled])
        assertEquals(true, initial.semantics.byLabel("Previous").config[SemanticsProperties.Disabled])
        assertNull(initial.semantics.byLabel("Next").config[SemanticsProperties.Disabled])

        pressKey(host, Key.Tab, width = 360, height = 220, content = content)
        pressKey(host, Key.Enter, width = 360, height = 220, content = content)
        assertEquals(1, contentClicks)

        pressKey(host, Key.Tab, width = 360, height = 220, content = content)
        pressKey(host, Key.Space, width = 360, height = 220, content = content)
        assertEquals(1, groupApplyClicks)
        assertEquals(0, groupLockedClicks)

        // Skips disabled group-locked and disabled Previous -> page 1 -> page 2 -> Next.
        pressKey(host, Key.Tab, width = 360, height = 220, content = content)
        pressKey(host, Key.Tab, width = 360, height = 220, content = content)
        pressKey(host, Key.Tab, width = 360, height = 220, content = content)
        pressKey(host, Key.Enter, width = 360, height = 220, content = content)
        assertEquals(2, page, "Next pagination button must activate via keyboard")
    }

    @Test
    fun toggleAndToggleGroupSupportKeyboardActivationAndDisabledExclusion() {
        // State, not plain vars: a host that skips unchanged passes only sees reads it can track.
        var toggleChecked by mutableStateOf(false)
        var disabledToggleChecked by mutableStateOf(false)
        var groupSelection by mutableStateOf(emptySet<String>())
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                Column {
                    toggleChecked = ShadcnToggle("Pin", toggleChecked, Modifier.testTag("toggle-on"))
                    disabledToggleChecked = ShadcnToggle(
                        "Muted",
                        disabledToggleChecked,
                        Modifier.testTag("toggle-off"),
                        enabled = false,
                    )
                    ShadcnToggleGroup(selected = groupSelection, onSelectedChange = { groupSelection = it }) {
                        item("grid", "Grid", tag = "tg-grid")
                        item("list", "List", enabled = false, tag = "tg-list")
                    }
                }
            }
        }

        val initial = host.frame(FrameInput(320, 200), content)
        assertEquals(true, initial.semantics.byTag("toggle-off").config[SemanticsProperties.Disabled])
        assertEquals(true, initial.semantics.byTag("tg-list").config[SemanticsProperties.Disabled])

        pressKey(host, Key.Tab, height = 200, content = content)
        pressKey(host, Key.Space, height = 200, content = content)
        assertTrue(toggleChecked, "Space on focused ShadcnToggle must toggle state once")

        // Next Tab skips disabled toggle-off and lands on tg-grid.
        pressKey(host, Key.Tab, height = 200, content = content)
        pressKey(host, Key.Enter, height = 200, content = content)
        assertEquals(setOf("grid"), groupSelection)

        clickNode(host, initial.semantics.byTag("toggle-off"), height = 200, content = content)
        clickNode(host, initial.semantics.byTag("tg-list"), height = 200, content = content)
        assertFalse(disabledToggleChecked)
        assertEquals(setOf("grid"), groupSelection)
    }

    @Test
    fun enabledInputSupportsPointerCaretSelectionTypingPasteImeAndOnSubmit() {
        val state = TextFieldState()
        var clicks = 0
        var submits = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                ShadcnInput(
                    state = state,
                    modifier = Modifier.testTag("input"),
                    onClick = { clicks++ },
                    onSubmit = { submits++ },
                )
            }
        }

        val initial = host.frame(FrameInput(320, 100), content)
        val inputNode = initial.semantics.byTag("input")
        assertNull(inputNode.config[SemanticsProperties.Disabled])

        clickNode(host, inputNode, height = 100, content = content)
        val focused = host.frame(FrameInput(320, 100), content)
        assertTrue(focused.ownership.isTextInputFocused)
        assertEquals(1, clicks)

        // Typing and paste (multi-line paste normalized to spaces in single-line ShadcnInput).
        host.frame(FrameInput(320, 100, typedText = "hello"), content)
        assertEquals("hello", state.text)
        host.frame(FrameInput(320, 100, typedText = " world\nnext"), content)
        assertEquals("hello world next", state.text)

        // IME pre-edit + commit.
        host.frame(FrameInput(320, 100, imeComposition = ImeComposition("...", 0, 3)), content)
        assertTrue(state.hasComposition)
        assertEquals("hello world next", state.text)
        host.frame(FrameInput(320, 100, imeCommit = "!"), content)
        assertFalse(state.hasComposition)
        assertEquals("hello world next!", state.text)

        // Pointer drag selection + Backspace deletion.
        val startX = inputNode.x + 14
        val endX = inputNode.x + 90
        val midY = inputNode.y + inputNode.height / 2
        host.frame(FrameInput(320, 100, pointerX = endX, pointerY = midY, pointerDown = true), content)
        host.frame(FrameInput(320, 100, pointerX = startX, pointerY = midY, pointerDown = true), content)
        host.frame(FrameInput(320, 100, pointerX = startX, pointerY = midY), content)
        assertTrue(state.hasSelection, "pointer drag across enabled ShadcnInput must select text")
        val beforeDeleteLength = state.text.length
        host.frame(FrameInput(320, 100, editActions = listOf(TextEditAction.Backspace)), content)
        assertTrue(state.text.length < beforeDeleteLength)

        // Submit via Enter fires once without inserting a newline.
        val textBeforeSubmit = state.text
        host.frame(FrameInput(320, 100, editActions = listOf(TextEditAction.Enter)), content)
        assertEquals(1, submits)
        assertEquals(textBeforeSubmit, state.text)
    }

    @Test
    fun disabledInputAndTextareaBlockFocusPointerTypingPasteImeAndSubmit() {
        val inputState = TextFieldState("draft", cursor = 5)
        val notesState = TextFieldState("line1", cursor = 5)
        val inputRequester = FocusRequester()
        val notesRequester = FocusRequester()
        var clicks = 0
        var submits = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                Column {
                    ShadcnInput(
                        state = inputState,
                        modifier = Modifier.testTag("input").focusRequester(inputRequester),
                        enabled = false,
                        onClick = { clicks++ },
                        onSubmit = { submits++ },
                    )
                    ShadcnTextarea(
                        state = notesState,
                        modifier = Modifier.testTag("notes").focusRequester(notesRequester),
                        enabled = false,
                    )
                }
            }
        }

        val disabledFrame = host.frame(FrameInput(320, 200), content)
        val inputNode = disabledFrame.semantics.byTag("input")
        val notesNode = disabledFrame.semantics.byTag("notes")
        assertEquals(true, inputNode.config[SemanticsProperties.Disabled])
        assertEquals(true, notesNode.config[SemanticsProperties.Disabled])

        inputRequester.requestFocus()
        clickNode(host, inputNode, height = 200, content = content)
        host.frame(
            FrameInput(
                viewportWidth = 320,
                viewportHeight = 200,
                pointerX = inputNode.x + 20,
                pointerY = inputNode.y + 18,
                pointerDown = true,
                typedText = "typed-or-pasted",
                imeComposition = ImeComposition("ime", 0, 3),
                imeCommit = "committed",
                editActions = listOf(TextEditAction.Backspace, TextEditAction.Enter),
            ),
            content,
        )
        notesRequester.requestFocus()
        clickNode(host, notesNode, height = 200, content = content)
        host.frame(FrameInput(320, 200, typedText = "extra", editActions = listOf(TextEditAction.Enter)), content)

        assertEquals("draft", inputState.text)
        assertEquals(5, inputState.cursor)
        assertFalse(inputState.hasComposition)
        assertEquals("line1", notesState.text)
        assertEquals(0, clicks)
        assertEquals(0, submits)
        assertFalse(host.frame(FrameInput(320, 200), content).ownership.isTextInputFocused)
    }

    @Test
    fun togglingInputEnabledPreservesCallerBufferAndDropsFocusWhileDisabled() {
        val inputState = TextFieldState("draft", cursor = 5)
        val requester = FocusRequester()
        var enabled = false
        var submits = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                ShadcnInput(
                    state = inputState,
                    modifier = Modifier.testTag("input").focusRequester(requester),
                    enabled = enabled,
                    onSubmit = { submits++ },
                )
            }
        }

        host.frame(FrameInput(320, 100), content)
        assertEquals("draft", inputState.text)

        enabled = true
        val enabledFrame = host.frame(FrameInput(320, 100), content)
        assertNull(enabledFrame.semantics.byTag("input").config[SemanticsProperties.Disabled])
        requester.requestFocus()
        host.frame(FrameInput(320, 100), content)
        inputState.moveCursorTo(inputState.text.length)
        host.frame(FrameInput(320, 100, typedText = "s"), content)
        host.frame(FrameInput(320, 100, editActions = listOf(TextEditAction.Enter)), content)
        assertEquals("drafts", inputState.text)
        assertEquals(1, submits)

        enabled = false
        val disabledAgain = host.frame(
            FrameInput(320, 100, typedText = "nope", editActions = listOf(TextEditAction.Enter)),
            content,
        )
        assertFalse(disabledAgain.ownership.isTextInputFocused)
        assertEquals("drafts", inputState.text)
        assertEquals(1, submits)

        enabled = true
        host.frame(FrameInput(320, 100), content)
        requester.requestFocus()
        host.frame(FrameInput(320, 100), content)
        inputState.moveCursorTo(inputState.text.length)
        host.frame(FrameInput(320, 100, typedText = "!"), content)
        assertEquals("drafts!", inputState.text)
    }

    @Test
    fun inputGroupForwardsDisabledAndOnSubmitContracts() {
        val state = TextFieldState("handle", cursor = 6)
        var enabled = true
        var submits = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                ShadcnInputGroup(
                    state = state,
                    modifier = Modifier.testTag("group"),
                    enabled = enabled,
                    prefix = { ShadcnText("@") },
                    onSubmit = { submits++ },
                )
            }
        }

        val initial = host.frame(FrameInput(320, 100), content)
        val group = initial.semantics.byTag("group")
        assertNull(group.config[SemanticsProperties.Disabled])

        clickNode(host, group, height = 100, content = content)
        state.moveCursorTo(state.text.length)
        host.frame(FrameInput(320, 100, typedText = "01"), content)
        host.frame(FrameInput(320, 100, editActions = listOf(TextEditAction.Enter)), content)
        assertEquals("handle01", state.text)
        assertEquals(1, submits)

        enabled = false
        val disabled = host.frame(
            FrameInput(
                viewportWidth = 320,
                viewportHeight = 100,
                typedText = "blocked",
                editActions = listOf(TextEditAction.Enter),
            ),
            content,
        )
        assertEquals(true, disabled.semantics.byTag("group").config[SemanticsProperties.Disabled])
        assertFalse(disabled.ownership.isTextInputFocused)
        assertEquals("handle01", state.text)
        assertEquals(1, submits)
    }

    @Test
    fun tabAndShiftTabTraverseEnabledFormControlsAndSkipDisabledOnes() {
        val disabledField = TextFieldState("locked")
        val activeField = TextFieldState()
        var submits = 0
        var disabledButtonClicks = 0
        var submitButtonClicks = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                Column {
                    ShadcnInput(disabledField, Modifier.testTag("disabled-input"), enabled = false)
                    ShadcnInput(
                        activeField,
                        Modifier.testTag("active-input"),
                        onSubmit = { submits++ },
                    )
                    ShadcnButton(
                        "Disabled Submit",
                        Modifier.testTag("disabled-btn"),
                        enabled = false,
                        onClick = { disabledButtonClicks++ },
                    )
                    ShadcnButton(
                        "Submit",
                        Modifier.testTag("submit-btn"),
                        onClick = { submitButtonClicks++ },
                    )
                }
            }
        }

        host.frame(FrameInput(320, 240), content)

        // First Tab skips disabled-input and focuses active-input.
        pressKey(host, Key.Tab, content = content)
        val onInput = host.frame(FrameInput(320, 240, typedText = "ready"), content)
        assertTrue(onInput.ownership.isTextInputFocused)
        assertEquals("locked", disabledField.text)
        assertEquals("ready", activeField.text)

        host.frame(FrameInput(320, 240, editActions = listOf(TextEditAction.Enter)), content)
        assertEquals(1, submits)

        // Second Tab skips disabled-btn and focuses submit-btn.
        pressKey(host, Key.Tab, content = content)
        val onButton = host.frame(FrameInput(320, 240), content)
        assertFalse(onButton.ownership.isTextInputFocused)

        pressKey(host, Key.Enter, content = content)
        assertEquals(0, disabledButtonClicks)
        assertEquals(1, submitButtonClicks)

        pressKey(host, Key.Space, content = content)
        assertEquals(2, submitButtonClicks)

        // Shift+Tab skips disabled-btn backwards and returns focus to active-input.
        pressKey(host, Key.Tab, shift = true, content = content)
        val backOnInput = host.frame(FrameInput(320, 240, typedText = "!"), content)
        assertTrue(backOnInput.ownership.isTextInputFocused)
        assertEquals("ready!", activeField.text)
    }
}
