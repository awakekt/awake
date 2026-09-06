/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.defaultMinSize
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.disabled
import com.awakekt.awake.compose.foundation.style.hovered
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.vector.ImageVector
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's toggle group: a strip of two-state buttons.
 *
 * **Not [ShadcnButtonGroup].** Upstream's own rule, stated in the ButtonGroup docs: a button group
 * groups buttons that *perform an action*, a toggle group groups buttons that *toggle a state*.
 *
 * Joined ends and seamless 1px dividers are drawn when [spacing] is 0 (upstream's default):
 * outer corners are rounded via [memberShape] while inner corners are squared, and outline
 * items drop their duplicate start borders via [memberBorderSides]. When [spacing] is greater
 * than 0, each item draws its own 4-corner rounded shape and full border.
 */
context(_: Composer)
fun ShadcnToggleGroup(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ShadcnButtonGroupOrientation = ShadcnButtonGroupOrientation.Horizontal,
    selection: ShadcnToggleGroupSelection = ShadcnToggleGroupSelection.Multiple,
    variant: ShadcnToggleGroupVariant = ShadcnToggleGroupVariant.Default,
    size: ShadcnToggleGroupSize = ShadcnToggleGroupSize.Default,
    spacing: Dp = 0.dp,
    content: ShadcnToggleGroupScope.() -> Unit,
) {
    val items = remember(content) { ShadcnToggleGroupScope().apply(content).items }
    // One value rather than three parallel arguments threaded to every item: they are the group's
    // appearance and behaviour, and an item never varies one without the others.
    val spec = ToggleGroupSpec(selection, variant, size, orientation, spacing)
    val arrangement = Arrangement.spacedBy(spacing)
    val group = modifier.semantics {
        // What makes a strip of radios *one* choice rather than several independent ones.
        if (selection == ShadcnToggleGroupSelection.Single) {
            this[SemanticsProperties.SelectableGroup] = true
        }
    }
    val count = items.size
    when (orientation) {
        ShadcnButtonGroupOrientation.Horizontal -> Row(
            group,
            horizontalArrangement = Arrangement.spacedByHorizontal(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                ToggleGroupItem(item, index, count, selected, spec, onSelectedChange)
            }
        }

        ShadcnButtonGroupOrientation.Vertical -> Column(group, verticalArrangement = arrangement) {
            items.forEachIndexed { index, item ->
                ToggleGroupItem(item, index, count, selected, spec, onSelectedChange)
            }
        }
    }
}

/**
 * The single-select spelling: one value, or none.
 *
 * Deselecting the active item is allowed, matching Radix. A caller for whom empty is meaningless --
 * a modal tool picker -- keeps the old value in its own handler, because "there is always a tool"
 * is a statement about that screen and not about toggle groups.
 */
context(_: Composer)
fun ShadcnToggleGroup(
    selected: String?,
    onSelectedChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ShadcnButtonGroupOrientation = ShadcnButtonGroupOrientation.Horizontal,
    variant: ShadcnToggleGroupVariant = ShadcnToggleGroupVariant.Default,
    size: ShadcnToggleGroupSize = ShadcnToggleGroupSize.Default,
    spacing: Dp = 0.dp,
    content: ShadcnToggleGroupScope.() -> Unit,
) = ShadcnToggleGroup(
    selected = selected?.let { setOf(it) } ?: emptySet(),
    onSelectedChange = { onSelectedChange(it.firstOrNull()) },
    modifier = modifier,
    orientation = orientation,
    selection = ShadcnToggleGroupSelection.Single,
    variant = variant,
    size = size,
    spacing = spacing,
    content = content,
)

/** Whether one item may be on at a time, or any number. */
enum class ShadcnToggleGroupSelection { Single, Multiple }

/** `variant="outline"` draws a border; the default is a fill on the on-state only. */
enum class ShadcnToggleGroupVariant { Default, Outline }

/** Upstream's `sm`/`default`/`lg` -- `h-8`, `h-9`, `h-10`. */
enum class ShadcnToggleGroupSize(internal val extent: Dp) {
    Sm(32.dp),
    Default(36.dp),
    Lg(40.dp),
}

/** One item in a [ShadcnToggleGroup]. */
class ShadcnToggleGroupItem internal constructor(
    val value: String,
    /** Named even when only an icon is drawn: it is what a reader hears and what a test finds. */
    val label: String,
    val icon: ImageVector?,
    val enabled: Boolean,
    val tag: String?,
    val content: (
        context(Composer)
        () -> Unit
    )?,
)

/** Declares the items of a [ShadcnToggleGroup], in order. */
@ShadcnToggleGroupDsl
class ShadcnToggleGroupScope internal constructor() {
    internal val items = mutableListOf<ShadcnToggleGroupItem>()

    fun item(
        value: String,
        label: String,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        tag: String? = null,
        content: (
            context(Composer)
            () -> Unit
        )? = null,
    ) {
        items += ShadcnToggleGroupItem(value, label, icon, enabled, tag, content)
    }
}

@DslMarker
annotation class ShadcnToggleGroupDsl

/** What every item in one group shares. */
private class ToggleGroupSpec(
    val selection: ShadcnToggleGroupSelection,
    val variant: ShadcnToggleGroupVariant,
    val size: ShadcnToggleGroupSize,
    val orientation: ShadcnButtonGroupOrientation,
    val spacing: Dp,
)

context(_: Composer)
private fun ToggleGroupItem(
    item: ShadcnToggleGroupItem,
    index: Int,
    count: Int,
    selected: Set<String>,
    spec: ToggleGroupSpec,
    onSelectedChange: (Set<String>) -> Unit,
) {
    val selection = spec.selection
    val variant = spec.variant
    val size = spec.size
    val orientation = spec.orientation
    val spacing = spec.spacing
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = item.enabled)
    val on = item.value in selected

    val shape = if (spacing == 0.dp) {
        memberShape(orientation, index, count, theme.radii.md)
    } else {
        RoundedCornerShape(theme.radii.md)
    }
    val borderSides = if (spacing == 0.dp && variant == ShadcnToggleGroupVariant.Outline) {
        memberBorderSides(orientation, index, count)
    } else {
        BorderSides.All
    }
    val style = remember(theme, Triple(on, variant, shape)) {
        theme.toggleGroupItemStyle(on, variant, shape)
    }

    // An item that draws a glyph instead of its label is only discoverable on hover, so it gets a
    // tooltip; one that draws its own text already says what it is. The rule is exact, so it is
    // applied rather than left to a flag every caller would have to remember.
    val labelled = item.icon == null && item.content == null
    val sizingModifier = if (labelled) {
        Modifier.defaultMinSize(minWidth = size.extent).height(size.extent)
            .padding(horizontal = Tw.Spacing.s2_5)
    } else {
        Modifier.size(size.extent)
    }
    TooltipIf(!labelled, item.label) {
        Box(
            sizingModifier
                .hoverable(interaction, enabled = item.enabled)
                .clickable(interaction) {
                    if (item.enabled) onSelectedChange(selected.toggling(item.value, selection))
                }
                .styleable(styleState, borderSides, style)
                .semantics {
                    // A single-select item is a radio, not a button that happens to look pressed.
                    this[SemanticsProperties.Role] = when (selection) {
                        ShadcnToggleGroupSelection.Single -> SemanticsRole.RadioButton
                        ShadcnToggleGroupSelection.Multiple -> SemanticsRole.Button
                    }
                    this[SemanticsProperties.Label] = item.label
                    this[SemanticsProperties.Selected] = on
                    if (!item.enabled) this[SemanticsProperties.Disabled] = true
                }
                .let { if (item.tag == null) it else it.testTag(item.tag) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val body = item.content
            val tint = if (on) theme.palette.accentForeground else theme.palette.foreground
            when {
                body != null -> body()
                item.icon != null -> ShadcnIcon(item.icon, tint = tint)
                else -> ShadcnText(item.label, variant = ShadcnTextVariant.Small, color = tint)
            }
        }
    }
}

/**
 * [content] with a tooltip, or plainly when [enabled] is false.
 *
 * A branch rather than a nullable label because the two arms must declare a different number of
 * nodes: identity here is positional, so wrapping conditionally inside one arm would renumber
 * every slot beneath it the moment the condition changed.
 */
context(_: Composer)
private fun TooltipIf(enabled: Boolean, text: String, content: context(Composer) () -> Unit) {
    if (enabled) ShadcnTooltipped(text) { content() } else content()
}

/**
 * The next selection after [value] is clicked.
 *
 * [ShadcnToggleGroupSelection.Single] replaces rather than adds, and clicking the active item
 * clears it -- Radix's behaviour, and the reason the single-select overload's callback is nullable.
 */
private fun Set<String>.toggling(
    value: String,
    selection: ShadcnToggleGroupSelection,
): Set<String> = when {
    value in this && selection == ShadcnToggleGroupSelection.Single -> emptySet()
    value in this -> this - value
    selection == ShadcnToggleGroupSelection.Single -> setOf(value)
    else -> this + value
}

private fun ShadcnThemeValues.toggleGroupItemStyle(
    on: Boolean,
    variant: ShadcnToggleGroupVariant,
    shape: Shape,
): Style = Style {
    shape(shape)
    if (variant == ShadcnToggleGroupVariant.Outline) border(ItemBorderWidth, palette.border)
    // `data-[state=on]:bg-accent`, the same on-state both variants share.
    if (on) background(palette.accent)
    hovered(Style { background(if (on) palette.accent else palette.muted) })
    disabled(Style { alpha(DISABLED_ALPHA) })
}

/** Tailwind's bare `border` is 1px; the width scale is not generated -- see `ShadcnCard`. */
private val ItemBorderWidth: Dp = 1.dp

/** `disabled:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f
