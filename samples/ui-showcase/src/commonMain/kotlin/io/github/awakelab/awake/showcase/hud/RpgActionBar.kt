/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.showcase.hud

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

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
