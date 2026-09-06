/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.Canvas
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.vector.ImageVector
import com.awakekt.awake.compose.ui.graphics.vector.rememberVectorPainter
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * An icon, drawn from an [ImageVector].
 *
 * **`Icon` is Material's, not Foundation's** -- `androidx.compose.material3.Icon`, with no
 * counterpart in `androidx.compose.foundation`. So this belongs here, in the design system, for the
 * same reason `ShadcnSlider` does. `ImageVector` itself is compose-ui's and lives there.
 *
 * shadcn sizes icons inside controls with `[&_svg:not([class*='size-'])]:size-4` -- a 16dp default
 * that a caller's own `size-*` overrides. That is the [size] default here.
 *
 * Drawn through `DrawScope.drawPath`, which this recipe is the reason for. Reaching for `emit`
 * instead -- the documented escape hatch -- painted the icon at the *frame's* origin rather than its
 * own node, because every other `DrawScope` helper maps node-local coordinates into tree space and
 * `emit` does not. Caught by looking at a gallery render and finding the chevron above the title it
 * was declared after.
 */
context(_: Composer)
fun ShadcnIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = IconSize,
    tint: Color? = null,
) {
    val color = tint ?: LocalTextStyle.current.color ?: shadcnTheme.palette.foreground
    val painter = rememberVectorPainter(icon)
    Canvas(modifier.size(size)) {
        painter.draw(this, Rectangle(0f, 0f, width.toFloat(), height.toFloat()), color)
    }
}

/** shadcn's `size-4` for an icon inside a control. */
private val IconSize: Dp = 16.dp
