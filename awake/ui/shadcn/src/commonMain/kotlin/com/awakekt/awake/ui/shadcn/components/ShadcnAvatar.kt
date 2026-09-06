/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's avatar, in its fallback form: a filled circle with initials.
 *
 * Upstream is an image with a fallback beneath it; there is no image loading in this engine yet, so
 * this is the fallback -- `bg-muted text-muted-foreground`, centred, `rounded-full`. Naming it
 * `shadcnAvatar` rather than `shadcnAvatarFallback` is deliberate: the image slot is an addition to
 * this component later, not a different one.
 */
context(_: Composer)
fun ShadcnAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: ShadcnAvatarSizeVariant = ShadcnAvatarSizeVariant.Default,
) {
    val theme = shadcnTheme
    Box(
        modifier.size(size.size).background(theme.palette.muted, theme.radii.full),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText(initials, variant = size.text, color = theme.palette.mutedForeground)
    }
}

/** Branded avatar status indicator badge. */
context(_: Composer)
fun ShadcnAvatarBadge(
    modifier: Modifier = Modifier,
    size: Dp = 10f.dp,
    color: Color? = null,
) {
    val theme = shadcnTheme
    val badgeColor = color ?: theme.palette.primary
    Box(
        modifier.size(size).background(badgeColor, theme.radii.full),
    )
}

/** Branded avatar group with overlapping items. */
context(_: Composer)
fun ShadcnAvatarGroup(
    initials: List<String>,
    modifier: Modifier = Modifier,
    size: ShadcnAvatarSizeVariant = ShadcnAvatarSizeVariant.Default,
    overlap: Dp = 8f.dp,
) {
    Row(modifier = modifier) {
        initials.forEachIndexed { index, value ->
            ShadcnAvatar(
                initials = value,
                size = size,
                modifier = if (index > 0) {
                    Modifier.offset(
                        (-overlap.value * index).dp,
                        0f.dp,
                    )
                } else {
                    Modifier
                },
            )
        }
    }
}
