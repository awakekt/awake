/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.drawWithCache
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * A determinate progress bar.
 *
 * Upstream `progress.tsx` is a `h-2 w-full overflow-hidden rounded-full bg-primary/20` track with a
 * `h-full bg-primary` indicator. The track is **the primary colour at 20%**, not `bg-muted` -- so a
 * progress bar tints with the theme's accent rather than reading as a grey groove.
 *
 * Drawn with [drawWithCache] rather than laid out as two boxes: the indicator is a fraction of the
 * track's measured width, and draw operations are cached across steady-state frames.
 */
context(_: Composer)
fun ShadcnProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: io.github.awakelab.awake.compose.ui.unit.Dp = ShadcnProgressHeight,
) {
    val theme = shadcnTheme
    val fraction = progress.coerceIn(0f, 1f)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val trackColor = theme.palette.primary.withAlpha(ShadcnProgressTrackAlpha)
                val indicatorColor = theme.palette.primary
                val radius = size.height / 2f
                onDrawBehind {
                    drawRoundedRect(
                        width = size.width,
                        height = size.height,
                        color = trackColor,
                        radius = radius,
                    )
                    if (fraction > 0f) {
                        drawRoundedRect(
                            width = size.width * fraction,
                            height = size.height,
                            color = indicatorColor,
                            radius = radius,
                        )
                    }
                }
            }
    )
}
