/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's breadcrumb trail: `text-sm text-muted-foreground` with `gap-1.5` between parts.
 *
 * **The last crumb is the page, and it is not muted.** Upstream marks it `aria-current="page"` and
 * styles it `text-foreground font-normal`, so the trail dims *behind* you and the place you are is
 * the readable one. A uniformly muted trail is the obvious wrong version and reads as all-disabled.
 *
 * The separator is a chevron in upstream; a slash is used here because a chevron would need the icon
 * registry threaded through, and the shape of the trail is what this component is for. Swapping it
 * for `HeroIcons.chevronRight` is a one-line change now that `shadcnIcon` exists.
 */
context(_: Composer)
fun shadcnBreadcrumb(
    crumbs: List<String>,
    modifier: Modifier = Modifier,
) {
    val theme = shadcnTheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { index, crumb ->
            val isCurrent = index == crumbs.lastIndex
            ShadcnText(
                crumb,
                variant = ShadcnTextVariant.Small,
                color = if (isCurrent) theme.palette.foreground else theme.palette.mutedForeground,
            )
            if (!isCurrent) {
                ShadcnText(
                    SEPARATOR,
                    modifier = Modifier.padding(horizontal = Tw.Spacing.s1_5),
                    variant = ShadcnTextVariant.Small,
                    color = theme.palette.mutedForeground,
                )
            }
        }
    }
}

/** Upstream uses a chevron; see the note on [shadcnBreadcrumb]. */
private const val SEPARATOR = "/"
