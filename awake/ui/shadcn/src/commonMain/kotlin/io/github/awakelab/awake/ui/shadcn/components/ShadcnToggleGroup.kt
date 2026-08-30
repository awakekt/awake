/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.disabled
import io.github.awakelab.awake.compose.foundation.style.hovered
import io.github.awakelab.awake.compose.foundation.style.rememberStyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.graphics.vector.ImageVector
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's toggle group: a strip of two-state buttons.
 *
 * **Not [ShadcnButtonGroup].** Upstream's own rule, stated in the ButtonGroup docs: a button group
 * groups buttons that *perform an action*, a toggle group groups buttons that *toggle a state*.
 * Studio's viewport pills were button groups faking pressed with `variant = Secondary/Ghost` --
 * thirteen states and one action, all reporting to a screen reader as unrelated buttons. The
 * difference is visible in the accessibility tree the moment this is used: [Single] reports a
 * radio group of radio buttons, which is what modal tool selection actually is.
 *
 * **Joined ends are not drawn yet.** Upstream squares every inner corner and rounds only the two
 * outer ones. `Style` carries a single `cornerRadius`, not four, so every item rounds all four --
 * the same limitation [ShadcnButtonGroup] states, and the same fix unblocks both (per-corner radii,
 * see `docs/audits/` on button-group intrinsics). Stated rather than approximated: faking it with
 * insets would move the geometry to hide a paint difference. Until then a caller that wants the
 * strip to read as deliberate can pass [spacing], which is upstream's own prop for a gapped group.
 */
context(_: Composer)
fun shadcnToggleGroup(
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
    val spec = remember(selection, variant to size) { ToggleGroupSpec(selection, variant, size) }
    val arrangement = Arrangement.spacedBy(spacing)
    val group = modifier.semantics {
        // What makes a strip of radios *one* choice rather than several independent ones.
        if (selection == ShadcnToggleGroupSelection.Single) {
            this[SemanticsProperties.SelectableGroup] = true
        }
    }
    when (orientation) {
        ShadcnButtonGroupOrientation.Horizontal -> Row(
            group,
            horizontalArrangement = Arrangement.spacedByHorizontal(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { ToggleGroupItem(it, selected, spec, onSelectedChange) }
        }
        ShadcnButtonGroupOrientation.Vertical -> Column(group, verticalArrangement = arrangement) {
            items.forEach { ToggleGroupItem(it, selected, spec, onSelectedChange) }
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
fun shadcnToggleGroup(
    selected: String?,
    onSelectedChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ShadcnButtonGroupOrientation = ShadcnButtonGroupOrientation.Horizontal,
    variant: ShadcnToggleGroupVariant = ShadcnToggleGroupVariant.Default,
    size: ShadcnToggleGroupSize = ShadcnToggleGroupSize.Default,
    spacing: Dp = 0.dp,
    content: ShadcnToggleGroupScope.() -> Unit,
) = shadcnToggleGroup(
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

/** One item in a [shadcnToggleGroup]. */
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

/** Declares the items of a [shadcnToggleGroup], in order. */
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
)

context(_: Composer)
private fun ToggleGroupItem(
    item: ShadcnToggleGroupItem,
    selected: Set<String>,
    spec: ToggleGroupSpec,
    onSelectedChange: (Set<String>) -> Unit,
) {
    val selection = spec.selection
    val variant = spec.variant
    val size = spec.size
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val styleState = rememberStyleState(interaction, enabled = item.enabled)
    val on = item.value in selected
    // Two keys, not three: `remember` has no three-key overload, and the pair (on, variant) is a
    // single value anyway -- the style depends on which of four combinations it is.
    val style = remember(theme, on to variant) { theme.toggleGroupItemStyle(on, variant) }

    // An item that draws a glyph instead of its label is only discoverable on hover, so it gets a
    // tooltip; one that draws its own text already says what it is. The rule is exact, so it is
    // applied rather than left to a flag every caller would have to remember.
    val labelled = item.icon == null && item.content == null
    TooltipIf(!labelled, item.label) {
        Box(
            Modifier
                .size(size.extent)
                .hoverable(interaction, enabled = item.enabled)
                .clickable(interaction) {
                    if (item.enabled) onSelectedChange(selected.toggling(item.value, selection))
                }
                .styleable(styleState, style)
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
                item.icon != null -> shadcnIcon(item.icon, tint = tint)
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
    if (enabled) shadcnTooltipped(text) { content() } else content()
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
): Style = Style {
    cornerRadius(radii.md)
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
