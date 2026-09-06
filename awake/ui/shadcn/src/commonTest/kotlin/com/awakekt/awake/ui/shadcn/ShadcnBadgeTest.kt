/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnBadgeVariant
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnBadgeTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun badge(variant: ShadcnBadgeVariant) =
        composeFrame(200, 80) {
            provideShadcnTheme(theme) { ShadcnBadge("Badge", variant = variant) }
        }

    @Test
    fun everyVariantDrawsAPillAndItsLabel() {
        ShadcnBadgeVariant.entries.forEach { variant ->
            val frame = badge(variant)
            assertTrue(
                frame.primitivesOf<DrawCommand.Glyph>().isNotEmpty(),
                "$variant drew no label",
            )
        }
    }

    @Test
    fun everyVariantIsTheSameSize() {
        // Upstream puts `border border-transparent` on the base so only Outline recolours it. If a
        // variant dropped the border instead of making it transparent, its box would shrink by 2px
        // and a row of mixed badges would sit unevenly.
        val widths = ShadcnBadgeVariant.entries.map { variant ->
            badge(variant).primitivesOf<DrawCommand.RoundedQuad>().first().w
        }

        assertEquals(1, widths.distinct().size, "variants disagree on width: $widths")
    }

    @Test
    fun destructiveTakesWhiteNotAThemeToken() {
        // Upstream is `text-white`, literally -- shadcn defines no --destructive-foreground, and
        // shadcn-compose substitutes onDestructive here. In dark mode the two differ.
        val label = badge(ShadcnBadgeVariant.Destructive).primitivesOf<DrawCommand.Glyph>().first()

        assertEquals(Color.White, label.color, "destructive's label is not text-white")
    }

    @Test
    fun outlineIsTheOnlyVariantWithAVisibleBorder() {
        val outline = badge(ShadcnBadgeVariant.Outline).meshColors()
        val default = badge(ShadcnBadgeVariant.Default).meshColors()

        assertTrue(theme.palette.border in outline, "outline drew no bordered edge")
        assertTrue(theme.palette.border !in default, "a filled variant drew a visible border")
    }

    @Test
    fun theOutlineBorderIsAsRoundedAsTheFill() {
        // Caught by looking at the render: the pill fills were round while Outline's border read as
        // a rectangle. A border drawn at a different radius than the box it traces is visible at
        // exactly the variant that has one.
        val quads = badge(ShadcnBadgeVariant.Outline).primitivesOf<DrawCommand.RoundedQuad>()
        val radii = quads.map { it.radius }.distinct()

        assertEquals(1, radii.size, "the outline's parts disagree on radius: $radii")
        // Equal is not enough -- two square corners are also equal.
        assertTrue(quads.all { it.radius >= it.h / 2f - 0.5f }, "the outline is not a pill: $radii")
    }

    @Test
    fun theCornerIsFullyRounded() {
        val pill = badge(ShadcnBadgeVariant.Default).primitivesOf<DrawCommand.RoundedQuad>().first()

        assertTrue(pill.radius >= pill.h / 2f - 0.5f, "rounded-full did not reach a pill")
    }
}
