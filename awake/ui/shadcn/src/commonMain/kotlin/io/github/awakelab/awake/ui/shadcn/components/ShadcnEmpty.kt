/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.font.FontWeight
import io.github.awakelab.awake.tailwind.Tw

/**
 * shadcn's empty state: `flex flex-col items-center justify-center gap-6 rounded-lg p-6 md:p-12`.
 *
 * Takes the `md:` value, as `ShadcnInput` does for `md:text-sm`: there is no breakpoint system
 * here, and every parity capture is taken at a desktop width.
 *
 * Two gaps, not one. Upstream nests `EmptyHeader` (`gap-2`) inside the container (`gap-6`), so the
 * description sits close to its title and the action sits well clear of both. The ui-core recipe
 * used a single 8dp gap throughout and made the title look like part of a list.
 *
 * `border-dashed` with no `border` sets a style and no width, so nothing is painted -- the capture
 * reports `borderWidth: 0`. Drawing a dashed border here would be inventing one.
 */
context(_: Composer)
fun shadcnEmpty(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(EmptyPadding),
        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // `text-lg font-medium` -- the Large variant is semibold, which is a different weight.
            ShadcnText(title, variant = ShadcnTextVariant.Large, weight = FontWeight.Medium)
            if (description != null) {
                ShadcnText(
                    description,
                    variant = ShadcnTextVariant.Muted,
                    // `text-sm/relaxed`: 14 * 1.625. Not a step in the scale, so it is stated.
                    lineHeight = RelaxedSmall,
                )
            }
        }
        val body = content
        if (body != null) body()
    }
}

/** `md:p-12`. */
private val EmptyPadding: Dp = Tw.Spacing.s12

/** Tailwind's `relaxed` leading at `text-sm`: 14 * 1.625. */
private val RelaxedSmall = 22.75f.sp
