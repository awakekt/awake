/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

/** A sensible blue for a Text's link spans when [CanvasStyle.linkColor] is not set. */
private val DefaultLinkColor = Color.fromHex("#3B82F6")

/**
 * A Text element's words: [text] drawn as [style] says, or, if it holds `[[key|shown words]]`
 * spans, one [Text] run per span laid out inline in a [Row] -- spans and plain text alike -- so a
 * span's words draw in the link colour and a click on them is read back with
 * [CanvasElement.consumeLink]. Plain text with no spans draws as a single run, exactly as before.
 */
context(_: Composer)
internal fun CanvasText(element: CanvasElement, text: String, style: TextStyle) {
    val spans = parseCanvasTextSpans(text)
    val plainOnly = spans.singleOrNull() as? CanvasTextSpan.Plain
    if (plainOnly != null) {
        Text(plainOnly.text, style = style)
        return
    }
    val linkColor = colorOf(element.style.linkColor, DefaultLinkColor)
    Row {
        for (span in spans) {
            when (span) {
                is CanvasTextSpan.Plain -> Text(span.text, style = style)
                is CanvasTextSpan.Link -> Text(
                    span.text,
                    Modifier.clickable { element.linkKey = span.key }.testTag("canvas-link-${span.key}"),
                    style = style.copy(color = linkColor),
                )
            }
        }
    }
}
