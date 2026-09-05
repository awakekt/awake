/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.showcase.hud

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

/**
 * Standard MMORPG radar minimap frame with player coordinates and zone title.
 */
context(composer: Composer)
fun RpgMinimap(
    zoneName: String,
    playerX: Float,
    playerZ: Float,
    theme: RpgHudTheme = RpgHudTheme.DEFAULT,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(130.dp)
            .background(theme.frameBackground, theme.cornerRadius)
            .border(theme.borderWidth, theme.frameBorderColor, theme.cornerRadius)
            .padding(Tw.Spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShadcnText(zoneName, variant = ShadcnTextVariant.Small)
            ShadcnText("(${playerX.toInt()}, ${playerZ.toInt()})", variant = ShadcnTextVariant.Muted)
            ShadcnText("▲ Player", variant = ShadcnTextVariant.Muted)
        }
    }
}
