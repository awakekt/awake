/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.showcase.hud

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.tailwind.Tw

/**
 * Turnkey responsive MMORPG HUD layout pre-arranging Unit Frames, Minimap, and Action Bar.
 */
context(composer: Composer)
fun RpgHudLayout(
    playerName: String,
    playerLevel: Int,
    currentHp: Int,
    maxHp: Int,
    currentMp: Int,
    maxMp: Int,
    zoneName: String,
    playerX: Float,
    playerZ: Float,
    actionSlots: List<RpgActionSlot>,
    onTriggerActionSlot: (Int) -> Unit,
    theme: RpgHudTheme = RpgHudTheme.DEFAULT,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Top-Left: Player Unit Frame
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Tw.Spacing.s4),
        ) {
            RpgUnitFrame(
                name = playerName,
                level = playerLevel,
                currentHp = currentHp,
                maxHp = maxHp,
                currentMp = currentMp,
                maxMp = maxMp,
                theme = theme,
            )
        }

        // Top-Right: Minimap
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Tw.Spacing.s4),
        ) {
            RpgMinimap(
                zoneName = zoneName,
                playerX = playerX,
                playerZ = playerZ,
                theme = theme,
            )
        }

        // Bottom-Center: Action Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Tw.Spacing.s4),
        ) {
            RpgActionBar(
                slots = actionSlots,
                onTriggerSlot = onTriggerActionSlot,
                theme = theme,
            )
        }
    }
}
