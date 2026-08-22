// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.headless.internal.controls

import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.ui.font
import io.github.ronjunevaldoz.awake.ui.theme
import io.github.ronjunevaldoz.awake.ui.UiSemanticRole
import io.github.ronjunevaldoz.awake.ui.UiShape
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.canvas
import io.github.ronjunevaldoz.awake.ui.fitTo
import io.github.ronjunevaldoz.awake.ui.headless.UiPopupDefaults
import io.github.ronjunevaldoz.awake.ui.headless.button
import io.github.ronjunevaldoz.awake.ui.headless.internal.layout.withIntrinsicLabelWidth
import io.github.ronjunevaldoz.awake.ui.foundation.text.UiTextOverflow
import io.github.ronjunevaldoz.awake.ui.foundation.text.text
import io.github.ronjunevaldoz.awake.ui.layouts.Arrangement
import io.github.ronjunevaldoz.awake.ui.layouts.ColumnScope
import io.github.ronjunevaldoz.awake.ui.layouts.surface
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier
import io.github.ronjunevaldoz.awake.ui.modifier.fillMaxWidth
import io.github.ronjunevaldoz.awake.ui.modifier.UiModifier
import io.github.ronjunevaldoz.awake.ui.modifier.height
import io.github.ronjunevaldoz.awake.ui.modifier.width
import io.github.ronjunevaldoz.awake.ui.popup
import io.github.ronjunevaldoz.awake.core.math2d.px
import io.github.ronjunevaldoz.awake.ui.rememberPopupState
import io.github.ronjunevaldoz.awake.ui.scope.recordSemantic
import io.github.ronjunevaldoz.awake.ui.scope.resolveStyle
import io.github.ronjunevaldoz.awake.ui.style.MutableStyleState
import io.github.ronjunevaldoz.awake.ui.style.Style
import io.github.ronjunevaldoz.awake.core.math2d.toPx
import io.github.ronjunevaldoz.awake.ui.headless.withDisabledAlpha

// Real shadcn/ui slider shape: a thin track (not a full-height button-like bar) with a
// circular knob straddling it at the current value -- the claimed slot stays the full
// hit-test/hover target (so dragging doesn't require pixel-precise aim at a thin line), but
// only a slice of it is painted as the track, and the knob is drawn on top, not "no knob at
// all" (the previous version only drew a flat fill rectangle with no handle).

fun UiPrimitiveScope.select(
    id: String,
    options: List<String>,
    selectedIndex: Int,
    modifier: UiModifier = Modifier,
    style: Style = Style.Empty,
    selectedStyle: Style? = null,
    optionStyle: Style? = null,
    // The panel behind the option rows. Without one the popup paints no background, border or
    // radius and its rows float over whatever is beneath them -- see combobox's matching note.
    //
    // Null means no panel at all, not an unstyled one: `surface()` falls back to
    // `neutralSurfaceDefaults`, which carries a background and 8dp of padding, so wrapping
    // unconditionally would hand every caller who asked for nothing a grey panel and an inset
    // it never requested. Supplying visual policy nobody asked for is exactly what this layer
    // must not do.
    surfaceStyle: Style? = null,
    enabled: Boolean = true,
    placeholder: String = "",
): Int? {
    val theme = theme
    val expandedState = rememberPopupState(id, key = "expanded")
    // Reads no ambient theme -- shadcnSelect's caller-supplied style (shadcnTextFieldStyle)
    // already resolves a complete background/border/shape/contentPadding/textSize.
    val resolvedDefaults = Style.Empty
    val selectedLabel = options.getOrNull(selectedIndex) ?: placeholder
    val triggerStyle = resolvedDefaults then style
    // A SelectContent item is a menu row, not another SelectTrigger. Keep the trigger's
    // border/radius/padding out of the option path unless the skin explicitly supplies one.
    val resolvedOptionStyle = optionStyle ?: Style.Companion {
        background(theme.colors.popover)
        foreground(theme.colors.popoverForeground)
        borderWidth(0f.dp)
        shape(UiShape.none)
        textSize(theme.typography.label)
    }
    val triggerModifier = withIntrinsicLabelWidth(
        modifier = modifier,
        label = selectedLabel,
        style = Style.Empty,
        defaults = triggerStyle,
        // drawDropdownTriggerContent reserves a 16dp chevron and an 8dp gap in addition to
        // the trigger's horizontal content padding.
        extraWidth = 24f.dp,
    )
    val (clicked, slot) = buttonSlot(
        id = "$id.trigger",
        modifier = triggerModifier.height(modifier.heightDimension ?: Dimension.Fixed(36f.dp)),
        style = triggerStyle,
        enabled = enabled,
    )
    if (clicked) {
        expandedState.toggle()
    }
    // buttonSlot's own disabled-dim already covers the trigger's fill/border (see
    // buttonSlotInternal); this widget's label/chevron paint on top of that fill separately
    // below, so they need their own matching group-alpha rather than sharing buttonSlot's
    // (that would compound into a double dim, `disabled` becoming ~0.25 opacity not 0.5).
    withDisabledAlpha(enabled) {
        drawDropdownTriggerContent(
            slot = slot,
            label = selectedLabel,
            expanded = expandedState.expanded,
            style = resolvedDefaults then style,
            semanticId = "$id.label",
            isPlaceholder = selectedIndex !in options.indices,
        )
    }
    recordSemantic(
        role = UiSemanticRole.Dropdown,
        id = id,
        label = selectedLabel,
        bounds = slot,
        selected = expandedState.expanded,
    )
    var picked: Int? = null
    val popupResult = popup(
        id = id,
        anchorSlot = slot,
        expanded = expandedState.expanded,
        width = Dimension.Fixed(slot.width.px),
        height = Dimension.WrapContent,
        verticalArrangement = Arrangement.spacedBy(0f.dp),
        // Same gap the dropdown MENU already used (Dropdown.kt's own `dropdown(offsetY = 4f.dp)`).
        // shadcn gives both the same 4px: DropdownMenuContent via `sideOffset = 4`, SelectContent
        // via `data-[side=bottom]:translate-y-1`. Without it the select's panel sat flush against
        // its trigger while every sibling popup floated.
        positionProvider = UiPopupDefaults.dropdown(offsetY = 4f.dp),
    ) {
        val optionRows: ColumnScope.() -> Unit = {
            options.forEachIndexed { index, option ->
                val selectedOptionStyle = if (index == selectedIndex) {
                    selectedStyle ?: Style.Companion {
                        background(theme.colors.accent)
                        foreground(theme.colors.accentForeground)
                    }
                } else {
                    Style.Empty
                }
                if (
                    button(
                        id = "$id.option$index",
                        label = option,
                        modifier = Modifier
                            // Fill the panel, not the trigger. Pinning these to the trigger's
                            // own width overran the surface, whose interior is narrower by its
                            // padding and border, so a selected row's highlight bled past the
                            // rounded edge.
                            .fillMaxWidth()
                            // SelectContent rows are independent menu items. They must not
                            // inherit the trigger's 36dp height; shadcn's `py-1.5 text-sm`
                            // row is 32dp at the default metrics.
                            .height(32f.dp),
                        style = resolvedOptionStyle then selectedOptionStyle,
                        // Start-aligned like menuItem -- see its note on button()'s centered
                        // default.
                        centered = false,
                        semanticRole = UiSemanticRole.MenuItem,
                    )
                ) {
                    picked = index
                }
            }
        }
        if (surfaceStyle != null) {
            surface(id = "$id.surface", modifier = Modifier.fillMaxWidth(), style = surfaceStyle) { optionRows() }
        } else {
            optionRows()
        }
    }
    if (popupResult.dismissed) {
        expandedState.close()
    }
    if (picked != null) {
        expandedState.close()
    }
    return picked
}

/** Select-trigger content: label left-aligned, expand chevron right-aligned -- matches the
 * real shadcn/ui Select trigger shape, not a big centered label ([buttonSlot]'s default).
 * Public so design-system layers building their own custom dropdown trigger (e.g. one that
 * also needs a popup menu shaped differently from [select]'s own) can reuse the same
 * label/chevron layout instead of re-deriving it. */
fun UiPrimitiveScope.drawDropdownTriggerContent(
    slot: Rectangle,
    label: String,
    expanded: Boolean,
    style: Style,
    semanticId: String? = null,
    // True when [label] is a placeholder rather than a real selected value (shadcnSelect's
    // `selectedIndex == null` case) -- muted, same treatment textField gives its placeholder.
    isPlaceholder: Boolean = false,
) {
    val theme = theme
    val resolvedFont = font
    val resolved = resolveStyle(
        defaults = style,
        state = MutableStyleState(
            hovered = hitTest(slot),
            active = expanded,
        ),
    )
    val textColor = if (isPlaceholder) {
        theme.colors.mutedForeground
    } else {
        (
            resolved.foreground
                ?: theme.colors.foreground
            )
    }
    // Authored in Dp and converted here, at the point of use. `slot` *is* physical-pixel
    // space, but it got there by density-scaling: every Dimension.Fixed resolves through
    // `.dp.toPx()`, and a fill width is the real framebuffer width. So the padding subtracted
    // from it has to be density-scaled too, or it stays 12 physical pixels while the trigger
    // it sits inside doubles -- a visually half-size inset at 2x. (This replaces an older
    // "raw px, not Dp" comment that predates widgets taking Dimension/Dp instead of literal
    // `width: Float` pixel params; back then `slot` really could arrive unscaled.)
    val horizontalPad = 12f.dp.toPx()
    val chevronGap = 8f.dp.toPx()
    val chevronSize = 16f.dp.toPx()
    text(
        label,
        slot = Rectangle(
            x = slot.x + horizontalPad,
            y = slot.y,
            width = (slot.width - horizontalPad * 2 - chevronSize - chevronGap).coerceAtLeast(0f),
            height = slot.height,
        ),
        font = resolvedFont,
        color = textColor,
        centered = false,
        verticallyCentered = true,
        overflow = UiTextOverflow.Ellipsis,
        textStyle = resolved.textStyle,
        semanticId = semanticId,
    )
    val chevronSlot = Rectangle(
        x = slot.x + slot.width - horizontalPad - chevronSize,
        y = slot.y + (slot.height - chevronSize) / 2f,
        width = chevronSize,
        height = chevronSize,
    )
    val chevronColor = textColor.withAlpha(0.5f)
    UiIcons.chevronDown.fitTo(chevronSlot).forEach { vectorPath ->
        // fitTo(chevronSlot) already resolved vectorPath.path to absolute coordinates -- see
        // Icon.kt's identical canvas(Rectangle(0f, 0f, 0f, 0f)) pattern for why the zero-size slot
        // keeps CanvasScope.fillPath's own bounds-translate a no-op.
        canvas(Rectangle(0f, 0f, 0f, 0f)) {
            fillPath(vectorPath.path, vectorPath.fill ?: chevronColor)
        }
    }
}
