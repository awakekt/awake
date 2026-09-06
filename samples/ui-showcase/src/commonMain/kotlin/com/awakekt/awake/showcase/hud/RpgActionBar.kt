/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.hud

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

data class RpgActionSlot(
    val index: Int,
    val keybind: String,
    val skillName: String? = null,
    val icon: String? = null,
    val cooldownPercent: Float = 0f,
)

/**
 * 10-slot MMORPG hotbar with keybind badges and cooldown sweeps.
 */
context(composer: Composer)
fun RpgActionBar(
    slots: List<RpgActionSlot>,
    onTriggerSlot: (Int) -> Unit,
    theme: RpgHudTheme = RpgHudTheme.DEFAULT,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(theme.frameBackground, theme.cornerRadius)
            .border(theme.borderWidth, theme.frameBorderColor, theme.cornerRadius)
            .padding(Tw.Spacing.s2),
        horizontalArrangement = Arrangement.spacedByHorizontal(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.take(10).forEach { slot ->
            val interaction = remember { InteractionSource() }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(interaction) { onTriggerSlot(slot.index) }
                    .background(theme.frameBackground, 4.dp)
                    .border(1.dp, if (slot.skillName != null) theme.frameBorderColor else theme.frameBorderColor.withAlpha(0.3f), 4.dp),
            ) {
                // Cooldown overlay
                if (slot.cooldownPercent > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.frameBackground.withAlpha(0.7f)),
                    )
                }

                // Skill label
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (slot.skillName != null) {
                        ShadcnText(slot.skillName.take(3), variant = ShadcnTextVariant.Small)
                    }
                }

                // Keybind badge top-left
                Box(modifier = Modifier.padding(2.dp), contentAlignment = Alignment.TopStart) {
                    ShadcnText(slot.keybind, variant = ShadcnTextVariant.Muted)
                }
            }
        }
    }
}
