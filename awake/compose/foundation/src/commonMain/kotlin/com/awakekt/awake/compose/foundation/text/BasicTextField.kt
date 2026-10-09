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
import com.awakekt.awake.compose.ui.platform.FrameClock
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
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
 * The mask a password field should pass to `BasicTextField(mask = ...)`, so every field in an app
 * hides its text the same way: U+2022 BULLET, what browsers and Compose draw.
 *
 * The bundled UI font packs it beside printable ASCII for exactly this. A font supplied through
 * `LocalFont` without it draws its fallback glyph instead; `MaskedTextFieldTest` guards the bundled
 * font.
 *
 * Deliberately not `const`: a constant is inlined into every caller's bytecode, so a later change
 * to the mask would never reach code compiled against this release.
 */
@Suppress("MayBeConst")
val PasswordMask: Char = '\u2022'

/** How long a just-typed character stays readable in a masked field: Compose's own 1.5 s. */
private const val LAST_TYPED_REVEAL_SECONDS = 1.5f

// Shared rather than built per pass: a configuration is never mutated once declared (the tree
// builder merges into a fresh one), so one instance serves every masked field with no allocation.
private val PasswordSemantics: Modifier = Modifier.semantics { this[SemanticsProperties.Password] = true }

context(_: Composer)
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
 * frame asks the platform for a password keyboard (`PlatformEffects.passwordKeyboard`), a copy or
 * cut through `ComposeHost` returns nothing, and its semantics carry
 * [SemanticsProperties.Password] so an accessibility service does not read the text aloud.
 *
 * [revealLastTyped] shows a character in clear for 1.5 seconds after it is typed, Compose's
 * `TextObfuscationMode.RevealLastTyped` and what Android and iOS do on a touch keyboard, where a
 * mistyped key is otherwise invisible. Only a single typed character is revealed: a paste, an IME
 * pre-edit, or any later edit hides it at once. Off by default, because a desktop browser never
 * reveals; pass it on touch platforms.
 *
 * Pass [PasswordMask] unless a design calls for another character. The bundled UI font covers
 * printable ASCII and the bullet; any other mask character draws that font's fallback glyph.
 */
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
    revealLastTyped: Boolean = false,
) {
    // A newline in a password could only draw as one more mask, so the field never breaks lines.
    val oneLine = singleLine || mask != null
    val resolved = LocalTextStyle.current then style
    val font = LocalFont.current
    val source = interactionSource ?: remember { InteractionSource() }
    val density = LocalDensity.current
    val clock = LocalFrameClock.current
    val horizontalOffset = remember { ScrollState() }
    val reveal = remember(state) { LastTypedReveal() }
    // Measure and paint the placeholder through the same run as real text. That keeps baselines,
    // clipping, and the empty-field minimum line height identical instead of treating a placeholder
    // as a decoration with its own layout rules.
    //
    // Read through a lambda, not captured: the string this resolves to must be this frame's, and
    // composition is a phase too early for that. See TextMeasurePolicy.text.
    //
    // The mask is applied here, to the same string the caret, selection and hit-test geometry are
    // read from, so all of them see the masked glyphs.
    val displayedText = { fieldText(state, mask, reveal, clock, singleLine).ifEmpty { placeholder ?: " " } }
    val policy = TextMeasurePolicy(displayedText, resolved, font, singleLine = oneLine)
    // Likewise a lambda: whether the field is showing its placeholder is a per-frame answer.
    val textColor = resolved.color ?: DefaultTextColor
    val color = { if (state.text.isEmpty() && placeholder != null) placeholderColor ?: textColor else textColor }

    Layout(
        nodeType = TextFieldNodeType,
        modifier = modifier
            .focusable(interactionSource = source)
            .then(if (mask != null) PasswordSemantics else Modifier)
            .then(
                TextFieldInputElement(
                    state, oneLine, mask, reveal.takeIf { revealLastTyped }, clock,
                    policy, density, horizontalOffset, onClick,
                ),
            )
            .clickable { }
            .drawBehind {
                clipped {
                    val paintColor = color()
                    val run = policy.runFor(density, 1f)
                    val textOffsetY = if (oneLine) ((height - run.lineHeightPx) / 2f).coerceAtLeast(0f) else 0f
                    val caret = TextRunOf(state, policy, density).position()
                    val focused = source.isFocused
                    val textOffsetX = if (oneLine) horizontalOffset.followCaret(caret.x, focused, run, policy, width) else 0f
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
                    if (focused && isCaretVisible(clock.totalSeconds)) {
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

/**
 * Scrolls a single-line field so the caret stays inside [width], and returns the x offset to draw
 * [run] at. Unfocused, it shows the start of the text instead: the caret sits at the end after an
 * edit, so following it would cut the first characters off while nobody is editing.
 */
private fun ScrollState.followCaret(
    caretX: Float,
    focused: Boolean,
    run: TextRun,
    policy: TextMeasurePolicy,
    width: Int,
): Float {
    maxValue = (run.offsetAt(policy.text.length) - width).coerceAtLeast(0f).roundToInt()
    val targetX = if (focused) caretX else 0f
    val visibleStart = value.toFloat()
    when {
        targetX < visibleStart -> scrollTo(targetX.roundToInt())
        targetX > visibleStart + width -> scrollTo((targetX - width).roundToInt())
    }
    return -value.toFloat()
}

/** What the field draws this frame, before an empty field falls back to its placeholder. */
private fun fieldText(
    state: TextFieldState,
    mask: Char?,
    reveal: LastTypedReveal,
    clock: FrameClock,
    singleLine: Boolean,
): String {
    val shown = state.displayedText
    return when {
        mask == null -> if (singleLine) shown.replace('\n', ' ') else shown
        reveal.isShowing(state, clock.totalSeconds) -> maskedText(shown, mask, reveal.start, reveal.end)
        else -> maskedText(shown, mask)
    }
}

/**
 * One [mask] per character of [text], so every index into one is the same index into the other.
 * Characters in `revealStart until revealEnd` are kept in clear.
 */
internal fun maskedText(text: String, mask: Char, revealStart: Int = 0, revealEnd: Int = 0): String {
    if (text.isEmpty()) return text
    val chars = CharArray(text.length)
    chars.fill(mask)
    for (i in revealStart.coerceAtLeast(0) until revealEnd.coerceAtMost(text.length)) chars[i] = text[i]
    return chars.concatToString()
}

/**
 * The character a masked field shows in clear right after it is typed.
 *
 * Valid only while the field's text is the very string the typing produced: any other edit makes a
 * new string, so an identity check retires the reveal without every edit path having to clear it.
 */
internal class LastTypedReveal {
    private var revealedText: String? = null
    private var until = 0f

    var start = 0
        private set
    var end = 0
        private set

    /** Reveals what was just inserted before the caret if it is one character, else hides. */
    fun record(state: TextFieldState, inserted: String, now: Float) {
        val oneCharacter = inserted.length == 1 ||
            (inserted.length == 2 && inserted[0].isHighSurrogate() && inserted[1].isLowSurrogate())
        if (!oneCharacter || state.hasComposition || state.cursor < inserted.length) {
            clear()
            return
        }
        revealedText = state.text
        end = state.cursor
        start = end - inserted.length
        until = now + LAST_TYPED_REVEAL_SECONDS
    }

    fun clear() {
        revealedText = null
    }

    fun isShowing(state: TextFieldState, now: Float): Boolean =
        revealedText != null && revealedText === state.text && !state.hasComposition && now < until
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

// One field per thing the node reads; grouping them would only move the list into another class.
@Suppress("LongParameterList")
private class TextFieldInputElement(
    private val state: TextFieldState,
    private val singleLine: Boolean,
    private val mask: Char?,
    private val reveal: LastTypedReveal?,
    private val clock: FrameClock,
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
        node.reveal = reveal
        node.clock = clock
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
    var reveal: LastTypedReveal? = null
    lateinit var clock: FrameClock
    lateinit var policy: TextMeasurePolicy
    var density: Float = 0f
    lateinit var horizontalOffset: ScrollState
    var onClick: (() -> Unit)? = null

    override val isPassword: Boolean get() = mask != null

    // A masked field never hands its text to the clipboard, the way a browser treats a password
    // input: copy yields nothing and cut neither yields nor removes anything.
    override fun copySelection(): String? = if (mask == null && state.hasSelection) state.selectedText else null

    override fun cutSelection(): String? = copySelection()?.also { state.apply(TextEditAction.Delete) }

    override fun onTextTyped(text: String) {
        val inserted = if (singleLine) text.replace('\n', ' ') else text
        state.insert(inserted)
        reveal?.record(state, inserted, clock.totalSeconds)
    }

    override fun onImeComposition(composition: ImeComposition) {
        state.setComposition(
            if (singleLine) composition.copy(text = composition.text.replace('\n', ' ')) else composition,
        )
    }

    override fun onImeCommit(text: String) {
        val committed = if (singleLine) text.replace('\n', ' ') else text
        state.commitComposition(committed)
        reveal?.record(state, committed, clock.totalSeconds)
    }

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
