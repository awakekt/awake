// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.ronjunevaldoz.awake.compose.foundation.text

import io.github.ronjunevaldoz.awake.compose.foundation.clickable
import io.github.ronjunevaldoz.awake.compose.foundation.focusable
import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.current
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.draw.drawBehind
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layout
import io.github.ronjunevaldoz.awake.compose.ui.node.TextInputNode
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalFont
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalTextStyle
import io.github.ronjunevaldoz.awake.compose.ui.text.input.EditCommand
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.ui.theme.TextStyle

// PascalCase composables: Compose's own convention. See 11-refinements.md rule 1.

private object TextFieldNodeType

private const val CARET_WIDTH = 1f

/**
 * An editable single line of text, with no decoration of its own.
 *
 * "Basic" in Compose's sense: no background, no border, no placeholder. Those are a design system's
 * job, and baking them in is what makes a field impossible to restyle.
 *
 * Focusable, so Tab reaches it and a click puts the caret where it landed. While it holds focus the
 * frame reports `isTextInputFocused`, which is how gameplay knows W/A/S/D is being typed rather
 * than walking the player -- the bug `ui-core` shipped by gating on pointer capture alone.
 */
context(_: Composer)
fun BasicTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    interactionSource: InteractionSource? = null,
) {
    val resolved = LocalTextStyle.current then style
    val font = LocalFont.current
    // Measured against the text plus one caret, so an empty field is still a full line tall and
    // does not collapse the row it sits in.
    val policy = TextMeasurePolicy(state.text.ifEmpty { " " }, resolved, font)
    val color = resolved.color ?: DefaultTextColor

    Layout(
        nodeType = TextFieldNodeType,
        modifier = modifier
            .focusable(interactionSource = interactionSource)
            .then(TextFieldInputNode(state))
            .clickable { }
            .drawBehind {
                val run = policy.runFor(density, 1f)
                run.paint(this, color)
                drawRect(
                    x = TextRunOf(state, policy, density).caretX(),
                    y = 0f,
                    width = CARET_WIDTH,
                    height = height.toFloat(),
                    color = color,
                )
            },
        measurePolicy = policy,
    )
}

/**
 * Caret x from the same run geometry the glyphs are painted with.
 *
 * Computing it separately is how a caret ends up a pixel off per character -- invisible at the
 * start of a line and obviously wrong at the end.
 */
private class TextRunOf(
    private val state: TextFieldState,
    private val policy: TextMeasurePolicy,
    private val density: Float,
) {
    fun caretX(): Float = policy.runFor(density, 1f).offsetAt(state.cursor)
}

private class TextFieldInputNode(private val state: TextFieldState) : TextInputNode {
    override fun onTextTyped(text: String) = state.insert(text)

    override fun onEditCommand(command: EditCommand) = state.apply(command)

    override fun toString(): String = "textInput(cursor=${state.cursor})"
}
