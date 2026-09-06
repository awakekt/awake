/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.hud

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

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
