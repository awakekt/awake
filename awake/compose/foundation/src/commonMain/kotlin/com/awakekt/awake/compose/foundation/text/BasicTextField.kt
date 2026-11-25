/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "UndocumentedPublicFunction", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.ScrollState
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.focusable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.node.TextInputNode
import com.awakekt.awake.compose.ui.platform.FrameClock
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.platform.LocalFrameClock
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.TextEditAction
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.WordBoundaries
import kotlin.math.roundToInt

// PascalCase composables: Compose's own convention. See 11-refinements.md rule 1.

private object TextFieldNodeType

private const val CARET_WIDTH = 1f
private const val CARET_BLINK_PERIOD_SECONDS = 1f
private const val DOUBLE_CLICK_MAX_SECONDS = 0.4f
private const val DOUBLE_CLICK_SLOP_PX = 8

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
@Suppress("MayBeConstant")
val PasswordMask: Char = '\u2022'

/** How long a just-typed character stays readable in a masked field: Compose's own 1.5 s. */
private const val LAST_TYPED_REVEAL_SECONDS = 1.5f

// Shared rather than built per pass: a configuration is never mutated once declared (the tree
// builder merges into a fresh one), so one instance serves every masked field with no allocation.
private val PasswordSemantics: Modifier = Modifier.semantics { this[SemanticsProperties.Password] = true }
private val DisabledSemantics: Modifier = Modifier.semantics { this[SemanticsProperties.Disabled] = true }
private val NoOpClickable: Modifier = Modifier.clickable {}

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
 * When [enabled] is `false`, the field leaves the focus ring, publishes
 * [SemanticsProperties.Disabled], and ignores pointer selection, typing, paste, and IME edits while
 * keeping [state]'s text and selection untouched until re-enabled.
 *
 * A non-null [mask] makes it a password field, the shape of Compose's `PasswordVisualTransformation`
 * and of `<input type=password>`. [state] keeps the real text; only what is drawn changes, one
 * [mask] per character, IME pre-edit included. A character is a grapheme cluster, the unit
 * [TextFieldState] edits in, so an emoji draws one mask however many `Char`s it takes. The masked
 * string keeps every index the same as the real one, so the caret, the selection and a click's
 * hit-test land on the masked glyphs with no offset mapping. A masked field is single-line, since a
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
 * printable ASCII, the bullet and the Latin accented letters and signs of `UiFonts.DEFAULT_CHARS`; any other
 * mask character draws that font's fallback glyph.
 *
 * [onSubmit] fires on Enter in a single-line field (`singleLine`, or masked since a password field
 * is always single-line) -- Compose's `KeyboardActions.onDone`. A multi-line field has nowhere to
 * submit from, so Enter inserts a newline there instead, same as always.
 *
 * [singleLine] also decides which keys gameplay keeps reading while this field holds focus: Up and
 * Down move the caret in a multi-line field, so they stay taken there, but a single-line field has
 * no use for them and lets them through -- a consumer pack's chat line steps through its sent
 * history with them. [passThroughKeys] lets a field give up more than that default, e.g. a
 * multi-line notes field that still wants Up/Down to reach the game.
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
    onSubmit: (() -> Unit)? = null,
    passThroughKeys: Set<Key> = emptySet(),
    enabled: Boolean = true,
) {
    rememberGroup(BasicTextFieldGroup) {
        // A newline in a password could only draw as one more mask, so the field never breaks lines.
        val oneLine = singleLine || mask != null
        val fieldState = remember { BasicTextFieldNodeState() }
        val source = fieldState.interactionSourceFor(interactionSource)
        val policy = fieldState.sync(
            state = state,
            ambientStyle = LocalTextStyle.current,
            style = style,
            ambientFont = LocalFont.current,
            density = LocalDensity.current,
            clock = LocalFrameClock.current,
            source = source,
            placeholder = placeholder,
            placeholderColor = placeholderColor,
            singleLine = singleLine,
            oneLine = oneLine,
            mask = mask,
            revealLastTyped = revealLastTyped,
            onClick = onClick,
            onSubmit = onSubmit,
            passThroughKeys = passThroughKeys,
            enabled = enabled,
        )

        Layout(
            nodeType = TextFieldNodeType,
            modifier = fieldState.modifierFor(modifier, enabled, source, mask != null),
            measurePolicy = policy,
        )
    }
}

/** Binary-compatible overload without `enabled`. */
@Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
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
    revealLastTyped: Boolean = false,
    onSubmit: (() -> Unit)? = null,
    passThroughKeys: Set<Key> = emptySet(),
) = BasicTextField(
    state = state,
    modifier = modifier,
    style = style,
    interactionSource = interactionSource,
    placeholder = placeholder,
    placeholderColor = placeholderColor,
    singleLine = singleLine,
    onClick = onClick,
    mask = mask,
    revealLastTyped = revealLastTyped,
    onSubmit = onSubmit,
    passThroughKeys = passThroughKeys,
    enabled = true,
)

private fun resolveFieldColor(
    state: TextFieldState,
    resolvedColor: Color?,
    placeholder: String?,
    placeholderColor: Color?,
): Color {
    val textColor = resolvedColor ?: DefaultTextColor
    return if (state.text.isEmpty() && placeholder != null) placeholderColor ?: textColor else textColor
}

private fun DrawScope.drawSelectionSegments(
    run: TextRun,
    state: TextFieldState,
    offsetX: Float,
    offsetY: Float,
    color: Color,
) {
    run.forEachSelectionSegment(state.displayedSelectionStart, state.displayedSelectionEnd) { x, y, width, height ->
        drawRect(x = x + offsetX, y = y + offsetY, width = width, height = height, color = color)
    }
}

private fun DrawScope.drawFieldCaret(x: Float, y: Float, height: Float, color: Color) {
    drawRect(x = x, y = y, width = CARET_WIDTH, height = height, color = color)
}

/**
 * Clips a field's text at its padding box, as CSS clips a field's content, not at the line box: a
 * glyph's ink reaches past its advance and its line (an accent, a leading `j`'s tail), and a clip at
 * the text's own box cut it. A side a single line is scrolled past ([scroll], null for a multi-line
 * field) keeps the tight clip, so a scrolled-away character never shows in the padding.
 */
private fun DrawScope.clipFieldText(scroll: ScrollState?, block: () -> Unit) {
    val scrolledLeft = scroll != null && scroll.value > 0
    val scrolledRight = scroll != null && scroll.value < scroll.maxValue
    clippedWithMargin(
        left = if (scrolledLeft) 0f else Float.POSITIVE_INFINITY,
        top = Float.POSITIVE_INFINITY,
        right = if (scrolledRight) 0f else Float.POSITIVE_INFINITY,
        bottom = Float.POSITIVE_INFINITY,
        block = block,
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
 * One [mask] per grapheme cluster of [text], with every index into one the same index into the
 * other. Clusters that overlap `revealStart until revealEnd` are kept in clear.
 *
 * A cluster of several `Char`s becomes the mask followed by [MASK_FILLER]s, which join it into one
 * cluster that draws as the mask alone, so the run sees as many clusters, at the same indices, as
 * the real text has.
 */
internal fun maskedText(text: String, mask: Char, revealStart: Int = 0, revealEnd: Int = 0): String {
    if (text.isEmpty()) return text
    val chars = CharArray(text.length)
    GraphemeClusters.forEach(text) { start, end ->
        if (start < revealEnd && end > revealStart) {
            for (i in start until end) chars[i] = text[i]
        } else {
            chars[start] = mask
            for (i in start + 1 until end) chars[i] = MASK_FILLER
        }
    }
    return chars.concatToString()
}

/**
 * U+034F COMBINING GRAPHEME JOINER: it extends the cluster before it and draws nothing. Not ZWJ,
 * which would join two masks that are themselves pictographs into one cluster.
 */
private const val MASK_FILLER = '\u034F'

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

    /** Reveals what was just inserted before the caret if it is one character (grapheme cluster), else hides. */
    fun record(state: TextFieldState, inserted: String, now: Float) {
        val oneCharacter = GraphemeClusters.count(inserted) == 1
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

private fun shouldDrawCaret(focused: Boolean, totalSeconds: Float): Boolean =
    focused && (totalSeconds % CARET_BLINK_PERIOD_SECONDS) < CARET_BLINK_PERIOD_SECONDS / 2f

/**
 * Retained per-node state for [BasicTextField]: keeps its layout cache, motion state, scroll
 * offset, reveal tracker, measure policy, input element, draw element, and modifier chain across
 * frames so an unchanged field allocates nothing on steady-state passes.
 */
private class BasicTextFieldNodeState {
    val textState = TextNodeState()
    val horizontalOffset = ScrollState()
    val reveal = LastTypedReveal()

    private var ownedSource: InteractionSource? = null
    private var hasState = false
    lateinit var state: TextFieldState
    var singleLine: Boolean = false
    var oneLine: Boolean = false
    var mask: Char? = null
    var revealLastTyped: Boolean = false
    var placeholder: String? = null
    var placeholderColor: Color? = null
    var resolvedColor: Color? = null
    var density: Float = 1f
    lateinit var clock: FrameClock
    var enabled: Boolean = true
    lateinit var source: InteractionSource
    var onClick: (() -> Unit)? = null
    var onSubmit: (() -> Unit)? = null
    var passThroughKeys: Set<Key> = emptySet()

    private var resolvedForFont: TextStyle? = null
    private var ambientFont: UiFont? = null
    private var font: UiFont? = null
    private var policyStyle: TextStyle? = null
    private var policyFont: UiFont? = null
    private var policyOneLine: Boolean = false
    private var cachedPolicy: TextMeasurePolicy? = null
    lateinit var policy: TextMeasurePolicy

    private var hasChain = false
    private var lastModifier: Modifier? = null
    private var lastEnabled = false
    private var lastSource: InteractionSource? = null
    private var lastHasMask = false
    private var chain: Modifier = Modifier

    // Measure and paint the placeholder through the same run as real text. Read through one
    // retained lambda so composition stays allocation-free while measure sees live edits.
    private val displayedText: () -> String = {
        fieldText(state, mask, reveal, clock, singleLine).ifEmpty { placeholder ?: " " }
    }
    private val inputModifier: Modifier = TextFieldInputElement(this).then(NoOpClickable)
    private val drawElement: BasicTextFieldDrawElement = BasicTextFieldDrawElement(this)

    fun interactionSourceFor(caller: InteractionSource?): InteractionSource =
        caller ?: ownedSource ?: InteractionSource().also { ownedSource = it }

    @Suppress("LongParameterList")
    fun sync(
        state: TextFieldState,
        ambientStyle: TextStyle,
        style: TextStyle,
        ambientFont: UiFont,
        density: Float,
        clock: FrameClock,
        source: InteractionSource,
        placeholder: String?,
        placeholderColor: Color?,
        singleLine: Boolean,
        oneLine: Boolean,
        mask: Char?,
        revealLastTyped: Boolean,
        onClick: (() -> Unit)?,
        onSubmit: (() -> Unit)?,
        passThroughKeys: Set<Key>,
        enabled: Boolean,
    ): TextMeasurePolicy {
        if (!hasState || this.state !== state) {
            hasState = true
            this.state = state
            reveal.clear()
        }
        this.singleLine = singleLine
        this.oneLine = oneLine
        this.mask = mask
        this.revealLastTyped = revealLastTyped
        this.placeholder = placeholder
        this.placeholderColor = placeholderColor
        this.density = density
        this.clock = clock
        this.enabled = enabled
        this.source = source
        this.onClick = onClick
        this.onSubmit = onSubmit
        this.passThroughKeys = passThroughKeys

        val resolved = textState.resolve(ambientStyle, style)
        resolvedColor = resolved.color
        textState.motion.clock = clock
        val resolvedFont = fontFor(resolved, ambientFont)
        val existing = cachedPolicy
        val samePolicyInputs = policyStyle === resolved && policyFont === resolvedFont && policyOneLine == oneLine
        val nextPolicy = if (existing != null && samePolicyInputs) {
            existing
        } else {
            policyStyle = resolved
            policyFont = resolvedFont
            policyOneLine = oneLine
            TextMeasurePolicy(displayedText, resolved, resolvedFont, singleLine = oneLine).also {
                it.cache = textState.cache
                it.motion = textState.motion
                cachedPolicy = it
            }
        }
        policy = nextPolicy
        return nextPolicy
    }

    fun modifierFor(
        modifier: Modifier,
        enabled: Boolean,
        source: InteractionSource,
        hasMask: Boolean,
    ): Modifier {
        val sameInputs = modifier == lastModifier && enabled == lastEnabled && source === lastSource
        if (!hasChain || !sameInputs || hasMask != lastHasMask) {
            hasChain = true
            lastModifier = modifier
            lastEnabled = enabled
            lastSource = source
            lastHasMask = hasMask
            chain = modifier
                .focusable(enabled = enabled, interactionSource = source)
                .then(if (hasMask) PasswordSemantics else Modifier)
                .then(if (enabled) inputModifier else DisabledSemantics)
                .then(drawElement)
        }
        return chain
    }

    private fun fontFor(resolved: TextStyle, ambient: UiFont): UiFont {
        val cached = font
        if (cached != null && resolved === resolvedForFont && ambient === ambientFont) return cached
        resolvedForFont = resolved
        ambientFont = ambient
        return resolved.resolveFont(ambient).also { font = it }
    }
}

private class BasicTextFieldDrawElement(
    private val fieldState: BasicTextFieldNodeState,
) : ModifierNodeElement<BasicTextFieldDrawNode>() {
    override fun create(): BasicTextFieldDrawNode = BasicTextFieldDrawNode(fieldState)

    override fun update(node: BasicTextFieldDrawNode) {
        node.fieldState = fieldState
    }

    override fun toString(): String = "basicTextFieldDraw()"
}

private class BasicTextFieldDrawNode(
    var fieldState: BasicTextFieldNodeState,
) : Modifier.Node(),
    DrawModifierNode {
    // Reused across draw passes so clippedWithMargin allocates no capturing lambda per frame.
    private var activeScope: DrawScope? = null
    private var activeRun: TextRun? = null
    private var activeColor: Color = DefaultTextColor
    private var activeOffsetX: Float = 0f
    private var activeOffsetY: Float = 0f
    private var activeDrawCaret: Boolean = false
    private var activeCaretX: Float = 0f
    private var activeCaretY: Float = 0f
    private val drawClippedBody: () -> Unit = {
        val scope = activeScope!!
        val run = activeRun!!
        val paintColor = activeColor
        if (fieldState.enabled && fieldState.state.hasSelection) {
            scope.drawSelectionSegments(run, fieldState.state, activeOffsetX, activeOffsetY, paintColor.withAlpha(0.35f))
        }
        run.paintDecorated(scope, paintColor, offsetX = activeOffsetX, offsetY = activeOffsetY, motion = fieldState.textState.motion)
        if (activeDrawCaret) {
            scope.drawFieldCaret(
                x = activeCaretX + activeOffsetX,
                y = activeCaretY,
                height = run.lineHeightPx,
                color = paintColor,
            )
        }
    }

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val paintColor = resolveFieldColor(fieldState.state, fieldState.resolvedColor, fieldState.placeholder, fieldState.placeholderColor)
        val policy = fieldState.policy
        val run = policy.runFor(fieldState.density, policy.measuredFontScale)
        val oneLine = fieldState.oneLine
        val textOffsetY = if (oneLine) ((height - run.lineHeightPx) / 2f).coerceAtLeast(0f) else 0f
        val focused = fieldState.enabled && fieldState.source.isFocused
        val caretLine = if (focused) run.caretLineIndex(fieldState.state.displayedCursor) else 0
        val caretX = if (focused) run.caretXAt(fieldState.state.displayedCursor, caretLine) else 0f
        val textOffsetX = if (oneLine) fieldState.horizontalOffset.followCaret(caretX, focused, run, policy, width) else 0f
        activeScope = this
        activeRun = run
        activeColor = paintColor
        activeOffsetX = textOffsetX
        activeOffsetY = textOffsetY
        activeDrawCaret = shouldDrawCaret(focused, fieldState.clock.totalSeconds)
        activeCaretX = caretX
        activeCaretY = if (oneLine) textOffsetY else caretLine * run.lineHeightPx
        clipFieldText(if (oneLine) fieldState.horizontalOffset else null, drawClippedBody)
        activeScope = null
        activeRun = null
        drawContent()
    }

    override fun toString(): String = "basicTextFieldDraw()"
}

private class TextFieldInputElement(
    private val fieldState: BasicTextFieldNodeState,
) : ModifierNodeElement<TextFieldInputNode>() {
    override fun create(): TextFieldInputNode = TextFieldInputNode(fieldState)

    override fun update(node: TextFieldInputNode) {
        node.fieldState = fieldState
        node.syncState()
    }
}

private class DoubleClickTracker {
    private var firstPressSeconds: Float = Float.NEGATIVE_INFINITY
    private var firstPressX: Int = 0
    private var firstPressY: Int = 0
    private var firstPressText: String? = null
    private var firstPressCursor: Int = -1
    private var firstPressDragged: Boolean = false
    private var awaitingSecondPress: Boolean = false
    private var wordDragStart: Int = -1
    private var wordDragEnd: Int = -1

    fun reset() {
        firstPressSeconds = Float.NEGATIVE_INFINITY
        firstPressText = null
        firstPressCursor = -1
        firstPressDragged = false
        awaitingSecondPress = false
        wordDragStart = -1
        wordDragEnd = -1
    }

    fun onPress(state: TextFieldState, mask: Char?, event: PointerEvent, index: Int, now: Float) {
        if (isSecondPressOfDoubleClick(state, event, now)) {
            awaitingSecondPress = false
            firstPressSeconds = Float.NEGATIVE_INFINITY
            if (mask != null) {
                state.selectAll()
            } else {
                state.selectWordAt(index)
                wordDragStart = state.selectionStart
                wordDragEnd = state.selectionEnd
            }
        } else {
            awaitingSecondPress = false
            wordDragStart = -1
            wordDragEnd = -1
            firstPressSeconds = now
            firstPressX = event.x
            firstPressY = event.y
            firstPressDragged = false
            state.moveCursorTo(index)
            firstPressText = state.text
            firstPressCursor = state.cursor
        }
    }

    fun onCapturedMove(state: TextFieldState, mask: Char?, event: PointerEvent, index: Int) {
        if (wordDragStart >= 0 && mask == null) {
            val targetStart = WordBoundaries.wordStart(state.text, index)
            val targetEnd = WordBoundaries.wordEnd(state.text, index)
            if (targetStart < wordDragStart) {
                state.select(wordDragEnd, targetStart)
            } else {
                state.select(wordDragStart, targetEnd)
            }
        } else {
            if (!isWithinSlop(event.x, event.y)) firstPressDragged = true
            state.select(state.selectionAnchor, index)
        }
    }

    fun onHoverMove(event: PointerEvent) {
        if (awaitingSecondPress && !isWithinSlop(event.x, event.y)) awaitingSecondPress = false
    }

    fun onRelease(state: TextFieldState, event: PointerEvent) {
        val wasWordDrag = wordDragStart >= 0
        wordDragStart = -1
        wordDragEnd = -1
        val cleanPointerGesture = !wasWordDrag && event.isCaptureHolder && event.isInBounds
        val cleanTap = cleanPointerGesture && !firstPressDragged && !event.longPressConsumed
        awaitingSecondPress = cleanTap && isUnchangedSinceFirstPress(state)
    }

    fun onExit(event: PointerEvent) {
        if (!event.isCaptureHolder) awaitingSecondPress = false
    }

    private fun isSecondPressOfDoubleClick(state: TextFieldState, event: PointerEvent, now: Float): Boolean {
        if (!awaitingSecondPress || !isUnchangedSinceFirstPress(state)) return false
        val withinTime = now >= firstPressSeconds && now - firstPressSeconds <= DOUBLE_CLICK_MAX_SECONDS
        return withinTime && isWithinSlop(event.x, event.y)
    }

    private fun isUnchangedSinceFirstPress(state: TextFieldState): Boolean =
        !state.hasSelection && state.text === firstPressText && state.cursor == firstPressCursor

    private fun isWithinSlop(x: Int, y: Int): Boolean {
        val dx = x - firstPressX
        val dy = y - firstPressY
        return dx * dx + dy * dy <= DOUBLE_CLICK_SLOP_PX * DOUBLE_CLICK_SLOP_PX
    }
}

private class TextFieldInputNode(
    var fieldState: BasicTextFieldNodeState,
) : Modifier.Node(),
    TextInputNode,
    PointerInputNode {
    private val clicks = DoubleClickTracker()
    private var lastState: TextFieldState = fieldState.state

    private val state: TextFieldState get() = fieldState.state
    private val singleLine: Boolean get() = fieldState.oneLine
    private val mask: Char? get() = fieldState.mask
    private val reveal: LastTypedReveal? get() = if (fieldState.revealLastTyped) fieldState.reveal else null
    private val clock: FrameClock get() = fieldState.clock
    private val policy: TextMeasurePolicy get() = fieldState.policy
    private val density: Float get() = fieldState.density
    private val horizontalOffset: ScrollState get() = fieldState.horizontalOffset
    private val onClick: (() -> Unit)? get() = fieldState.onClick
    private val onSubmit: (() -> Unit)? get() = fieldState.onSubmit
    override val passThroughKeys: Set<Key> get() = fieldState.passThroughKeys

    override val isPassword: Boolean get() = mask != null
    override val isSingleLine: Boolean get() = singleLine

    fun syncState() {
        val current = fieldState.state
        if (lastState !== current) {
            clicks.reset()
            lastState = current
        }
    }

    override fun onDetach() {
        clicks.reset()
    }

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
        when {
            action == TextEditAction.Enter -> if (singleLine) onSubmit?.invoke() else state.insert("\n")
            mask != null && action == TextEditAction.WordLeft -> state.apply(TextEditAction.Home)
            mask != null && action == TextEditAction.WordRight -> state.apply(TextEditAction.End)
            else -> state.apply(action)
        }
    }

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.isConsumed) return
        syncState()
        when (event.type) {
            PointerEventType.Press -> {
                clicks.onPress(state, mask, event, hitIndexAt(event), clock.totalSeconds)
                event.consume()
            }
            PointerEventType.Release -> {
                clicks.onRelease(state, event)
                onClick?.invoke()
            }
            PointerEventType.Move -> if (event.isCaptureHolder) {
                clicks.onCapturedMove(state, mask, event, hitIndexAt(event))
                event.consume()
            } else {
                clicks.onHoverMove(event)
            }
            PointerEventType.Exit -> clicks.onExit(event)
            else -> Unit
        }
    }

    private fun hitIndexAt(event: PointerEvent): Int =
        policy.runFor(density, policy.measuredFontScale).indexAt(
            x = (event.x + if (singleLine) horizontalOffset.value else 0).toFloat(),
            y = event.y.toFloat(),
        )

    override fun toString(): String = "textInput(cursor=${state.cursor})"
}

/** Keys this component's `remember` slots as one group in its caller's node. */
private object BasicTextFieldGroup
