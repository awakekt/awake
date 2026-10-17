/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.rememberGroup
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.dropShadow
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.isCompact
import com.awakekt.awake.ui.shadcn.theme.shadcnDensity
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/** Visual appearance of the tabs bar. */
enum class ShadcnTabsVariant {
    /** Pill-style tab list with muted background container and elevated active pill. */
    Pill,

    /** Underline-style tab list with bottom border and active indicator line. */
    Line,

    /** Containerless ghost tab list with transparent track and soft active pill. */
    Ghost,
}

/** Sizing variants for [ShadcnTabs]. */
enum class ShadcnTabsSize(
    internal val height: com.awakekt.awake.compose.ui.unit.Dp,
    internal val paddingX: com.awakekt.awake.compose.ui.unit.Dp,
    internal val textVariant: ShadcnTextVariant,
    /** The horizontal pad of a trigger in the [ShadcnTabsVariant.Pill] and [ShadcnTabsVariant.Line] lists. */
    internal val triggerPaddingX: com.awakekt.awake.compose.ui.unit.Dp,
    /** The vertical pad of a [ShadcnTabsVariant.Pill] trigger, which is 7dp shorter than its list. */
    internal val triggerPaddingY: com.awakekt.awake.compose.ui.unit.Dp,
) {
    /** Standard shadcn tabs sizing (`h-9` / 36dp). */
    Default(ShadcnTabsHeight, Tw.Spacing.s3, ShadcnTextVariant.Small, Tw.Spacing.s2, Tw.Spacing.s1),

    /** Compact tabs sizing (28dp height with text-xs typography) suitable for toolbars and sub-headers. */
    Sm(28.dp, Tw.Spacing.s2, ShadcnTextVariant.Xs, Tw.Spacing.s1_5, Tw.Spacing.s0_5),
}

/**
 * shadcn's tab bar: a muted `rounded-lg p-[3px] h-9` list with `rounded-md` triggers inside,
 * or a clean underline-style tab strip with [ShadcnTabsVariant.Line].
 *
 * **An inactive trigger is `text-foreground/60`, not `text-muted-foreground`.** Sixty percent of the
 * *foreground* is a different colour from the muted token, and it is what makes an inactive tab read
 * as dimmed-but-present rather than as a secondary label. Hover takes it back to full.
 *
 * The active trigger gets the background fill and source `shadow-sm` elevation.
 *
 * [size] left out follows `LocalShadcnDensity`: `Default`, or `Sm` under `Compact`. It sizes every
 * variant's list and triggers; a size passed here always wins.
 *
 * Returns the selected index after this frame's input, per this repo's return-value idiom.
 */
context(_: Composer)
fun ShadcnTabs(
    selectedValue: String,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: ShadcnTabsVariant = ShadcnTabsVariant.Pill,
    size: ShadcnTabsSize? = null,
    content: ShadcnTabsScope.() -> Unit,
) {
    rememberGroup(ShadcnTabsGroup) {
        val items = remember(content) { ShadcnTabsScope().apply(content).items }
        val theme = shadcnTheme
        val tabsSize = size ?: if (shadcnDensity.isCompact) ShadcnTabsSize.Sm else ShadcnTabsSize.Default

        key("shadcn-tabs-bar") {
            when (variant) {
                ShadcnTabsVariant.Line -> {
                    Row(
                        modifier
                            .height(tabsSize.height)
                            .border(1.dp, theme.palette.border, sides = BorderSides(top = false, end = false, bottom = true, start = false)),
                    ) {
                        items.forEach { item ->
                            key(item.value) {
                                ShadcnTabLineTrigger(item, selectedValue, onSelectedChange, tabsSize)
                            }
                        }
                    }
                }
                ShadcnTabsVariant.Pill -> {
                    Row(
                        modifier
                            .height(tabsSize.height)
                            .background(theme.palette.muted, theme.radii.lg)
                            .padding(ShadcnTabsListPadding),
                    ) {
                        items.forEach { item ->
                            key(item.value) {
                                ShadcnTabTrigger(item, selectedValue, onSelectedChange, tabsSize)
                            }
                        }
                    }
                }
                ShadcnTabsVariant.Ghost -> {
                    Row(
                        modifier.height(tabsSize.height),
                        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
                    ) {
                        items.forEach { item ->
                            key(item.value) {
                                ShadcnTabGhostTrigger(item, selectedValue, onSelectedChange, tabsSize)
                            }
                        }
                    }
                }
            }
        }
    }
}

context(_: Composer)
private fun ShadcnTabTrigger(
    item: ShadcnTab,
    selectedValue: String,
    onSelectedChange: (String) -> Unit,
    size: ShadcnTabsSize,
) {
    val theme = shadcnTheme
    val interaction = remember(item.value) { InteractionSource() }
    val active = item.value == selectedValue
    Box(
        Modifier
            // The source trigger is `h-[calc(100%-1px)]`: a 29px control inside the
            // 36px list with its 3px list padding. The line box plus py-1 is only 28px
            // in an integer retained layout, so state the source height explicitly.
            .height(size.height - ShadcnTabListInset)
            .let {
                if (active) {
                    it
                        .zIndex(1f)
                        .dropShadow(RoundedCornerShape(theme.radii.md), shadcnTabsActiveShadow(theme))
                        .background(theme.palette.background, theme.radii.md)
                } else {
                    it.zIndex(0f)
                }
            }
            .hoverable(interaction, enabled = item.enabled)
            .clickable(interaction) { if (item.enabled) onSelectedChange(item.value) }
            .padding(horizontal = size.triggerPaddingX, vertical = size.triggerPaddingY)
            .semantics {
                this[SemanticsProperties.Role] = SemanticsRole.Tab
                this[SemanticsProperties.Label] = item.label
                this[SemanticsProperties.TestTag] = item.tag ?: "parity-tabs.${item.label}"
                this[SemanticsProperties.Selected] = active
                if (!item.enabled) this[SemanticsProperties.Disabled] = true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val body = item.content
        if (body == null) {
            ShadcnText(
                item.label,
                variant = size.textVariant,
                color = if (active || interaction.isHovered) {
                    theme.palette.foreground
                } else {
                    theme.palette.foreground.withAlpha(ShadcnTabsInactiveAlpha)
                },
            )
        } else {
            body()
        }
    }
}

context(_: Composer)
private fun ShadcnTabLineTrigger(
    item: ShadcnTab,
    selectedValue: String,
    onSelectedChange: (String) -> Unit,
    size: ShadcnTabsSize,
) {
    val theme = shadcnTheme
    val interaction = remember(item.value) { InteractionSource() }
    val active = item.value == selectedValue
    Box(
        Modifier
            .height(size.height)
            .hoverable(interaction, enabled = item.enabled)
            .clickable(interaction) { if (item.enabled) onSelectedChange(item.value) }
            .padding(horizontal = size.triggerPaddingX, vertical = Tw.Spacing.s1)
            .semantics {
                this[SemanticsProperties.Role] = SemanticsRole.Tab
                this[SemanticsProperties.Label] = item.label
                this[SemanticsProperties.TestTag] = item.tag ?: "parity-tabs.${item.label}"
                this[SemanticsProperties.Selected] = active
                if (!item.enabled) this[SemanticsProperties.Disabled] = true
            }
            .let {
                if (active) {
                    it.border(2.dp, theme.palette.foreground, sides = BorderSides(top = false, end = false, bottom = true, start = false))
                } else {
                    it
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val body = item.content
        if (body == null) {
            ShadcnText(
                item.label,
                variant = size.textVariant,
                color = if (active || interaction.isHovered) {
                    theme.palette.foreground
                } else {
                    theme.palette.foreground.withAlpha(ShadcnTabsInactiveAlpha)
                },
            )
        } else {
            body()
        }
    }
}

context(_: Composer)
private fun ShadcnTabGhostTrigger(
    item: ShadcnTab,
    selectedValue: String,
    onSelectedChange: (String) -> Unit,
    size: ShadcnTabsSize,
) {
    val theme = shadcnTheme
    val interaction = remember(item.value) { InteractionSource() }
    val active = item.value == selectedValue
    Box(
        Modifier
            .height(size.height)
            .let {
                if (active) {
                    it.background(theme.palette.muted, theme.radii.md)
                } else if (interaction.isHovered && item.enabled) {
                    it.background(theme.palette.muted.withAlpha(0.5f), theme.radii.md)
                } else {
                    it
                }
            }
            .hoverable(interaction, enabled = item.enabled)
            .clickable(interaction) { if (item.enabled) onSelectedChange(item.value) }
            .padding(horizontal = size.paddingX, vertical = Tw.Spacing.s1)
            .semantics {
                this[SemanticsProperties.Role] = SemanticsRole.Tab
                this[SemanticsProperties.Label] = item.label
                this[SemanticsProperties.TestTag] = item.tag ?: "parity-tabs.${item.label}"
                this[SemanticsProperties.Selected] = active
                if (!item.enabled) this[SemanticsProperties.Disabled] = true
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val body = item.content
        if (body == null) {
            ShadcnText(
                item.label,
                variant = size.textVariant,
                color = if (active || interaction.isHovered) {
                    theme.palette.foreground
                } else {
                    theme.palette.foreground.withAlpha(ShadcnTabsInactiveAlpha)
                },
            )
        } else {
            body()
        }
    }
}

/**
 * What the triggers' height gives up to the list: the source trigger is `h-[calc(100%-1px)]` inside a
 * `p-[3px]` list, so 29dp in a 36dp one.
 */
private val ShadcnTabListInset = 7.dp

/** One tab trigger declared in [ShadcnTabs]' caller-owned slot. */
class ShadcnTab internal constructor(
    val value: String,
    val label: String,
    val enabled: Boolean,
    val tag: String?,
    val content: (
        context(Composer)
        () -> Unit
    )?,
)

/** Declares Tab triggers in their source order. */
@ShadcnTabsDsl
class ShadcnTabsScope internal constructor() {
    internal val items = mutableListOf<ShadcnTab>()

    fun tab(
        value: String,
        label: String,
        enabled: Boolean = true,
        tag: String? = null,
        content: (
            context(Composer)
            () -> Unit
        )? = null,
    ) {
        items += ShadcnTab(value, label, enabled, tag, content)
    }
}

@DslMarker
annotation class ShadcnTabsDsl

/** Keys this component's `remember` slots as one group in its caller's node. */
private object ShadcnTabsGroup
