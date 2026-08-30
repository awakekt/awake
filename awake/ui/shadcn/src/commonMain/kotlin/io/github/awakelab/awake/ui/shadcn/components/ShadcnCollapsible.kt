/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.SemanticsRole
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

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
fun shadcnCollapsible(
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
    val interaction = remember { InteractionSource() }
    val open = expanded

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
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
            shadcnIcon(
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
