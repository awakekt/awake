/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.testing.rasterize
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.heroicons.icon.HeroIcons
import io.github.awakelab.awake.ui.shadcn.components.ShadcnIcon
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An outline icon is an outline: its middle is empty.
 *
 * The tier is stroked path data, and the stroker turns each centreline into a fill by offsetting it
 * to either side. Where that goes wrong the result is a solid blob -- which is what the tier looked
 * like *before* it was stroked at all, so a regression here is invisible unless something measures
 * the hole.
 */
class OutlineIconStrokeTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun interiorCoverage(icon: io.github.awakelab.awake.compose.ui.graphics.vector.ImageVector): Float {
        val pixels = composeFrame(SIZE, SIZE) {
            provideShadcnTheme(theme) {
                ShadcnIcon(
                    icon,
                    size = SIZE.dp,
                    tint = Color(1f, 1f, 1f, 1f),
                )
            }
        }.primitives.rasterize(SIZE, SIZE, background = Color(0f, 0f, 0f, 0f))

        var covered = 0
        var total = 0
        val from = SIZE / 2 - PROBE
        val to = SIZE / 2 + PROBE
        for (y in from..to) {
            for (x in from..to) {
                val alpha = pixels[(y * SIZE + x) * 4 + 3].toInt() and 0xFF
                if (alpha > INK) covered++
                total++
            }
        }
        return covered.toFloat() / total
    }

    @Test
    fun outlineIconsAreNotSolidDiscs() {
        val measured = OUTLINE_ICONS.map { (name, icon) -> name to interiorCoverage(icon) }

        val unexpectedBlobs = measured.filter { (name, coverage) ->
            coverage > MAX_INTERIOR && name !in KNOWN_FILLED
        }
        assertTrue(
            unexpectedBlobs.isEmpty(),
            "these outline icons render as filled shapes: $unexpectedBlobs",
        )
    }

    /**
     * The known-broken list is exactly broken -- no more, and no fewer.
     *
     * Without this half, fixing the stroker leaves the list behind and the next icon to regress
     * hides inside it.
     */
    @Test
    fun theKnownFilledListIsCurrent() {
        val stillFilled = OUTLINE_ICONS
            .filter { (name, _) -> name in KNOWN_FILLED }
            .filter { (_, icon) -> interiorCoverage(icon) > MAX_INTERIOR }
            .map { it.first }
            .toSet()

        assertTrue(
            stillFilled == KNOWN_FILLED,
            "KNOWN_FILLED is stale: still filled $stillFilled, listed $KNOWN_FILLED. Remove what now " +
                "renders as an outline.",
        )
    }

    private companion object {
        /** A sample: two that stroke correctly, and the two that do not. */
        val OUTLINE_ICONS = listOf(
            "globeAlt" to HeroIcons.Outline24.globeAlt,
            "cube" to HeroIcons.Outline24.cube,
            "camera" to HeroIcons.Outline24.camera,
            "userCircle" to HeroIcons.Outline24.userCircle,
            "lightBulb" to HeroIcons.Outline24.lightBulb,
        )

        /** Icons the stroke tessellator still fills. Empty, and meant to stay that way. */
        val KNOWN_FILLED = emptySet<String>()

        const val SIZE = 64

        /** A window over the icon's middle, well inside its outline. */
        const val PROBE = 6

        const val INK = 40

        /** A stroke crossing the centre covers some of this window; a filled shape covers it all. */
        const val MAX_INTERIOR = 0.6f
    }
}
