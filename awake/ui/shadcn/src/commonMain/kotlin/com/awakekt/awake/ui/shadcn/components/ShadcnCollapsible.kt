/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's collapsible: a trigger row and, when open, its content below.
 *
 * The trigger is a full-width row with the title on the left and a chevron on the right -- no
 * padding of its own, which is why the capture reports 280x36 with zero insets. The chevron is
 * chrome, so it stays muted whatever the title does.
 *
 * Returns the open state after this frame's click.
 */
context(_: Composer)
fun ShadcnCollapsible(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val open = expanded

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
        val interaction = remember { InteractionSource() }
        Row(
            Modifier
                .fillMaxWidth()
                .height(TriggerHeight)
                .clickable(interaction) { onExpandedChange(!expanded) }
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Button
                    this[SemanticsProperties.Label] = title
                    this[SemanticsProperties.Selected] = open
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(title, Modifier.weight(1f), variant = ShadcnTextVariant.Small)
            ShadcnIcon(
                if (open) ShadcnIcons.chevronUp else ShadcnIcons.chevronDown,
                tint = theme.palette.mutedForeground,
            )
        }
        val body = content
        if (open && body != null) body()
    }
}

/** The capture's trigger row height. */
private val TriggerHeight: Dp = 36.dp

@Deprecated("Use ShadcnCollapsible instead", ReplaceWith("ShadcnCollapsible(title, expanded, modifier, onExpandedChange, content)"))
context(_: Composer)
fun shadcnCollapsible(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) = ShadcnCollapsible(title, expanded, modifier, onExpandedChange, content)
