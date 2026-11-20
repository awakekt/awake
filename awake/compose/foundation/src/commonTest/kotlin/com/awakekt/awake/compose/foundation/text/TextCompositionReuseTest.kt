/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * [Text] hands back the last composition's style, policy and modifier chain while its inputs are equal (#1813), and
 * builds new ones the frame any input changes: each input below is changed between two frames, and what is drawn moves.
 * A reuse keyed on less than every input would draw the last frame's text, silently.
 */
class TextCompositionReuseTest {
    private var host = ComposeHost()
    private var text = "Harbor Town"
    private var style = TextStyle(size = 12.sp)
    private var ambient = TextStyle(color = Color(1f, 1f, 1f, 1f))
    private var font: UiFont = UiFonts.default()
    private var overflow = TextOverflow.Clip
    private var softWrap = true
    private var maxLines = Int.MAX_VALUE
    private var modifier: Modifier = Modifier

    private fun frame(): List<UiDrawPrimitive.Glyph> = host.frame(FrameInput(WIDTH, HEIGHT)) {
        CompositionLocalProvider(LocalTextStyle provides ambient, LocalFont provides font) {
            Text(text, modifier, style, overflow, softWrap, maxLines)
        }
    }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    private fun policy() = host.root.children[0].measurePolicy

    @Test
    fun anUnchangedCompositionKeepsItsPolicyAndChain() {
        val glyphs = frame()
        val policy = policy()
        val chain = host.root.children[0].modifier
        style = style.copy() // equal, not the same instance
        ambient = ambient.copy()

        assertEquals(glyphs, frame())
        assertSame(policy, policy())
        assertSame(chain, host.root.children[0].modifier)
    }

    @Test
    fun eachInputThatChangesWhatIsDrawnRebuildsIt() {
        val changes: List<Pair<String, () -> Unit>> = listOf(
            "text" to { text = "Harbor Gate keeps its lights on" },
            "style" to { style = style.copy(size = 14.sp) },
            "ambient style" to { ambient = ambient.copy(color = Color(1f, 0f, 0f, 1f)) },
            "ambient font" to { font = UiFonts.bitmap() },
            "max lines" to { maxLines = 1 },
            "soft wrap" to { softWrap = false },
            "overflow" to { overflow = TextOverflow.Ellipsis },
            "modifier" to { modifier = Modifier.padding(4.dp) },
        )
        for ((input, change) in changes) {
            reset(truncated = input == "overflow")
            val before = frame()
            val policy = policy()
            change()
            val after = frame()
            assertNotEquals(before, after, "changing the $input left the drawing as it was")
            if (input != "modifier") assertNotSame(policy, policy(), "changing the $input kept the last policy")
        }
    }

    /** A fresh node with a label that wraps in the frame, so the line inputs show; [truncated] to its first line. */
    private fun reset(truncated: Boolean) {
        host = ComposeHost()
        text = "Harbor Town keeps its lights on"
        style = TextStyle(size = 12.sp)
        ambient = TextStyle(color = Color(1f, 1f, 1f, 1f))
        font = UiFonts.default()
        overflow = TextOverflow.Clip
        softWrap = true
        maxLines = if (truncated) 1 else Int.MAX_VALUE
        modifier = Modifier
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 80
    }
}
