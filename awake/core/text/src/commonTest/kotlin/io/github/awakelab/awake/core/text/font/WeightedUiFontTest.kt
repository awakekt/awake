/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WeightedUiFontTest {
    private val font = UiFonts.weightedSans()

    @Test
    fun everyBundledWeightResolvesToItsOwnAtlasSlice() {
        val normal = font.glyphFor('A', FontWeight.Normal)
        val medium = font.glyphFor('A', FontWeight.Medium)
        val thin = font.glyphFor('A', FontWeight.Thin)

        requireNotNull(normal)
        requireNotNull(medium)
        requireNotNull(thin)
        assertNotEquals(normal.v0, medium.v0)
        assertNotEquals(normal.v0, thin.v0)
    }

    @Test
    fun unsupportedIntermediateWeightChoosesTheNearestBundledFace() {
        val semiBold = font.glyphFor('A', FontWeight.SemiBold)
        val custom550 = font.glyphFor('A', FontWeight(550))

        requireNotNull(semiBold)
        requireNotNull(custom550)
        assertTrue(custom550.v0 == semiBold.v0, "550 should resolve to the nearest 600 face")
    }
}
