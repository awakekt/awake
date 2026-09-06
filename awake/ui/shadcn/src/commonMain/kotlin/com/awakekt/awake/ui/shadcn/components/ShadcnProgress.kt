/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A determinate progress bar.
 *
 * Upstream `progress.tsx` is a `h-2 w-full overflow-hidden rounded-full bg-primary/20` track with a
 * `h-full bg-primary` indicator. The track is **the primary colour at 20%**, not `bg-muted` -- so a
 * progress bar tints with the theme's accent rather than reading as a grey groove.
 *
 * Drawn rather than laid out as two boxes: the indicator is a fraction of the track's measured
 * width.
 *
 * Not [drawBehind]'s caching sibling. `drawWithCache` rebuilds on size, density and layout
 * direction only, so it captured a `progress` that changed while the bar stayed the same width and
 * kept redrawing the first frame's fraction forever.
 */
context(_: Composer)
fun ShadcnProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: com.awakekt.awake.compose.ui.unit.Dp = ShadcnProgressHeight,
) {
    val theme = shadcnTheme
    val fraction = progress.coerceIn(0f, 1f)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val trackColor = theme.palette.primary.withAlpha(ShadcnProgressTrackAlpha)
                val indicatorColor = theme.palette.primary
                // `this.` is load-bearing: the composable's own `height: Dp` parameter shadows the
                // DrawScope's measured `height: Int`, and Dp compiles here as a silent wrong unit.
                val drawnWidth = this.width.toFloat()
                val drawnHeight = this.height.toFloat()
                val radius = drawnHeight / 2f
                drawRoundedRect(
                    width = drawnWidth,
                    height = drawnHeight,
                    color = trackColor,
                    radius = radius,
                )
                if (fraction > 0f) {
                    drawRoundedRect(
                        width = drawnWidth * fraction,
                        height = drawnHeight,
                        color = indicatorColor,
                        radius = radius,
                    )
                }
            },
    )
}
