/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.animation.rememberLoopingPhase
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.foundation.text.rememberTextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's `InputOTP`: accessible one-time password component with live keyboard input.
 *
 * Each slot is `size-9` (36dp). Slots inside a group are joined with seamless 1px [BorderSides]
 * and outer corner rounding (`first:rounded-l-md last:rounded-r-md`), with [ShadcnInputOtpSeparator]
 * rendered between groups.
 *
 * [length] is upstream's `maxLength`, and it is a limit on the value rather than on what is shown.
 * Displaying `text.take(length)` while the field kept the rest looked like a working cap and was
 * not one: a seventh digit was still in the value, invisible, and the next two backspaces appeared
 * to do nothing because they were deleting characters that had never been drawn.
 *
 * [pattern] is upstream's prop of the same name, and like it defaults to accepting anything --
 * one-time codes are not always numeric. Pass [ShadcnInputOtpPatterns.Digits] for the numeric
 * field that shadcn's own example builds.
 */
context(_: Composer)
fun ShadcnInputOtp(
    state: TextFieldState,
    length: Int = 6,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    groupSize: Int = 0,
    pattern: Regex? = null,
) {
    val theme = shadcnTheme
    val radius = theme.radii.md
    val interaction = remember { InteractionSource() }
    val isFocused = interaction.isFocused
    // Hoisted rather than read inside the active slot: `remember` is keyed by call site, and that
    // call site only exists while a slot is active.
    val caretPhase = rememberLoopingPhase(CARET_BLINK_SECONDS)
    val text = state.acceptOtp(length, pattern)

    val groups = if (groupSize > 0 && groupSize < length) {
        val count = (length + groupSize - 1) / groupSize
        (0 until count).map { g ->
            val start = g * groupSize
            val end = (start + groupSize).coerceAtMost(length)
            start until end
        }
    } else {
        listOf(0 until length)
    }

    Box(
        // `has-disabled:opacity-50`: upstream dims the control and leaves it otherwise identical.
        // Swapping the slot fill to `muted` instead turned it into a grey slab that shared no
        // colour with the enabled state.
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { this[SemanticsProperties.Label] = text },
    ) {
        // Visual slots rendering
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            groups.forEachIndexed { groupIndex, slotIndices ->
                if (groupIndex > 0) {
                    ShadcnInputOtpSeparator()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val groupCount = slotIndices.count()
                    slotIndices.forEachIndexed { indexInGroup, slotIndex ->
                        val char = if (slotIndex < text.length) text[slotIndex].toString() else ""
                        val isActive = isFocused && enabled && slotIndex == text.length.coerceAtMost(length - 1)
                        val shape = slotShape(indexInGroup, groupCount, radius)
                        val borderSides = slotBorderSides(indexInGroup)
                        val borderColor = if (isError) {
                            theme.palette.destructive
                        } else if (isActive) {
                            theme.palette.ring
                        } else {
                            theme.palette.input
                        }
                        Box(
                            Modifier
                                .size(SLOT_SIZE)
                                .let { if (isActive) it.zIndex(1f) else it.zIndex(0f) }
                                // `data-[active=true]:ring-[3px]` with `ring-ring/50`, drawn outside
                                // the slot. The border itself stays 1px and only changes colour --
                                // thickening it to 2px instead made the active slot read as a
                                // heavier box than its neighbours rather than a highlighted one.
                                // The slot's own shape, not one radius: a first or last slot is
                                // rounded on its outer side and square where it meets its
                                // neighbour, and a uniform ring drew it as a detached rounded box
                                // floating above the row.
                                .focusRing(
                                    alpha = if (isActive) 1f else 0f,
                                    width = FieldFocusRingWidth,
                                    color = theme.palette.ring.scaleAlpha(RING_ALPHA),
                                    shape = shape,
                                )
                                .border(
                                    width = SLOT_BORDER_WIDTH,
                                    color = borderColor,
                                    shape = shape,
                                    // Unchanged when active. Upstream's `data-[active=true]` rules
                                    // change the border's *colour* and add the ring; switching a
                                    // middle slot to all four sides drew edges its neighbours
                                    // already own and broke the seam the group is made of.
                                    sides = borderSides,
                                )
                                // Upstream only adds `dark:bg-input/30`; light mode leaves the
                                // slot transparent so the border and page surface stay clean.
                                .let {
                                    if (theme.config.dark) {
                                        it.background(color = theme.palette.input.scaleAlpha(SLOT_FILL_ALPHA), shape = shape)
                                    } else {
                                        it
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (char.isNotEmpty()) {
                                ShadcnText(
                                    char,
                                    variant = ShadcnTextVariant.Small,
                                    color = theme.palette.foreground,
                                )
                            } else if (isActive && caretPhase < CARET_VISIBLE_FRACTION) {
                                // `h-4 w-px`, on `animate-caret-blink`'s duty cycle: lit for the
                                // first 70% of each second, dark for the rest.
                                Box(
                                    Modifier
                                        .size(width = CARET_WIDTH, height = CARET_HEIGHT)
                                        .background(theme.palette.foreground),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Focus & Keyboard input overlay (top layer so it directly captures clicks across the entire box)
        // matchParentSize, not fillMaxSize: the latter takes the incoming maximum, so the Box
        // reported its parent's whole area and an enabled field measured 300x60 where a disabled one
        // measured 216x36 -- the control's own size depended on whether it was editable, and its
        // click target covered the entire parent. Upstream's real input is `absolute inset-0`.
        if (enabled) {
            BasicTextField(
                state = state,
                modifier = Modifier.matchParentSize().zIndex(10f),
                style = TextStyle(color = Color(0f, 0f, 0f, 0f)),
                interactionSource = interaction,
                singleLine = true,
            )
        }
    }
}

/** Tailwind's `/N` opacity modifier: scales what alpha the colour already has. */
private fun Color.scaleAlpha(fraction: Float): Color = withAlpha(a * fraction)

/**
 * Value-driven overload for [ShadcnInputOtp].
 */
context(_: Composer)
fun ShadcnInputOtp(
    value: String,
    length: Int = 6,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    groupSize: Int = 0,
    pattern: Regex? = null,
    onValueChange: (String) -> Unit = {},
) {
    val state = rememberTextFieldState(value)
    // Clamped before the comparison, not after: the state is what the field wrote, so an
    // over-long or rejected keystroke would otherwise report as a change and then be dropped.
    if (state.acceptOtp(length, pattern) != value) {
        onValueChange(state.text)
    }
    ShadcnInputOtp(
        state = state,
        length = length,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        groupSize = groupSize,
        pattern = pattern,
    )
}

/**
 * Drops whatever the field accepted that this OTP does not, and returns what is left.
 *
 * Enforced against the state rather than by filtering at the draw: the field owns the value, so a
 * cap applied only where slots are drawn leaves rejected characters in it.
 */
private fun TextFieldState.acceptOtp(length: Int, pattern: Regex?): String {
    val accepted = text
        .filter { pattern == null || pattern.matches(it.toString()) }
        .take(length)
    // The cursor follows to the end: an OTP is only ever appended to.
    if (accepted != text) setText(accepted)
    return accepted
}

/** The alphabets shadcn's own examples pass to `pattern`. */
object ShadcnInputOtpPatterns {
    val Digits: Regex = Regex("[0-9]")
    val Chars: Regex = Regex("[a-zA-Z]")
    val DigitsAndChars: Regex = Regex("[a-zA-Z0-9]")
}

/**
 * Hairline separator / dash between OTP slot groups.
 */
context(_: Composer)
fun ShadcnInputOtpSeparator(modifier: Modifier = Modifier) {
    // Upstream renders lucide's `MinusIcon` -- a centred stroked line. A "-" glyph sits on the text
    // baseline rather than the slot's centre, and its length and weight follow the font.
    Box(
        modifier.size(SEPARATOR_BOX),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = SEPARATOR_LINE_LENGTH, height = SEPARATOR_LINE_WEIGHT)
                .background(shadcnTheme.palette.mutedForeground),
        )
    }
}

private fun slotShape(index: Int, count: Int, radius: Dp): Shape {
    if (count <= 1) return RoundedCornerShape(radius)
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) radius else 0.dp,
        topEnd = if (last) radius else 0.dp,
        bottomEnd = if (last) radius else 0.dp,
        bottomStart = if (first) radius else 0.dp,
    )
}

private fun slotBorderSides(index: Int): BorderSides = BorderSides(
    top = true,
    end = true,
    bottom = true,
    start = index == 0,
)

/** `size-9`. */
private val SLOT_SIZE = 36.dp

/** `border` is 1px in every state; only its colour changes. */
private val SLOT_BORDER_WIDTH = 1.dp

/**
 * `dark:bg-input/30`.
 *
 * Tailwind's `/N` is `color-mix(in oklab, <colour> N%, transparent)`, which *scales* the colour's
 * existing alpha rather than replacing it. `--input` is already white at 15%, so `bg-input/30` is
 * white at 4.5% -- a barely-there lift. Setting the alpha to 0.3 outright, which is what a
 * `withAlpha` reads like it should do, painted the slots at 83/255 over a 10/255 page: a grey box
 * where upstream has a hint.
 */
private const val SLOT_FILL_ALPHA = 0.3f

/** `ring-ring/50`. */
private const val RING_ALPHA = 0.5f

/** `opacity-50`. */
private const val DISABLED_ALPHA = 0.5f

/** `h-4 w-px`. */
private val CARET_WIDTH = 1.dp
private val CARET_HEIGHT = 16.dp

/** `animate-caret-blink duration-1000`, lit for the first 70% of each cycle. */
private const val CARET_BLINK_SECONDS = 1f
private const val CARET_VISIBLE_FRACTION = 0.7f

/** lucide's 24px icon box, with `M5 12h14` at `stroke-width: 2`. */
private val SEPARATOR_BOX = 24.dp
private val SEPARATOR_LINE_LENGTH = 14.dp
private val SEPARATOR_LINE_WEIGHT = 2.dp
