/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.ColumnScope
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnItem`: A media object list item row with leading slot, title/description text stack, and trailing action slot.
 *
 * **Tailwind Reference**: `flex items-center gap-3 rounded-md border bg-card px-4 py-3 text-card-foreground shadow-sm`.
 *
 * Use cases:
 * - Settings rows (e.g. 2FA, Security tokens, Notifications with trailing switch/button).
 * - User profile rows (leading avatar, title/subtitle, trailing badge/status).
 * - Media & notification items (leading icon, title/time, trailing action menu).
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnItem(
 *     title = "Two-Factor Authentication",
 *     description = "Add an extra layer of security to your account.",
 *     leading = { ShadcnAvatar(initials = "2F") },
 *     trailing = { ShadcnButton("Enable", size = ShadcnButtonSizeVariant.Sm) }
 * )
 * ```
 *
 * @param title Primary header text for the item row.
 * @param description Optional secondary description or subtitle rendered in muted text.
 * @param modifier Custom layout modifier applied to the container.
 * @param leading Optional leading slot content (e.g. avatar, icon, or checkbox).
 * @param trailing Optional trailing slot content (e.g. action button, badge, switch, or chevron).
 *
 * Keywords: item, row, media object, list row, settings row, avatar row, notification item.
 */
context(_: Composer)
fun ShadcnItem(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    leading: (
        context(Composer)
        () -> Unit
    )? = null,
    trailing: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    ShadcnItem(
        modifier = modifier,
        leading = leading,
        trailing = trailing,
    ) {
        ShadcnText(title, variant = ShadcnTextVariant.Small)
        if (description != null) {
            shadcnMuted(description)
        }
    }
}

/**
 * Composable body variant of `ShadcnItem` for custom multi-line or badge-annotated media rows.
 *
 * @param modifier Custom layout modifier.
 * @param leading Optional leading slot content (e.g. icon or avatar).
 * @param trailing Optional trailing slot content (e.g. action buttons or badges).
 * @param content Custom column content slot for the item's central text/media block.
 */
context(_: Composer)
fun ShadcnItem(
    modifier: Modifier = Modifier,
    leading: (
        context(Composer)
        () -> Unit
    )? = null,
    trailing: (
        context(Composer)
        () -> Unit
    )? = null,
    content: context(Composer) ColumnScope.() -> Unit,
) {
    val theme = shadcnTheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.palette.card, theme.radii.md)
            .border(1f.dp, theme.palette.border, theme.radii.md)
            .padding(horizontal = Tw.Spacing.s4, vertical = Tw.Spacing.s3),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1),
        ) {
            content()
        }
        if (trailing != null) {
            trailing()
        }
    }
}
