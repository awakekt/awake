/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * A filled, bordered panel.
 *
 * Thin on purpose, and arguably shouldn't exist. `ui-core`'s `surface()` was the only way to draw
 * a filled rounded bordered box, so every recipe went through it; `:compose:foundation` does that
 * with `Modifier.background`/`.border`, which is how Compose does it too -- `Surface` is Material's
 * and foundation has none.
 *
 * It is here because Studio's shell calls `shadcnSurface` in a dozen places and porting those to
 * bare modifiers in the same change would bury the layout migration in unrelated diffs. New code
 * should use the modifiers; this exists so that migration is one step rather than two.
 */
@Deprecated("Should utilize shadcn Card")
context(_: Composer)
fun shadcnSurface(
    modifier: Modifier = Modifier,
    contentPadding: Dp = SurfacePadding,
    cornerRadius: Dp = shadcnTheme.radii.lg,
    bordered: Boolean = true,
    content: context(Composer) () -> Unit,
) {
    val theme = shadcnTheme
    val base = modifier.background(theme.palette.card, cornerRadius)
    Column(
        (
            if (bordered) {
                base.border(
                    SurfaceBorderWidth,
                    theme.palette.border,
                    cornerRadius,
                )
            } else {
                base
            }
            )
            .padding(contentPadding),
    ) { content() }
}

/** shadcn's panel inset, `p-4`. */
private val SurfacePadding: Dp = 16.dp

/** Tailwind's bare `border`. */
private val SurfaceBorderWidth: Dp = 1.dp
