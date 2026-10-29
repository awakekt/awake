/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.input.TextEditAction
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.measureTextWidth
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.WordBreak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CjkTextAndEditingTest {

    private val font = UiFonts.trueSans()

    @Test
    fun ellipsisAndMaxLinesWorkForChineseJapaneseAndKorean() {
        val glyphPx = 16f
        val samples = listOf(
            "港口小镇的灯火缓缓亮起，石桥、仓库与木船在水面映出暖光。「今晚风浪平稳，按计划启航！」" to WordBreak.Normal,
            "港町の桟橋に朝霧が漂い、灯台の光が静かに揺れている。「北埠頭の準備は完了しましたか？」" to WordBreak.Normal,
            "항구 마을의 등불이 잔잔한 바다 위에 비치고, 어부들은 새벽 출항을 차분히 준비합니다." to WordBreak.Normal,
            "항구 마을의 등불이 잔잔한 바다 위에 비치고, 어부들은 새벽 출항을 차분히 준비합니다." to WordBreak.KeepAll,
            "Harbor港口Town的第12号Pier开放，加载lantern_quay.glb。" to WordBreak.Normal,
        )
        for ((paragraph, mode) in samples) {
            val style = TextStyle(font = font, size = 16.sp, wordBreak = mode)
            val fullRun = TextRun(paragraph, style, font, glyphPx, density = 1f, wrapWidthPx = 96f)
            assertTrue(fullRun.lines.size >= 4, "expected at least 4 wrapped lines for '$paragraph' ($mode)")

            for (maxLines in listOf(1, 2, 3)) {
                val capped = TextRun(
                    text = paragraph,
                    style = style,
                    font = font,
                    glyphPx = glyphPx,
                    density = 1f,
                    wrapWidthPx = 96f,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = maxLines,
                )
                assertEquals(maxLines, capped.lines.size, "maxLines=$maxLines for '$paragraph' ($mode)")
                assertTrue(capped.lines.last().endsWith("\u2026"), "last line should end with ellipsis: ${capped.lines.last()}")
                for (line in capped.lines) {
                    assertTrue(
                        font.measureTextWidth(line, glyphPx) <= 96.5f,
                        "ellipsized line '$line' exceeded box width",
                    )
                }
            }
        }
    }

    @Test
    fun emergencyUrlWrapAndEllipsisNeverSplitGraphemeClusters() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val combining = "e\u0301"
        val url = "https://awake.example/harbor_${combining}_${family}_lantern_quay_manifest.json"
        val glyphPx = 16f
        val style = TextStyle(font = font, size = 16.sp)

        val wrapped = TextRun(url, style, font, glyphPx, density = 1f, wrapWidthPx = 80f)
        assertTrue(wrapped.lines.size >= 3)
        var offset = 0
        for (line in wrapped.lines) {
            assertTrue(GraphemeClusters.isBoundary(url, offset), "line start $offset split a cluster")
            offset += line.length
            assertTrue(GraphemeClusters.isBoundary(url, offset), "line end $offset split a cluster")
        }
        assertEquals(url.length, offset)

        val ellipsized = TextRun(
            text = url,
            style = style,
            font = font,
            glyphPx = glyphPx,
            density = 1f,
            wrapWidthPx = 80f,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
        assertEquals(2, ellipsized.lines.size)
        assertTrue(ellipsized.lines.last().endsWith("\u2026"))
        val kept = ellipsized.lines[0] + ellipsized.lines[1].removeSuffix("\u2026")
        assertTrue(url.startsWith(kept))
        assertTrue(GraphemeClusters.isBoundary(url, kept.length), "ellipsis cut inside a grapheme cluster")
    }

    @Test
    fun minIntrinsicWidthReflectsCjkOpportunitiesAndKeepAllWords() {
        val chinese = LayoutNode(
            TextMeasurePolicy("港口小镇", TextStyle(size = 16.sp), font),
            density = 1f,
        )
        val singleIdeograph = LayoutNode(
            TextMeasurePolicy("港", TextStyle(size = 16.sp), font),
            density = 1f,
        )
        assertEquals(
            singleIdeograph.maxIntrinsicWidth(Constraints.Infinity),
            chinese.minIntrinsicWidth(Constraints.Infinity),
            "Chinese paragraph minIntrinsicWidth should equal one ideograph",
        )

        val kinsoku = LayoutNode(
            TextMeasurePolicy("港。文", TextStyle(size = 16.sp), font),
            density = 1f,
        )
        val gluedPair = LayoutNode(
            TextMeasurePolicy("港。", TextStyle(size = 16.sp), font),
            density = 1f,
        )
        assertEquals(
            gluedPair.maxIntrinsicWidth(Constraints.Infinity),
            kinsoku.minIntrinsicWidth(Constraints.Infinity),
            "Kinsoku non-starter keeps '港。' together in minIntrinsicWidth",
        )

        val koreanNormal = LayoutNode(
            TextMeasurePolicy("항구 마을입니다", TextStyle(size = 16.sp, wordBreak = WordBreak.Normal), font),
            density = 1f,
        )
        val koreanKeepAll = LayoutNode(
            TextMeasurePolicy("항구 마을입니다", TextStyle(size = 16.sp, wordBreak = WordBreak.KeepAll), font),
            density = 1f,
        )
        assertTrue(
            koreanKeepAll.minIntrinsicWidth(Constraints.Infinity) > koreanNormal.minIntrinsicWidth(Constraints.Infinity),
            "WordBreak.KeepAll minIntrinsicWidth should hold the widest Korean word whole",
        )
    }

    @Test
    fun basicTextFieldWordShortcutsAndDoubleClickSelectAcrossMixedScripts() {
        val sample = "Harbor港口 항구 コーヒー"
        val state = TextFieldState(sample, cursor = 0)
        val host = ComposeHost()
        val style = TextStyle(font = font, size = 16.sp, lineHeight = 20.sp)
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(state, Modifier.size(320.dp, 28.dp), style = style, singleLine = true)
        }

        // Focus near x=2 (start of "Harbor").
        host.frame(FrameInput(340, 40, 2, 10), content)
        host.frame(FrameInput(340, 40, 2, 10, pointerDown = true), content)
        host.frame(FrameInput(340, 40, 2, 10), content)
        state.moveCursorTo(0)

        // Step forward word by word with TextEditAction.WordRight:
        // "Harbor" (6) -> "港" (7) -> "口" (8) -> "항구" (11) -> "コーヒー" (16).
        val forward = mutableListOf<Int>()
        repeat(5) {
            host.frame(FrameInput(340, 40, 2, 10, editActions = listOf(TextEditAction.WordRight)), content)
            forward += state.cursor
        }
        assertEquals(listOf(6, 7, 8, 11, 16), forward)

        // Step backward word by word with TextEditAction.WordLeft:
        // "コーヒー" start (12) -> "항구" start (9) -> "口" (7) -> "港" (6) -> "Harbor" (0).
        val backward = mutableListOf<Int>()
        repeat(5) {
            host.frame(FrameInput(340, 40, 2, 10, editActions = listOf(TextEditAction.WordLeft)), content)
            backward += state.cursor
        }
        assertEquals(listOf(12, 9, 7, 6, 0), backward)

        // Wait past double-click window, then double-click inside "항구" and drag into "コーヒー".
        val run = TextRun(sample, style, font, glyphPx = 16f, density = 1f, singleLine = true)
        val hangulMidX = ((run.offsetAt(9) + run.offsetAt(11)) / 2f).toInt()
        val katakanaMidX = ((run.offsetAt(12) + run.offsetAt(16)) / 2f).toInt()

        repeat(6) { host.frame(FrameInput(340, 40, hangulMidX, 10, deltaSeconds = 0.1f), content) }
        // First click:
        host.frame(FrameInput(340, 40, hangulMidX, 10, pointerDown = true, deltaSeconds = 0.02f), content)
        host.frame(FrameInput(340, 40, hangulMidX, 10, pointerDown = false, deltaSeconds = 0.02f), content)
        // Second click within double-click window selects "항구":
        host.frame(FrameInput(340, 40, hangulMidX, 10, pointerDown = true, deltaSeconds = 0.05f), content)
        assertEquals("항구", state.selectedText)

        // Dragging while holding the second click extends selection to the end of "コーヒー":
        host.frame(FrameInput(340, 40, katakanaMidX, 10, pointerDown = true, deltaSeconds = 0.02f), content)
        assertEquals("항구 コーヒー", state.selectedText)
        host.frame(FrameInput(340, 40, katakanaMidX, 10, pointerDown = false, deltaSeconds = 0.02f), content)
    }

    @Test
    fun maskedPasswordFieldNeverLeaksWordBoundariesOnWordJumpOrDoubleClick() {
        val secret = "harbor secret 42"
        val state = TextFieldState(secret, cursor = 8)
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(
                state,
                Modifier.size(240.dp, 24.dp),
                style = TextStyle(font = font, size = 16.sp, lineHeight = 20.sp),
                singleLine = true,
                mask = '*',
            )
        }

        // Focus field and place caret in the middle of the secret.
        host.frame(FrameInput(260, 40, 10, 10), content)
        host.frame(FrameInput(260, 40, 10, 10, pointerDown = true), content)
        host.frame(FrameInput(260, 40, 10, 10), content)
        state.moveCursorTo(8)

        // WordLeft / WordRight jump to the start / end of the whole masked field rather than stopping at spaces.
        host.frame(FrameInput(260, 40, 10, 10, editActions = listOf(TextEditAction.WordLeft)), content)
        assertEquals(0, state.cursor)
        assertFalse(state.hasSelection)

        state.moveCursorTo(4)
        host.frame(FrameInput(260, 40, 10, 10, editActions = listOf(TextEditAction.WordRight)), content)
        assertEquals(secret.length, state.cursor)

        // Double-click selects the entire masked value rather than a single hidden word.
        repeat(6) { host.frame(FrameInput(260, 40, 12, 10, deltaSeconds = 0.1f), content) }
        host.frame(FrameInput(260, 40, 12, 10, pointerDown = true, deltaSeconds = 0.02f), content)
        host.frame(FrameInput(260, 40, 12, 10, pointerDown = false, deltaSeconds = 0.02f), content)
        host.frame(FrameInput(260, 40, 12, 10, pointerDown = true, deltaSeconds = 0.05f), content)
        assertEquals(secret, state.selectedText)
    }

    @Test
    fun caretMoveBetweenRapidClicksDoesNotTriggerDoubleClickSelection() {
        val state = TextFieldState("hello world", cursor = 0)
        val host = ComposeHost()
        val style = TextStyle(font = font, size = 16.sp, lineHeight = 20.sp)
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(state, Modifier.size(240.dp, 24.dp), style = style, singleLine = true)
        }

        host.frame(FrameInput(260, 40, 28, 10), content)
        host.frame(FrameInput(260, 40, 28, 10, pointerDown = true, deltaSeconds = 0.02f), content)
        val firstCaret = state.cursor
        assertTrue(firstCaret in 1..4)
        host.frame(FrameInput(260, 40, 28, 10, pointerDown = false, deltaSeconds = 0.02f), content)

        // Moving the caret between two rapid clicks cancels the double-click sequence.
        state.moveCursorTo(0)
        host.frame(FrameInput(260, 40, 28, 10, pointerDown = true, deltaSeconds = 0.02f), content)
        assertFalse(state.hasSelection)
        assertEquals(firstCaret, state.cursor)
    }
}
