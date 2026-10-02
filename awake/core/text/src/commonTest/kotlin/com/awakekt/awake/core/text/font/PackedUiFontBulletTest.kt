/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val BULLET = '\u2022'

/**
 * U+2022 BULLET, the one glyph the atlas packs beyond ASCII: what a password field masks with.
 *
 * Without it every masked character drew the fallback `'?'`, which reads as a broken field
 * rather than a hidden one.
 */
class PackedUiFontBulletTest {

    private val faces = listOf(
        RobotoThinUiFontData,
        RobotoLightUiFontData,
        RobotoRegularUiFontData,
        RobotoMediumUiFontData,
        RobotoSemiBoldUiFontData,
        RobotoBoldUiFontData,
        RobotoBlackUiFontData,
    )

    @Test
    fun everyFaceDrawsTheBulletRatherThanItsFallback() {
        faces.forEach { data ->
            val font = PackedUiFont(data)
            assertNotEquals(font.uvFor('\uFFFF'), font.uvFor(BULLET), "${data.name} has no bullet glyph")
        }
    }

    @Test
    fun theBulletIsADotInsideTheXHeightThatFitsItsAdvance() {
        faces.forEach { data ->
            val font = PackedUiFont(data)
            val bullet = requireNotNull(font.inkFor(BULLET))
            val x = requireNotNull(font.inkFor('x'))

            assertTrue(bullet.widthEm > 0f && bullet.heightEm > 0f, "${data.name} bullet has no ink")
            assertTrue(
                bullet.offsetYEm >= x.offsetYEm && bullet.offsetYEm + bullet.heightEm <= x.offsetYEm + x.heightEm,
                "${data.name} bullet sits outside the x-height band",
            )
            assertTrue(
                bullet.offsetXEm + bullet.widthEm <= font.advanceFor(BULLET, 1f),
                "${data.name} bullet overhangs its own advance, so a run of masks would touch",
            )
        }
    }

    @Test
    fun theDefaultWeightedFamilyResolvesTheBulletInEveryWeight() {
        val family = UiFonts.default()
        listOf(
            FontWeight.Thin,
            FontWeight.Light,
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
            FontWeight.Black,
        ).forEach { weight ->
            assertNotEquals(
                family.glyphFor('\uFFFF', weight),
                family.glyphFor(BULLET, weight),
                "the bullet fell back to '?' at $weight",
            )
        }
    }
}
