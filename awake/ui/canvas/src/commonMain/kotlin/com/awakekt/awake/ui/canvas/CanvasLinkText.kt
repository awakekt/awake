/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlexBoxConfig
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.focus.onFocusChanged
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle

/** A sensible blue for a Text's link spans when [CanvasStyle.linkColor] is not set. */
private val DefaultLinkColor = Color.fromHex("#3B82F6")

/**
 * A Text element's words: [text] drawn as [style] says, or, if it holds `[[key|shown words]]`
 * spans, one [Text] run a word, so a span's words draw in the link colour and a click on them is
 * read back with [CanvasElement.consumeLink]. The words wrap at the box's edge and a line break
 * starts a new line, as one run's would. Plain text with no spans draws as a single run.
 */
context(_: Composer)
internal fun CanvasWords(element: CanvasElement, text: String, style: TextStyle) {
    if (!element.links) {
        Text(text, style = style)
        return
    }
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

/** This element's words in [color] and [font], at its size, with its shadow, outline and [numerals]. */
internal fun CanvasElement.textStyle(color: Color, font: UiFont?) = TextStyle(
    color = color,
    size = fontSize.sp,
    shadow = style.textShadow?.toTextShadow(),
    outline = style.textOutline?.toTextOutline(),
    font = font,
    numerals = numerals,
)

/** How strong a TextField's hint draws against the field's text colour. */
private const val PLACEHOLDER_ALPHA = 0.5f

/** A TextField: the player's line, typed into its box, submitted on Enter and let go on Escape. */
context(_: Composer)
internal fun TextFieldView(
    element: CanvasElement,
    modifier: Modifier,
    textStyle: TextStyle,
    tree: CanvasTree,
    children: List<CanvasItem>,
) {
    Box(modifier) {
        element.syncTextField()
        element.textFieldState.maxLength = element.maxLength.takeIf { it > 0 }
        element.textFieldState.maxLengthUnit = element.maxLengthUnit
        // Read and clear once a composition: true here means this very frame's applied focus
        // request is what set it, strictly before this pass, so the Enter this same frame's
        // dispatch is about to deliver is the one that opened the field, not one meant to submit it.
        val justFocused = element.focusGainedThisFrame
        element.focusGainedThisFrame = false
        // The hint gives way to the caret as soon as the field holds the keyboard; it draws as muted
        // text, the field's own colour at half strength, since a canvas has no muted token of its own.
        val hint = if (element.focused) "" else tree.placeholderOf(element)
        BasicTextField(
            state = element.textFieldState,
            // The padding sits ahead of the field's own input and paint, so its text, caret and
            // selection all work inside it, while its clip, which reaches the node's edge, keeps the
            // overhang of a glyph at the field's edge, as `overflow` does for a padded CSS input.
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = element.padding.horizontal.coerceAtLeast(0f).dp, vertical = element.padding.vertical.coerceAtLeast(0f).dp)
                .focusRequester(element.focusRequester)
                .onFocusChanged {
                    element.focused = it
                    if (it) element.focusGainedThisFrame = true
                }
                .onKeyEvent { event ->
                    val escape = event.type == KeyEventType.Down && event.key == Key.Escape
                    if (escape) element.focused = false
                    escape
                },
            style = textStyle,
            placeholder = hint.ifEmpty { null },
            placeholderColor = textStyle.color?.let { it.withAlpha(it.a * PLACEHOLDER_ALPHA) },
            singleLine = true,
            mask = element.mask.firstOrNull(),
            onSubmit = { if (!justFocused) element.submit() },
        )
        Children(element, children, tree)
    }
}

/** A run of a Text element's words, plain or a clickable `[[key|shown words]]` span. */
internal sealed class CanvasTextSpan {
    /** Words drawn in the Text's own colour. */
    data class Plain(val text: String) : CanvasTextSpan()

    /** Words drawn in the link colour; a click on them reports [key] through [CanvasElement.consumeLink]. */
    data class Link(val key: String, val text: String) : CanvasTextSpan()
}

/** `[[key|shown words]]`, key and words each everything but `|` and `]]`. */
private val LINK_SPAN = Regex("""\[\[([^|\]]+)\|([^]]+)]]""")

/**
 * Splits [text] on `[[key|shown words]]` spans. Text with none comes back as its one [CanvasTextSpan.Plain].
 *
 * `[[` is reserved: there is no escape for a literal `[[` in plain text.
 */
internal fun parseCanvasTextSpans(text: String): List<CanvasTextSpan> {
    if ("[[" !in text) return listOf(CanvasTextSpan.Plain(text))
    return buildList {
        var last = 0
        for (match in LINK_SPAN.findAll(text)) {
            if (match.range.first > last) add(CanvasTextSpan.Plain(text.substring(last, match.range.first)))
            add(CanvasTextSpan.Link(match.groupValues[1], match.groupValues[2]))
            last = match.range.last + 1
        }
        if (last < text.length) add(CanvasTextSpan.Plain(text.substring(last)))
    }
}
