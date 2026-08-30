/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.text.BasicTextField
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.core.input.ImeComposition
import io.github.awakelab.awake.compose.ui.platform.blocksGameplayKeys
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.input.TextEditAction
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val idle = FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 5, pointerY = 5)

private fun typing(text: String = "", vararg commands: TextEditAction) = FrameInput(
    viewportWidth = 200,
    viewportHeight = 200,
    pointerX = 5,
    pointerY = 5,
    typedText = text,
    editActions = commands.toList(),
)

/** Editing arithmetic, independent of any tree. */
class TextFieldStateTest {

    @Test
    fun selectedTextIsReplacedAndDeletionCollapsesTheRange() {
        val state = TextFieldState("hello", cursor = 5)

        state.select(anchor = 1, focus = 4)
        assertEquals("ell", state.selectedText)
        state.insert("i")
        assertEquals("hio", state.text)
        assertEquals(2, state.cursor)
        assertFalse(state.hasSelection)

        state.select(anchor = 1, focus = 2)
        state.apply(TextEditAction.Backspace)
        assertEquals("ho", state.text)
        assertEquals(1, state.cursor)
        assertFalse(state.hasSelection)
    }

    @Test
    fun horizontalNavigationCollapsesSelectionTowardItsMotion() {
        val state = TextFieldState("hello", cursor = 4)
        state.select(anchor = 1, focus = 4)

        state.apply(TextEditAction.ArrowLeft)
        assertEquals(1, state.cursor)
        assertFalse(state.hasSelection)

        state.select(anchor = 4, focus = 1)
        state.apply(TextEditAction.ArrowRight)
        assertEquals(4, state.cursor)
        assertFalse(state.hasSelection)
    }

    @Test
    fun selectAllCoversTheCurrentValue() {
        val state = TextFieldState("hello", cursor = 2)
        state.selectAll()
        assertEquals("hello", state.selectedText)
        assertEquals(0, state.selectionStart)
        assertEquals(5, state.selectionEnd)
    }

    @Test
    fun imeCompositionIsVisibleWithoutChangingTheCommittedValue() {
        val state = TextFieldState("hello", cursor = 4)
        state.select(anchor = 1, focus = 4)

        state.setComposition(ImeComposition("i"))

        assertEquals("hello", state.text)
        assertEquals("hio", state.displayedText)
        assertEquals(2, state.displayedCursor)

        state.commitComposition("i")
        assertEquals("hio", state.text)
        assertFalse(state.hasComposition)
    }

    @Test
    fun insertingGoesAtTheCaretAndMovesIt() {
        val state = TextFieldState("ac", cursor = 1)

        state.insert("b")

        assertEquals("abc", state.text)
        assertEquals(2, state.cursor)
    }

    @Test
    fun backspaceDeletesBeforeTheCaret() {
        val state = TextFieldState("abc", cursor = 2)

        state.apply(TextEditAction.Backspace)

        assertEquals("ac", state.text)
        assertEquals(1, state.cursor)
    }

    @Test
    fun deleteRemovesAfterTheCaretAndLeavesItPut() {
        // The whole difference between the two keys, and the one people get backwards.
        val state = TextFieldState("abc", cursor = 1)

        state.apply(TextEditAction.Delete)

        assertEquals("ac", state.text)
        assertEquals(1, state.cursor)
    }

    @Test
    fun editingAtTheEdgesIsANoOpNotAnError() {
        // Holding backspace at the start of a field is normal, not exceptional.
        val start = TextFieldState("ab", cursor = 0)
        start.apply(TextEditAction.Backspace)
        start.apply(TextEditAction.ArrowLeft)
        assertEquals("ab", start.text)
        assertEquals(0, start.cursor)

        val end = TextFieldState("ab", cursor = 2)
        end.apply(TextEditAction.Delete)
        end.apply(TextEditAction.ArrowRight)
        assertEquals("ab", end.text)
        assertEquals(2, end.cursor)
    }

    @Test
    fun homeAndEndJumpToTheEdges() {
        val state = TextFieldState("abcd", cursor = 2)

        state.apply(TextEditAction.Home)
        assertEquals(0, state.cursor)
        state.apply(TextEditAction.End)
        assertEquals(4, state.cursor)
    }

    @Test
    fun homeAndEndStayWithinTheCurrentMultilineLine() {
        val state = TextFieldState("first\nsecond", cursor = 9)

        state.apply(TextEditAction.Home)
        assertEquals(6, state.cursor)
        state.apply(TextEditAction.End)
        assertEquals(12, state.cursor)
    }

    @Test
    fun homeAndEndCollapseAnExistingSelection() {
        val state = TextFieldState("first\nsecond", cursor = 10)
        state.select(anchor = 7, focus = 10)

        state.apply(TextEditAction.Home)
        assertEquals(6, state.cursor)
        assertFalse(state.hasSelection)

        state.select(anchor = 7, focus = 10)
        state.apply(TextEditAction.End)
        assertEquals(12, state.cursor)
        assertFalse(state.hasSelection)
    }

    @Test
    fun verticalMotionClampsToTheTargetLineAndPreservesPreferredColumn() {
        val state = TextFieldState("12345\nxy\n123456", cursor = 4)

        state.apply(TextEditAction.ArrowDown)
        assertEquals(8, state.cursor)
        state.apply(TextEditAction.ArrowDown)
        assertEquals(13, state.cursor)
        state.apply(TextEditAction.ArrowUp)
        assertEquals(8, state.cursor)
    }

    @Test
    fun theCaretCannotLeaveTheText() {
        val state = TextFieldState("ab", cursor = 99)

        assertEquals(2, state.cursor)
        state.setText("x")
        assertEquals(1, state.cursor)
    }
}

/**
 * Typing through the real frame loop.
 *
 * Routed by focus, not by pointer position: a keyboard has no coordinates, and the point of a caret
 * is that typing goes where it is rather than where the mouse is.
 */
class TextFieldThroughFrameTest {

    private fun hostWith(state: TextFieldState): Pair<
        ComposeHost,
        context(Composer)
        () -> Unit,
        > {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(state, Modifier.size(100.dp, 20.dp))
        }
        return host to content
    }

    private fun focused(state: TextFieldState): Pair<
        ComposeHost,
        context(Composer)
        () -> Unit,
        > {
        val (host, content) = hostWith(state)
        host.frame(idle, content)
        host.frame(FrameInput(200, 200, 5, 5, pointerDown = true), content)
        host.frame(idle, content)
        return host to content
    }

    @Test
    fun typingReachesAFocusedField() {
        val state = TextFieldState()
        val (host, content) = focused(state)

        host.frame(typing("hi"), content)

        assertEquals("hi", state.text)
    }

    @Test
    fun typingIsIgnoredWhileNothingIsFocused() {
        // A stray keystroke must not land in a field the user never clicked into.
        val state = TextFieldState()
        val (host, content) = hostWith(state)
        host.frame(idle, content)

        host.frame(typing("hi"), content)

        assertEquals("", state.text)
    }

    @Test
    fun editActionsReachTheFieldToo() {
        val state = TextFieldState("abc")
        val (host, content) = focused(state)
        // Focus was obtained by a real pointer press, which also places the caret. Restore the
        // explicit editing position this test is about rather than relying on the old no-op click.
        state.moveCursorTo(state.text.length)

        host.frame(typing(commands = arrayOf(TextEditAction.Backspace)), content)

        assertEquals("ab", state.text)
    }

    @Test
    fun theFrameReportsThatTextHasFocus() {
        // The bug `ui-core` shipped: gating gameplay on pointer capture alone let W/A/S/D both
        // insert the letters and walk the player.
        val state = TextFieldState()
        val (host, content) = focused(state)

        val output = host.frame(idle, content)

        assertTrue(output.ownership.isTextInputFocused)
        assertTrue(output.ownership.blocksGameplayKeys)
        assertTrue(output.effects.requestKeyboard, "the platform was never asked for a keyboard")
    }

    @Test
    fun caretIsOnlyPaintedWhileTheFieldIsFocusedAndInItsVisibleBlinkPhase() {
        val state = TextFieldState()
        val (host, content) = hostWith(state)

        val unfocused = host.frame(idle, content)
        assertTrue(unfocused.primitives.none(::isCaret), "an unfocused field painted a static caret")

        host.frame(FrameInput(200, 200, 5, 5, pointerDown = true), content)
        val focused = host.frame(FrameInput(200, 200, 5, 5, deltaSeconds = 0.1f), content)
        assertTrue(focused.primitives.any(::isCaret), "a focused field did not paint its caret")

        repeat(4) { host.frame(FrameInput(200, 200, 5, 5, deltaSeconds = 0.1f), content) }
        val blinkOff = host.frame(FrameInput(200, 200, 5, 5, deltaSeconds = 0.1f), content)
        assertTrue(blinkOff.primitives.none(::isCaret), "the caret never entered its hidden blink phase")
    }

    @Test
    fun clickingAwayReleasesTheKeyboardAndTheKeys() {
        val state = TextFieldState()
        val host = ComposeHost()
        // Wrapped in a Column: the host root stacks its children at the origin, so two siblings
        // declared bare would overlap and the click would land on whichever painted last.
        val content: context(Composer)
        () -> Unit = {
            Column {
                BasicTextField(state, Modifier.size(50.dp, 20.dp))
                Spacer(Modifier.size(50.dp, 50.dp))
            }
        }
        host.frame(idle, content)
        host.frame(FrameInput(200, 200, 5, 5, pointerDown = true), content)
        assertTrue(host.frame(idle, content).ownership.isTextInputFocused)

        host.frame(FrameInput(200, 200, 5, 60, pointerDown = true), content)
        val output = host.frame(FrameInput(200, 200, 5, 60), content)

        assertFalse(output.ownership.isTextInputFocused)
        assertFalse(output.effects.requestKeyboard)
        host.frame(typing("x"), content)
        assertEquals("", state.text, "typing kept landing after the field lost focus")
    }

    @Test
    fun theFieldKeepsItsTextAcrossFrames() {
        // The state lives in a remembered object rather than in the link, which is rebuilt every
        // pass -- keeping it in the link would drop every keystroke one frame later.
        val state = TextFieldState()
        val (host, content) = focused(state)

        host.frame(typing("a"), content)
        host.frame(idle, content)
        host.frame(typing("b"), content)

        assertEquals("ab", state.text)
    }

    @Test
    fun imePreeditAndCommitReachTheFocusedFieldThroughFrames() {
        val state = TextFieldState("ab", cursor = 1)
        val (host, content) = focused(state)
        state.moveCursorTo(1)

        host.frame(
            FrameInput(200, 200, 5, 5, imeComposition = ImeComposition("x")),
            content,
        )
        assertEquals("ab", state.text)
        assertEquals("axb", state.displayedText)

        host.frame(FrameInput(200, 200, 5, 5, imeCommit = "x"), content)
        assertEquals("axb", state.text)
    }

    @Test
    fun draggingSelectsTextAndPaintingIncludesTheSelectedRange() {
        val state = TextFieldState("hello")
        val (host, content) = hostWith(state)
        host.frame(idle, content)
        host.frame(FrameInput(200, 200, 1, 5, pointerDown = true), content)
        val frame = host.frame(FrameInput(200, 200, 20, 5, pointerDown = true), content)

        assertTrue(state.hasSelection)
        assertTrue(state.selectedText.isNotEmpty())
        assertTrue(
            frame.primitives.filterIsInstance<DrawCommand.Quad>().any { it.w > 1f && it.h > 1f },
            "the selected range was not painted",
        )
    }
}

class TextFieldAlignmentTest {

    @Test
    fun singleLineTextIsCenteredWhileMultilineTextStartsAtTheTop() {
        fun inkTop(singleLine: Boolean): Float {
            val host = ComposeHost()
            val frame = host.frame(FrameInput(120, 80)) {
                BasicTextField(
                    TextFieldState("Ag"),
                    Modifier.size(120.dp, 40.dp),
                    style = TextStyle(lineHeight = 20f.sp),
                    singleLine = singleLine,
                )
            }
            return frame.primitives.filterIsInstance<DrawCommand.Glyph>().minOf { it.y }
        }

        val topAligned = inkTop(singleLine = false)
        val centered = inkTop(singleLine = true)
        assertEquals(10f, centered - topAligned, 0.01f, "single-line text did not center its line box")
    }

    @Test
    fun singleLineCaretUsesTheAuthoredLineBox() {
        val state = TextFieldState()
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(
                state,
                Modifier.size(120.dp, 40.dp),
                style = TextStyle(lineHeight = 20f.sp),
                singleLine = true,
            )
        }
        host.frame(FrameInput(200, 80, 5, 5), content)
        host.frame(FrameInput(200, 80, 5, 5, pointerDown = true), content)
        val focused = host.frame(FrameInput(200, 80, 5, 5, deltaSeconds = 0.1f), content)
        assertTrue(
            focused.primitives.any { it is DrawCommand.Quad && it.w == 1f && it.h == 20f },
            "single-line caret did not use the authored 20px line box",
        )
    }

    @Test
    fun singleLineTypingNormalizesNewlinesLikeCompose() {
        val state = TextFieldState()
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(state, Modifier.size(120.dp, 40.dp), singleLine = true)
        }
        host.frame(FrameInput(200, 80, 5, 5), content)
        host.frame(FrameInput(200, 80, 5, 5, pointerDown = true), content)
        host.frame(FrameInput(200, 80, 5, 5, typedText = "a\nb"), content)
        assertEquals("a b", state.text)
    }

    @Test
    fun multilineTypingPreservesEnterAsANewline() {
        val state = TextFieldState("a")
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(state, Modifier.size(120.dp, 60.dp))
        }
        host.frame(FrameInput(200, 100, 5, 5), content)
        host.frame(FrameInput(200, 100, 5, 5, pointerDown = true), content)
        host.frame(FrameInput(200, 100, 5, 5, typedText = "\n"), content)

        assertEquals("a\n", state.text)
    }

    @Test
    fun multilineEditActionEnterInsertsANewline() {
        val state = TextFieldState("a", cursor = 1)
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(state, Modifier.size(120.dp, 60.dp))
        }
        host.frame(FrameInput(200, 100, 5, 5), content)
        host.frame(FrameInput(200, 100, 5, 5, pointerDown = true), content)
        host.frame(FrameInput(200, 100, 5, 5, editActions = listOf(TextEditAction.Enter)), content)

        assertEquals("a\n", state.text)
    }

    @Test
    fun multilineCaretUsesTheCurrentLineBoxInsteadOfTheViewportHeight() {
        val state = TextFieldState("a\nb", cursor = 2)
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(
                state,
                Modifier.size(120.dp, 60.dp),
                style = TextStyle(lineHeight = 20f.sp),
            )
        }
        host.frame(FrameInput(200, 100, 5, 25), content)
        host.frame(FrameInput(200, 100, 5, 25, pointerDown = true), content)
        val focused = host.frame(FrameInput(200, 100, 5, 25, deltaSeconds = 0.1f), content)
        val caret = focused.primitives.filterIsInstance<DrawCommand.Quad>().first { it.w == 1f }

        assertEquals(20f, caret.h, 0.01f)
        assertEquals(20f, caret.y, 0.01f)
    }

    @Test
    fun softWrappedMultilineCaretUsesTheVisualLineBox() {
        val state = TextFieldState("one two", cursor = 7)
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(
                state,
                Modifier.size(24.dp, 60.dp),
                style = TextStyle(lineHeight = 20f.sp),
            )
        }
        host.frame(FrameInput(200, 100, 5, 5), content)
        host.frame(FrameInput(200, 100, 5, 5, pointerDown = true), content)
        state.moveCursorTo(state.text.length)
        val focused = host.frame(FrameInput(200, 100, 5, 5, deltaSeconds = 0.1f), content)
        val caret = focused.primitives.filterIsInstance<DrawCommand.Quad>().first { it.w == 1f }

        assertEquals(20f, caret.h, 0.01f)
        assertEquals(20f, caret.y, 0.01f)
    }

    @Test
    fun singleLineTextDoesNotWrapAndKeepsTheEndCaretInsideTheViewport() {
        val state = TextFieldState("abcdefghijklmnopqrstuvwxyz")
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(
                state,
                Modifier.size(60.dp, 40.dp),
                style = TextStyle(lineHeight = 20f.sp),
                singleLine = true,
            )
        }

        host.frame(FrameInput(200, 80, 5, 5), content)
        host.frame(FrameInput(200, 80, 5, 5, pointerDown = true), content)
        val focused = host.frame(FrameInput(200, 80, 5, 5, deltaSeconds = 0.1f), content)

        val glyphs = focused.primitives.filterIsInstance<DrawCommand.Glyph>()
        assertTrue(glyphs.isNotEmpty())
        assertTrue(
            glyphs.maxOf { it.y } - glyphs.minOf { it.y } < 20f,
            "single-line text wrapped onto multiple rows",
        )
        val caret = focused.primitives.filterIsInstance<DrawCommand.Quad>().first { it.w == 1f && it.h == 20f }
        assertTrue(caret.x >= 0f && caret.x <= 60f, "the end caret left the single-line viewport")
    }
}

private fun isCaret(primitive: Any): Boolean =
    primitive is DrawCommand.Quad && primitive.w == 1f && primitive.h > 0f

/**
 * The three actions the field gained by dropping its own copy of the edit vocabulary.
 *
 * `EditCommand` had six entries and no host spoke it. `TextEditAction` has nine and four host
 * bridges already map it -- GLFW, Android, iOS and the wasm canvas. Enter, ArrowUp and ArrowDown
 * arrive now where before they had no name at all.
 */
class TextFieldGainedActionsTest {

    @Test
    fun aSingleLineFieldIgnoresEnterWithoutLosingItsText() {
        val state = TextFieldState("hello", cursor = 5)

        state.apply(TextEditAction.Enter)

        assertEquals("hello", state.text, "Enter inserted something into a single-line field")
        assertEquals(5, state.cursor)
    }

    @Test
    fun verticalMotionIsANoOpUntilThereIsMoreThanOneLine() {
        val state = TextFieldState("hello", cursor = 2)

        state.apply(TextEditAction.ArrowUp)
        state.apply(TextEditAction.ArrowDown)

        assertEquals(2, state.cursor, "the caret moved on a field with one line")
    }
}
