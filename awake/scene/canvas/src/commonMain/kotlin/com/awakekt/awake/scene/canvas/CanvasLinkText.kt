/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlexBoxConfig
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.focus.onFocusChanged
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.ecs.Entity

/** A sensible blue for a Text's link spans when [CanvasStyle.linkColor] is not set. */
private val DefaultLinkColor = Color.fromHex("#3B82F6")

/**
 * A Text element's words: [text] drawn as [style] says, or, if it holds `[[key|shown words]]`
 * spans, one [Text] run a word, so a span's words draw in the link colour and a click on them is
 * read back with [CanvasElement.consumeLink]. The words wrap at the box's edge and a line break
 * starts a new line, as one run's would. Plain text with no spans draws as a single run.
 */
context(_: Composer)
internal fun CanvasText(element: CanvasElement, text: String, style: TextStyle) {
    val spans = parseCanvasTextSpans(text)
    val plainOnly = spans.singleOrNull() as? CanvasTextSpan.Plain
    if (plainOnly != null) {
        Text(plainOnly.text, style = style)
        return
    }
    val linkStyle = style.copy(color = colorOf(element.style.linkColor, DefaultLinkColor))
    Column {
        for (line in canvasTextLines(spans)) {
            FlexBox(config = WRAPPING) {
                if (line.isEmpty()) Text(" ", style = style)
                for (word in line) {
                    val key = word.key
                    if (key == null) {
                        Text(word.text, style = style)
                    } else {
                        Text(word.text, Modifier.clickable { element.linkKey = key }.testTag("canvas-link-$key"), style = linkStyle)
                    }
                }
            }
        }
    }
}

/** One word of a Text with links, its trailing spaces kept, and the link it belongs to, if any. */
internal class CanvasWord(val text: String, val key: String?)

/** [spans] cut into words, a list a line of the text. */
internal fun canvasTextLines(spans: List<CanvasTextSpan>): List<List<CanvasWord>> {
    val lines = arrayListOf(ArrayList<CanvasWord>())
    for (span in spans) {
        val (key, words) = when (span) {
            is CanvasTextSpan.Plain -> null to span.text
            is CanvasTextSpan.Link -> span.key to span.text
        }
        words.split('\n').forEachIndexed { index, piece ->
            if (index > 0) lines += ArrayList<CanvasWord>()
            for (match in WORD.findAll(piece)) lines.last() += CanvasWord(match.value, key)
        }
    }
    return lines
}

private val WORD = Regex("""\S+\s*|\s+""")

private val WRAPPING = FlexBoxConfig { wrap = FlexWrap.Wrap }

/** This element's words in [color], at its size, with its shadow and outline. */
internal fun CanvasElement.textStyle(color: Color) = TextStyle(
    color = color,
    size = fontSize.sp,
    shadow = style.textShadow?.toTextShadow(),
    outline = style.textOutline?.toTextOutline(),
)

/** A TextField: the player's line, typed into its box, submitted on Enter and let go on Escape. */
context(_: Composer)
internal fun TextFieldView(
    element: CanvasElement,
    modifier: Modifier,
    textStyle: TextStyle,
    tree: CanvasTree,
    children: List<Pair<Entity, CanvasElement>>,
) {
    Box(modifier) {
        element.syncTextField()
        BasicTextField(
            state = element.textFieldState,
            modifier = Modifier.fillMaxSize()
                .focusRequester(element.focusRequester)
                .onFocusChanged { element.focused = it }
                .onKeyEvent { event ->
                    val escape = event.type == KeyEventType.Down && event.key == Key.Escape
                    if (escape) element.focused = false
                    escape
                },
            style = textStyle,
            singleLine = true,
            onSubmit = element::submit,
        )
        Children(element, children, tree)
    }
}
