// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.headless.internal.controls

import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.ui.font
import io.github.ronjunevaldoz.awake.ui.theme
import io.github.ronjunevaldoz.awake.ui.UiSemanticRole
import io.github.ronjunevaldoz.awake.ui.UiShape
import io.github.ronjunevaldoz.awake.core.math2d.Dp
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.canvas
import io.github.ronjunevaldoz.awake.ui.foundation.UiToggleableState
import io.github.ronjunevaldoz.awake.ui.foundation.toggled
import io.github.ronjunevaldoz.awake.ui.graphics.drawCheckmark
import io.github.ronjunevaldoz.awake.ui.graphics.drawInsetDash
import io.github.ronjunevaldoz.awake.ui.graphics.drawRadioDot
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.paintSurface
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.resolveInteractiveSurface
import io.github.ronjunevaldoz.awake.ui.headless.internal.layout.withIntrinsicLabelWidth
import io.github.ronjunevaldoz.awake.ui.foundation.text.UiTextOverflow
import io.github.ronjunevaldoz.awake.ui.foundation.text.text
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier
import io.github.ronjunevaldoz.awake.ui.modifier.UiModifier
import io.github.ronjunevaldoz.awake.ui.modifier.withSizeFallback
import io.github.ronjunevaldoz.awake.ui.scope.recordSemantic
import io.github.ronjunevaldoz.awake.ui.style.Style
import io.github.ronjunevaldoz.awake.core.math2d.toPx
import io.github.ronjunevaldoz.awake.ui.headless.withDisabledAlpha

// Dp, not raw px: every coordinate it is added to below (`boxPx`, `surface.interaction.slot`)
// already went through `.dp.toPx()`, so a raw literal here would stay 8 physical pixels while
// everything around it doubled on a 2x display -- a visually half-size gap.
private val CHECKBOX_LABEL_GAP = 8f.dp

// A real iOS-style switch, not a stretched checkbox -- fixed compact size (a switch has one
// natural size, unlike a button/row that should fill available width), pill-shaped track, and
// a sliding circular knob instead of checkbox's centered inset-square "check" mark.

fun UiPrimitiveScope.checkbox(
    id: String,
    checked: Boolean,
    label: String? = null,
    modifier: UiModifier = Modifier,
    style: Style = Style.Empty,
    boxSize: Dp = 16f.dp,
    indeterminate: Boolean = false,
    enabled: Boolean = true,
): Boolean {
    val theme = theme
    // Reads no ambient theme -- real callers (shadcnCheckbox via shadcnCheckboxStyle) already
    // supply a complete themed Style, including textSize (added there specifically so this
    // default's removal wouldn't silently grow the label to the ambient body text size).
    val defaults = Style.Empty
    val sizedModifier = modifier.withSizeFallback(
        label?.let {
            withIntrinsicLabelWidth(
                modifier = modifier,
                label = it,
                style = style,
                defaults = defaults,
                extraWidth = boxSize + CHECKBOX_LABEL_GAP,
            ).widthDimension
        } ?: Dimension.FillMax,
        Dimension.Fixed(24f.dp),
    )
    val surface = resolveInteractiveSurface(
        id = id,
        modifier = sizedModifier,
        style = style,
        defaults = defaults,
        selected = checked,
        disabled = !enabled,
        enabled = enabled,
    )
    val boxPx = boxSize.toPx()
    val boxSlot = Rectangle(
        surface.interaction.slot.x,
        surface.interaction.slot.y + (surface.interaction.slot.height - boxPx) / 2f,
        boxPx,
        boxPx,
    )
    // Reference's `disabled:opacity-50` treatment, same single group-alpha shape as
    // `Buttons.kt`'s `buttonSlotInternal` -- covers the box fill/border, the check/dash mark,
    // and the label as one composited unit so the label drawn on top of the box's own paint
    // never gets double-dimmed.
    return withDisabledAlpha(enabled) {
        paintSurface(
            slot = boxSlot,
            resolved = if (checked || indeterminate) {
                // CSS checked state paints the same primary surface through the border; an
                // inset one-pixel border would shrink the black fill and produce a visibly
                // smaller indicator than the reference.
                surface.resolved.copy(borderWidth = UiShape.none)
            } else {
                surface.resolved
            },
            // resolved.background/borderColor already reflect the caller's own checked-state
            // colors (shadcnCheckboxStyle sets both per `checked`); the theme token is only a
            // fallback for a bare-Style.Empty caller, not a hardcoded override that would ignore
            // a caller's own Style -- see skills/awake-ui-authoring's headless-Style-only rule.
            fillColor = surface.resolved.background
                ?: theme.colors.primary.takeIf { checked || indeterminate },
            borderColor = surface.resolved.borderColor
                ?: theme.colors.primary.takeIf { checked || indeterminate },
        )
        // triStateToggleable: clicking Indeterminate lands on checked, same as clicking Off --
        // only On flips to false. The transition lives in ui-core's foundation layer so the
        // three controls that toggle cannot drift apart again.
        val toggleState = when {
            indeterminate -> UiToggleableState.Indeterminate
            checked -> UiToggleableState.On
            else -> UiToggleableState.Off
        }
        val newChecked = surface.interaction.toggled(toggleState) == UiToggleableState.On
        val inset = boxPx * 0.25f
        // The indicator is the resolved foreground, same as the fill/border above take the
        // resolved background/borderColor. These read the theme directly, which no skin could
        // override -- a caller supplying a complete Style still got a theme-coloured mark.
        // The theme token stays as a fallback for a bare-Style.Empty caller.
        if (indeterminate) {
            val dashColor = surface.resolved.foreground ?: theme.colors.primary
            canvas(boxSlot) { drawInsetDash(boxSlot, inset, dashColor) }
        } else if (newChecked) {
            val markColor = surface.resolved.foreground ?: theme.colors.primaryForeground
            canvas(boxSlot) { drawCheckmark(boxSlot, markColor) }
        }
        val resolvedFont = font
        if (label != null) {
            val gapPx = CHECKBOX_LABEL_GAP.toPx()
            val labelSlot = Rectangle(
                boxSlot.x + boxPx + gapPx,
                surface.interaction.slot.y,
                surface.interaction.slot.width - boxPx - gapPx,
                surface.interaction.slot.height,
            )
            text(
                label,
                slot = labelSlot,
                font = resolvedFont,
                // The resolved foreground styles the indicator itself. The caption is a
                // separate content node and must retain the normal text color when the box is
                // selected; using primaryForeground here made a checked checkbox's label white
                // on the page background.
                color = if (enabled) theme.colors.foreground else theme.colors.mutedForeground,
                centered = false,
                verticallyCentered = true,
                overflow = UiTextOverflow.Ellipsis,
                textStyle = surface.resolved.textStyle,
                semanticId = "$id.label",
            )
        }
        recordSemantic(
            role = UiSemanticRole.Checkbox,
            id = id,
            label = label,
            bounds = surface.interaction.slot,
            contentBounds = boxSlot,
            selected = newChecked,
            indeterminate = indeterminate,
        )
        newChecked
    }
}

/**
 * Radio indicator primitive. Radio buttons share checkbox interaction semantics, but their
 * selected affordance is a centered dot; reusing [checkbox] here rendered a checkmark in a
 * circle, which is visibly different from shadcn's `RadioGroupItem`.
 */
fun UiPrimitiveScope.radio(
    id: String,
    selected: Boolean,
    modifier: UiModifier = Modifier,
    style: Style = Style.Empty,
    enabled: Boolean = true,
    boxSize: Dp = 16f.dp,
): Boolean {
    val theme = theme
    val surface = resolveInteractiveSurface(
        id = id,
        // A shadcn RadioGroupItem is `size-4`, not a full-width checkbox row. Its authored
        // label is a sibling in the caller's `flex items-center gap-2` row, so claiming the
        // remaining width here makes both the semantic crop and the actual layout diverge.
        modifier = modifier.withSizeFallback(Dimension.Fixed(boxSize), Dimension.Fixed(boxSize)),
        style = style,
        // radio() has no label to paint, so unlike checkbox() there is no textSize concern --
        // shadcnRadio's shadcnRadioStyle already supplies a complete background/border/shape.
        defaults = Style.Empty,
        selected = selected,
        disabled = !enabled,
        enabled = enabled,
    )
    val boxPx = boxSize.toPx()
    val boxSlot = Rectangle(
        surface.interaction.slot.x,
        surface.interaction.slot.y + (surface.interaction.slot.height - boxPx) / 2f,
        boxPx,
        boxPx,
    )
    return withDisabledAlpha(enabled) {
        paintSurface(
            slot = boxSlot,
            resolved = surface.resolved.copy(shapeSpec = io.github.ronjunevaldoz.awake.core.graphics2d.UiShapeSpec.Circle),
            fillColor = surface.resolved.background ?: theme.colors.background,
            borderColor = surface.resolved.borderColor ?: theme.colors.border,
            shapeSpec = io.github.ronjunevaldoz.awake.core.graphics2d.UiShapeSpec.Circle,
        )
        if (selected) {
            val dotColor = surface.resolved.foreground ?: theme.colors.primary
            canvas(boxSlot) { drawRadioDot(boxSlot, dotColor) }
        }
        val next = if (surface.interaction.clicked && enabled) true else selected
        recordSemantic(
            role = UiSemanticRole.Radio,
            id = id,
            bounds = surface.interaction.slot,
            contentBounds = boxSlot,
            selected = next,
        )
        next
    }
}
