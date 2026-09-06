/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.hud

import com.awakekt.awake.showcase.hud.RpgActionSlot
import com.awakekt.awake.showcase.hud.RpgHudTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RpgHudTest {

    @Test
    fun defaultRpgHudThemeHasExpectedTokens() {
        val theme = RpgHudTheme.DEFAULT
        assertNotNull(theme.healthColor)
        assertNotNull(theme.manaColor)
        assertNotNull(theme.frameBackground)
        assertEquals(1f, theme.healthColor.a)
    }

    @Test
    fun actionSlotCarriesKeybindAndCooldown() {
        val slot = RpgActionSlot(
            index = 0,
            keybind = "1",
            skillName = "Slash",
            cooldownPercent = 0.5f,
        )
        assertEquals(0, slot.index)
        assertEquals("1", slot.keybind)
        assertEquals("Slash", slot.skillName)
        assertEquals(0.5f, slot.cooldownPercent)
    }
}
