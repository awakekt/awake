/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.ScrollState
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.node.TextInputNode
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.TextEditAction
import com.awakekt.awake.core.text.theme.TextStyle
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
 *
 * A non-null [mask] makes it a password field, the shape of Compose's `PasswordVisualTransformation`
 * and of `<input type=password>`. [state] keeps the real text; only what is drawn changes, one
 * [mask] per character, IME pre-edit included. One-for-one keeps every index the same in both
 * strings, so the caret, the selection and a click's hit-test land on the masked glyphs with no
 * offset mapping. Characters are UTF-16 units, the unit [TextFieldState] edits in, so a character
 * outside the Basic Multilingual Plane draws two masks. A masked field is single-line, since a
 * line break would be a visible mask the user cannot tell from any other. While it holds focus the
 * frame asks the platform for a password keyboard (`PlatformEffects.passwordKeyboard`), and a
 * copy or cut through `ComposeHost` returns nothing.
 *
 * The bundled UI font covers printable ASCII only, so `'*'` draws as itself and `'•'` draws that
 * font's fallback glyph until a font with the bullet is supplied through `LocalFont`.
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
    mask: Char? = null,
) {
    // A newline in a password could only draw as one more mask, so the field never breaks lines.
    val oneLine = singleLine || mask != null
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
    //
    // The mask is applied here, to the same string the caret, selection and hit-test geometry are
    // read from, so all of them see the masked glyphs.
    val displayedText = {
        val shown = state.displayedText
        when {
            mask != null -> maskedText(shown, mask)
            singleLine -> shown.replace('\n', ' ')
            else -> shown
        }.ifEmpty { placeholder ?: " " }
    }
    val policy = TextMeasurePolicy(displayedText, resolved, font, singleLine = oneLine)
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
            .then(TextFieldInputElement(state, oneLine, mask, policy, density, horizontalOffset, onClick))
            .clickable { }
            .drawBehind {
                clipped {
                    val paintColor = color()
                    val run = policy.runFor(density, 1f)
                    val textOffsetY = if (oneLine) {
                        ((height - run.lineHeightPx) / 2f).coerceAtLeast(0f)
                    } else {
                        0f
                    }
                    val caret = TextRunOf(state, policy, density).position()
                    val textOffsetX = if (oneLine) {
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
                            y = if (oneLine) textOffsetY else caret.y,
                            width = CARET_WIDTH,
                            height = run.lineHeightPx,
                            color = paintColor,
                        )
                    }
                }
            },
        measurePolicy = policy,
    )
}

/** One [mask] per character of [text], so every index into one is the same index into the other. */
internal fun maskedText(text: String, mask: Char): String {
    if (text.isEmpty()) return text
    val chars = CharArray(text.length)
    chars.fill(mask)
    return chars.concatToString()
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
    private val mask: Char?,
    private val policy: TextMeasurePolicy,
    private val density: Float,
    private val horizontalOffset: ScrollState,
    private val onClick: (() -> Unit)?,
) : ModifierNodeElement<TextFieldInputNode>() {
    override fun create(): TextFieldInputNode = TextFieldInputNode()

    override fun update(node: TextFieldInputNode) {
        node.state = state
        node.singleLine = singleLine
        node.mask = mask
        node.policy = policy
        node.density = density
        node.horizontalOffset = horizontalOffset
        node.onClick = onClick
    }
}

private class TextFieldInputNode :
    Modifier.Node(),
    TextInputNode,
    PointerInputNode {
    lateinit var state: TextFieldState
    var singleLine: Boolean = false
    var mask: Char? = null
    lateinit var policy: TextMeasurePolicy
    var density: Float = 0f
    lateinit var horizontalOffset: ScrollState
    var onClick: (() -> Unit)? = null

    override val isPassword: Boolean get() = mask != null

    // A masked field never hands its text to the clipboard, the way a browser treats a password
    // input: copy yields nothing and cut neither yields nor removes anything.
    override fun copySelection(): String? = if (mask == null && state.hasSelection) state.selectedText else null

    override fun cutSelection(): String? = copySelection()?.also { state.apply(TextEditAction.Delete) }

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
