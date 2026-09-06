/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A hairline rule.
 *
 * Upstream `separator.tsx` is `shrink-0 bg-border`, then `h-px w-full` horizontally and
 * `h-full w-px` vertically -- so the thickness is Tailwind's `px`, which is one *CSS* pixel and
 * therefore one density-independent unit, not one physical pixel. `1.dp` is the faithful
 * translation; a device-pixel hairline would be half as thick at 2x and is a different design.
 *
 * The recipe this replaces took a nullable `id` and fell back to `"separator.${orientation}"`, with
 * its own comment noting the collision that invites -- two horizontal separators on one screen
 * sharing a state slot. Positional identity removes the parameter rather than fixing it.
 */
context(_: Composer)
fun ShadcnSeparator(
    modifier: Modifier = Modifier,
    orientation: ShadcnSeparatorOrientation = ShadcnSeparatorOrientation.Horizontal,
    thickness: Dp = SeparatorThickness,
) {
    val theme = shadcnTheme
    val sized = when (orientation) {
        ShadcnSeparatorOrientation.Horizontal -> modifier.fillMaxWidth().height(thickness)
        ShadcnSeparatorOrientation.Vertical -> modifier.fillMaxHeight().width(thickness)
    }
    Spacer(sized.background(theme.palette.border))
}

/** Tailwind's `px`: one CSS pixel, which is one density-independent unit. */
private val SeparatorThickness: Dp = 1.dp
