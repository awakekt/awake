/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.tailwind

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.math2d.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TwTest {

    @Test
    fun tailwindSpacingScaleMatchesExpectedValues() {
        assertEquals(0f.dp, Tw.Spacing.s0)
        assertEquals(1f.dp, Tw.Spacing.px)
        assertEquals(2f.dp, Tw.Spacing.s0_5)
        assertEquals(4f.dp, Tw.Spacing.s1)
        assertEquals(8f.dp, Tw.Spacing.s2)
        assertEquals(10f.dp, Tw.Spacing.s2_5)
        assertEquals(16f.dp, Tw.Spacing.s4)
        assertEquals(32f.dp, Tw.Spacing.s8)
        assertEquals(64f.dp, Tw.Spacing.s16)
        assertEquals(384f.dp, Tw.Spacing.s96)
    }

    @Test
    fun tailwindRadiusScaleMatchesExpectedValues() {
        assertEquals(0f.dp, Tw.Radius.none)
        assertEquals(2f.dp, Tw.Radius.sm)
        assertEquals(4f.dp, Tw.Radius.md)
        assertEquals(6f.dp, Tw.Radius.lg)
        assertEquals(8f.dp, Tw.Radius.xl)
        assertEquals(9999f.dp, Tw.Radius.full)
    }

    @Test
    fun modifierExtensionsReturnChainedModifier() {
        val modifier = Modifier.p(Tw.Spacing.s4).px(Tw.Spacing.s2).roundedMd().wFull()
        assertNotNull(modifier)
    }
}
