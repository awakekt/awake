/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.hud

import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color

/**
 * Design system theme tokens for the turnkey MMORPG HUD suite.
 *
 * Provides styling for resource bars, action slots, minimap, and frame borders.
 */
data class RpgHudTheme(
    val healthColor: Color = Color.fromHex(0xE53935),
    val healthBackgroundColor: Color = Color.fromHex(0x4A1212),
    val manaColor: Color = Color.fromHex(0x1E88E5),
    val manaBackgroundColor: Color = Color.fromHex(0x0D335A),
    val energyColor: Color = Color.fromHex(0x43A047),
    val energyBackgroundColor: Color = Color.fromHex(0x143B17),
    val frameBackground: Color = Color.fromHex(0x1A1D24, 0.85f),
    val frameBorderColor: Color = Color.fromHex(0xE0A82E),
    val cornerRadius: Dp = 6.dp,
    val borderWidth: Dp = 1.dp,
) {
    companion object {
        val DEFAULT = RpgHudTheme()
    }
}
