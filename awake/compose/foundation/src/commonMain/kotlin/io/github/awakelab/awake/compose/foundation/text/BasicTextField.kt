/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.compose.foundation.text

import io.github.awakelab.awake.compose.foundation.ScrollState
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.focusable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.draw.drawBehind
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.node.PointerInputNode
import io.github.awakelab.awake.compose.ui.node.TextInputNode
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventPass
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.platform.LocalDensity
import io.github.awakelab.awake.compose.ui.platform.LocalFrameClock
import io.github.awakelab.awake.compose.ui.platform.LocalFont
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.input.TextEditAction
import io.github.awakelab.awake.core.input.ImeComposition
import io.github.awakelab.awake.core.text.theme.TextStyle
import kotlin.math.roundToInt

// PascalCase composables: Compose's own convention. See 11-refinements.md rule 1.

private object TextFieldNodeType

private const val CARET_WIDTH = 1f
private const val CARET_BLINK_PERIOD_SECONDS = 1f

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
    placeholder: String? = null,
    placeholderColor: Color? = null,
    singleLine: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val resolved = LocalTextStyle.current then style
    val font = LocalFont.current
    val source = interactionSource ?: remember { InteractionSource() }
    val density = LocalDensity.current
    val clock = LocalFrameClock.current
    val horizontalOffset = remember { ScrollState() }
    // Measure and paint the placeholder through the same run as real text. That keeps baselines,
    // clipping, and the empty-field minimum line height identical instead of treating a placeholder
    // as a decoration with its own layout rules.
    //
    // Read through a lambda, not captured: the string this resolves to must be this frame's, and
    // composition is a phase too early for that. See TextMeasurePolicy.text.
    val displayedText = {
        state.displayedText
            .let { if (singleLine) it.replace('\n', ' ') else it }
            .ifEmpty { placeholder ?: " " }
    }
    val policy = TextMeasurePolicy(displayedText, resolved, font, singleLine = singleLine)
    // Likewise a lambda: whether the field is showing its placeholder is a per-frame answer.
    val color = {
        if (state.text.isEmpty() && placeholder != null) {
            placeholderColor ?: resolved.color ?: DefaultTextColor
        } else {
            resolved.color ?: DefaultTextColor
        }
    }

    Layout(
        nodeType = TextFieldNodeType,
        modifier = modifier
            .focusable(interactionSource = source)
            .then(TextFieldInputElement(state, singleLine, policy, density, horizontalOffset, onClick))
            .clickable { }
            .drawBehind {
                val paintColor = color()
                val run = policy.runFor(density, 1f)
                val textOffsetY = if (singleLine) {
                    ((height - run.lineHeightPx) / 2f).coerceAtLeast(0f)
                } else {
                    0f
                }
                val caret = TextRunOf(state, policy, density).position()
                val textOffsetX = if (singleLine) {
                    val maxOffset = (run.offsetAt(policy.text.length) - width).coerceAtLeast(0f)
                    horizontalOffset.maxValue = maxOffset.roundToInt()
                    val visibleStart = horizontalOffset.value.toFloat()
                    val visibleEnd = visibleStart + width
                    when {
                        caret.x < visibleStart -> horizontalOffset.scrollTo(caret.x.roundToInt())
                        caret.x > visibleEnd -> horizontalOffset.scrollTo((caret.x - width).roundToInt())
                    }
                    -horizontalOffset.value.toFloat()
                } else {
                    0f
                }
                run.forEachSelectionSegment(state.displayedSelectionStart, state.displayedSelectionEnd) { x, y, width, height ->
                    drawRect(
                        x = x + textOffsetX,
                        y = y + textOffsetY,
                        width = width,
                        height = height,
                        color = paintColor.withAlpha(0.35f),
                    )
                }
                run.paint(this, paintColor, offsetX = textOffsetX, offsetY = textOffsetY)
                if (source.isFocused && isCaretVisible(clock.totalSeconds)) {
                    drawRect(
                        x = caret.x + textOffsetX,
                        y = if (singleLine) textOffsetY else caret.y,
                        width = CARET_WIDTH,
                        height = run.lineHeightPx,
                        color = paintColor,
                    )
                }
            },
        measurePolicy = policy,
    )
}

private fun isCaretVisible(totalSeconds: Float): Boolean =
    (totalSeconds % CARET_BLINK_PERIOD_SECONDS) < CARET_BLINK_PERIOD_SECONDS / 2f

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
    fun position(): CaretPosition {
        val run = policy.runFor(density, 1f)
        val position = run.caretPositionAt(state.displayedCursor)
        return CaretPosition(position.x, position.y)
    }
}

private data class CaretPosition(val x: Float, val y: Float)

private class TextFieldInputElement(
    private val state: TextFieldState,
    private val singleLine: Boolean,
    private val policy: TextMeasurePolicy,
    private val density: Float,
    private val horizontalOffset: ScrollState,
    private val onClick: (() -> Unit)?,
) : ModifierNodeElement<TextFieldInputNode>() {
    override fun create(): TextFieldInputNode = TextFieldInputNode()

    override fun update(node: TextFieldInputNode) {
        node.state = state
        node.singleLine = singleLine
        node.policy = policy
        node.density = density
        node.horizontalOffset = horizontalOffset
        node.onClick = onClick
    }
}

private class TextFieldInputNode : Modifier.Node(), TextInputNode, PointerInputNode {
    lateinit var state: TextFieldState
    var singleLine: Boolean = false
    lateinit var policy: TextMeasurePolicy
    var density: Float = 0f
    lateinit var horizontalOffset: ScrollState
    var onClick: (() -> Unit)? = null

    override fun onTextTyped(text: String) = state.insert(if (singleLine) text.replace('\n', ' ') else text)

    override fun onImeComposition(composition: ImeComposition) {
        state.setComposition(
            if (singleLine) composition.copy(text = composition.text.replace('\n', ' ')) else composition,
        )
    }

    override fun onImeCommit(text: String) = state.commitComposition(if (singleLine) text.replace('\n', ' ') else text)

    override fun onEditAction(action: TextEditAction) {
        if (action == TextEditAction.Enter && !singleLine) {
            state.insert("\n")
        } else {
            state.apply(action)
        }
    }

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.isConsumed) return
        val index = policy.runFor(density, 1f).indexAt(
            x = (event.x + if (singleLine) horizontalOffset.value else 0).toFloat(),
            y = event.y.toFloat(),
        )
        when (event.type) {
            PointerEventType.Press -> {
                state.moveCursorTo(index)
                event.consume()
            }
            PointerEventType.Release -> onClick?.invoke()
            PointerEventType.Move -> if (event.isCaptureHolder) {
                state.select(state.selectionAnchor, index)
                event.consume()
            }
            else -> Unit
        }
    }

    override fun toString(): String = "textInput(cursor=${state.cursor})"
}
