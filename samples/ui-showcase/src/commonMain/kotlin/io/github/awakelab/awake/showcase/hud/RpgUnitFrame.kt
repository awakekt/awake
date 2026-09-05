/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.showcase.hud

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

/**
 * MMORPG Unit Frame displaying name, level, HP, and MP bars.
 */
context(composer: Composer)
fun RpgUnitFrame(
    name: String,
    level: Int,
    currentHp: Int,
    maxHp: Int,
    currentMp: Int,
    maxMp: Int,
    theme: RpgHudTheme = RpgHudTheme.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val safeMaxHp = maxHp.coerceAtLeast(1)
    val hpPercent = (currentHp.toFloat() / safeMaxHp).coerceIn(0f, 1f)
    val safeMaxMp = maxMp.coerceAtLeast(1)
    val mpPercent = (currentMp.toFloat() / safeMaxMp).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .width(220.dp)
            .background(theme.frameBackground, theme.cornerRadius)
            .border(theme.borderWidth, theme.frameBorderColor, theme.cornerRadius)
            .padding(Tw.Spacing.s2),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText(name, variant = ShadcnTextVariant.Small)
                ShadcnBadge(label = "Lv.$level", variant = ShadcnBadgeVariant.Secondary)
            }

            // HP Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(theme.healthBackgroundColor, 2.dp)
                    .border(1.dp, theme.frameBorderColor.withAlpha(0.5f), 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpPercent)
                        .fillMaxHeight()
                        .background(theme.healthColor, 2.dp),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ShadcnText("$currentHp / $safeMaxHp", variant = ShadcnTextVariant.Muted)
                }
            }

            // MP Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(theme.manaBackgroundColor, 2.dp)
                    .border(1.dp, theme.frameBorderColor.withAlpha(0.5f), 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(mpPercent)
                        .fillMaxHeight()
                        .background(theme.manaColor, 2.dp),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ShadcnText("$currentMp / $safeMaxMp", variant = ShadcnTextVariant.Muted)
                }
            }
        }
    }
}
