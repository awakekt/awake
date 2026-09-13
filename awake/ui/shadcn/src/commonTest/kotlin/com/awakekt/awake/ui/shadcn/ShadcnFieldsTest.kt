/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.ui.shadcn.components.ShadcnBreadcrumb
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnTextarea
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnFieldsTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    // -- input / textarea -----------------------------------------------------------------------

    @Test
    fun aFieldIsTransparentSoItTakesWhateverItSitsOn() {
        // `bg-transparent`. Reaching for `palette.background` gives a field that punches a hole in a
        // card, which looks right on the page and wrong everywhere else.
        val quads = composeFrame(300, 80) {
            provideShadcnTheme(theme) { ShadcnInput(TextFieldState("hello")) }
        }.primitivesOf<DrawCommand.RoundedQuad>()

        assertTrue(
            quads.none { it.color == theme.palette.background },
            "the field painted an opaque fill instead of bg-transparent",
        )
    }

    @Test
    fun aDarkFieldUsesTheInputTintFromTheReference() {
        val darkTheme = shadcnThemeValues(dark = true)
        val quads = composeFrame(300, 80) {
            provideShadcnTheme(darkTheme) { ShadcnInput(TextFieldState("hello")) }
        }.primitivesOf<DrawCommand.RoundedQuad>()

        assertTrue(
            quads.any { it.color == darkTheme.palette.input.withAlpha(darkTheme.palette.input.a * 0.3f) },
            "dark input is missing the reference dark:bg-input/30 surface",
        )
    }

    @Test
    fun anInputIsThirtySixTall() {
        val quad = composeFrame(300, 80) {
            provideShadcnTheme(theme) { ShadcnInput(TextFieldState("hello")) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(36f, quad.h, 0.5f, "the input is not shadcn's h-9")
        assertEquals(300f, quad.w, 0.5f, "the input is not w-full by default")
    }

    @Test
    fun inputFocusRoutesTypingAndMovesItsCaret() {
        val state = TextFieldState("ac", cursor = 1)
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit =
            { provideShadcnTheme(theme) { ShadcnInput(state) } }
        val idle = FrameInput(200, 100, 5, 5)
        host.frame(idle, content)
        host.frame(FrameInput(200, 100, 5, 5, pointerDown = true), content)
        host.frame(idle, content)
        host.frame(FrameInput(200, 100, 5, 5, typedText = "b"), content)

        assertEquals("abc", state.text)
        assertEquals(2, state.cursor)
    }

    @Test
    fun anEmptyInputPaintsItsPlaceholderWithTheMutedForegroundToken() {
        val glyphs = composeFrame(300, 80) {
            provideShadcnTheme(theme) { ShadcnInput(TextFieldState(), placeholder = "Email") }
        }.primitivesOf<DrawCommand.Glyph>()

        assertTrue(glyphs.isNotEmpty(), "the empty input did not paint its placeholder")
        assertTrue(
            glyphs.all { it.color == theme.palette.mutedForeground },
            "the placeholder did not use text-muted-foreground",
        )
    }

    @Test
    fun aTextareaStartsTallerThanAnInputAndCanGrow() {
        // `min-h-16` with `field-sizing-content`: two lines to start, growing with the content. A
        // fixed height would cap a long note with no scroll.
        fun height(text: String) = composeFrame(300, 200) {
            provideShadcnTheme(theme) { ShadcnTextarea(TextFieldState(text)) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first().h

        assertTrue(height("one line") >= 64f - 0.5f, "the textarea is under its min-h-16")
        assertTrue(height("one line") <= height("a\nb\nc\nd\ne\nf"), "the textarea cannot grow")
    }

    @Test
    fun bothFieldsShareTheSameBorderColour() {
        // They share one style; this is what catches the two drifting apart if someone edits one.
        fun border(input: Boolean) = composeFrame(300, 200) {
            provideShadcnTheme(theme) {
                if (input) ShadcnInput(TextFieldState("x")) else ShadcnTextarea(TextFieldState("x"))
            }
        }.meshColors()

        assertTrue(
            border(true).any { it == theme.palette.input },
            "the input has no border-input edge",
        )
        assertTrue(border(false).any { it == theme.palette.input }, "the textarea's border drifted")
    }

    @Test
    fun fieldsInsideAFullWidthCardKeepTheirAvailableWidth() {
        val quads = composeFrame(300, 180) {
            provideShadcnTheme(theme) {
                ShadcnCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        ShadcnInput(TextFieldState("name"))
                        ShadcnTextarea(TextFieldState("bio"))
                    }
                }
            }
        }.primitivesOf<DrawCommand.RoundedQuad>()

        // 300px card with contentPadding = 0.dp, minus only the 1px border on each side:
        // fields get full available width (296px+).
        assertTrue(quads.any { it.w >= 296f && it.h == 36f }, "the input collapsed inside the card")
        assertTrue(
            quads.any { it.w >= 296f && it.h >= 64f },
            "the textarea collapsed inside the card",
        )
    }

    // -- tabs -----------------------------------------------------------------------------------

    private fun tabs(selected: Int) = composeFrame(300, 80) {
        provideShadcnTheme(theme) {
            ShadcnTabs(selectedValue = selected.toString(), onSelectedChange = {}) {
                tab(value = "0", label = "Account")
                tab(value = "1", label = "Password")
            }
        }
    }

    @Test
    fun theTabListIsMutedAndTheActiveTriggerIsNot() {
        val quads = tabs(0).primitivesOf<DrawCommand.RoundedQuad>()

        assertEquals(theme.palette.muted, quads.first().color, "the tab list is not bg-muted")
        assertTrue(
            quads.any { it.color == theme.palette.background },
            "the active trigger drew no fill to separate it",
        )
    }

    @Test
    fun anInactiveTriggerIsSixtyPercentForegroundNotTheMutedToken() {
        // `text-foreground/60`. Sixty percent of the foreground is a different colour from
        // muted-foreground, and it is what makes an inactive tab read as dimmed rather than as a
        // secondary label.
        val glyphs = tabs(0).primitivesOf<DrawCommand.Glyph>()
        val inactive = glyphs.first { it.color.a < 1f }.color

        assertEquals(0.6f, inactive.a, 0.05f, "an inactive trigger is not at 60%")
        assertTrue(inactive != theme.palette.mutedForeground, "it took the muted token instead")
    }

    @Test
    fun tabTriggersUseTheSourceTwentyNinePixelTrackHeight() {
        val active = tabs(0).primitivesOf<DrawCommand.RoundedQuad>()
            .first { it.color == theme.palette.background }

        assertEquals(29f, active.h, 0.5f, "a tab trigger must be h-[calc(100%-1px)] inside h-9")
    }

    @Test
    fun theActiveTriggerFollowsTheSelection() {
        fun activeX(selected: Int) = tabs(selected).primitivesOf<DrawCommand.RoundedQuad>()
            .first { it.color == theme.palette.background }.x

        assertTrue(activeX(1) > activeX(0), "the active fill did not move with the selection")
    }

    @Test
    fun tabsDoNotCrashWhenReplacingBranchWithButtons() {
        val host = ComposeHost()
        var showTabs = false
        val frame1 = host.frame(FrameInput(400, 100)) {
            provideShadcnTheme(theme) {
                if (!showTabs) {
                    Row {
                        ShadcnButton("Action 1")
                        ShadcnButton("Action 2")
                    }
                } else {
                    ShadcnTabs(selectedValue = "0", onSelectedChange = {}) {
                        tab(value = "0", label = "Account")
                        tab(value = "1", label = "Password")
                        tab(value = "2", label = "Advanced")
                    }
                }
            }
        }
        assertTrue(frame1.primitives.isNotEmpty())

        showTabs = true
        val frame2 = host.frame(FrameInput(400, 100)) {
            provideShadcnTheme(theme) {
                if (!showTabs) {
                    Row {
                        ShadcnButton("Action 1")
                        ShadcnButton("Action 2")
                    }
                } else {
                    ShadcnTabs(selectedValue = "0", onSelectedChange = {}) {
                        tab(value = "0", label = "Account")
                        tab(value = "1", label = "Password")
                        tab(value = "2", label = "Advanced")
                    }
                }
            }
        }
        assertTrue(frame2.primitives.isNotEmpty())
    }

    // -- breadcrumb -----------------------------------------------------------------------------

    @Test
    fun theLastCrumbIsNotMuted() {
        // Upstream marks it aria-current="page" and styles it `text-foreground`. A uniformly muted
        // trail is the obvious wrong version and reads as all-disabled.
        val glyphs = composeFrame(400, 60) {
            provideShadcnTheme(theme) { ShadcnBreadcrumb(listOf("Home", "Docs", "Components")) }
        }.primitivesOf<DrawCommand.Glyph>()

        assertEquals(theme.palette.foreground, glyphs.last().color, "the current page is muted")
        assertEquals(
            theme.palette.mutedForeground,
            glyphs.first().color,
            "an ancestor is not muted",
        )
    }

    @Test
    fun aSingleCrumbIsTheCurrentPage() {
        val glyphs = composeFrame(400, 60) {
            provideShadcnTheme(theme) { ShadcnBreadcrumb(listOf("Home")) }
        }.primitivesOf<DrawCommand.Glyph>()

        assertTrue(glyphs.all { it.color == theme.palette.foreground }, "a lone crumb was muted")
    }
}
