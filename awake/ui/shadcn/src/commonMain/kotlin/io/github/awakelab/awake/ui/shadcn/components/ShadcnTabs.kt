/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.dropShadow
import io.github.awakelab.awake.compose.ui.draw.zIndex
import io.github.awakelab.awake.compose.ui.graphics.RoundedCornerShape
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's tab bar: a muted `rounded-lg p-[3px] h-9` list with `rounded-md` triggers inside.
 *
 * **An inactive trigger is `text-foreground/60`, not `text-muted-foreground`.** Sixty percent of the
 * *foreground* is a different colour from the muted token, and it is what makes an inactive tab read
 * as dimmed-but-present rather than as a secondary label. Hover takes it back to full.
 *
 * The active trigger gets the background fill and source `shadow-sm` elevation.
 *
 * Returns the selected index after this frame's input, per this repo's return-value idiom.
 */
context(_: Composer)
fun ShadcnTabs(
    selectedValue: String,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: ShadcnTabsScope.() -> Unit,
) {
    val items = remember(content) { ShadcnTabsScope().apply(content).items }
    val theme = shadcnTheme

    Row(
        modifier
            .height(ShadcnTabsHeight)
            .background(theme.palette.muted, theme.radii.lg)
            .padding(ShadcnTabsListPadding),
    ) {
        items.forEach { item ->
            val interaction = remember { InteractionSource() }
            val active = item.value == selectedValue
            Box(
                Modifier
                    // The source trigger is `h-[calc(100%-1px)]`: a 29px control inside the
                    // 36px list with its 3px list padding. The line box plus py-1 is only 28px
                    // in an integer retained layout, so state the source height explicitly.
                    .height(ShadcnTabHeight)
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
                    .padding(horizontal = Tw.Spacing.s2, vertical = Tw.Spacing.s1)
                    .hoverable(interaction, enabled = item.enabled)
                    .clickable(interaction) { if (item.enabled) onSelectedChange(item.value) }
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
                        variant = ShadcnTextVariant.Small,
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
    }
}

private val ShadcnTabHeight = 29.dp

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
