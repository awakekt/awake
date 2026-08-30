/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnTextTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun glyphs(variant: ShadcnTextVariant, color: Color? = null): List<DrawCommand.Glyph> =
        composeFrame(400, 200) {
            provideShadcnTheme(theme) { ShadcnText("Ag", variant = variant, color = color) }
        }.primitivesOf()

    @Test
    fun everyVariantDrawsItsText() {
        ShadcnTextVariant.entries.forEach { variant ->
            assertTrue(glyphs(variant).isNotEmpty(), "$variant drew no glyphs")
        }
    }

    @Test
    fun theScaleDescendsFromH1ToMuted() {
        // shadcn's own order: 4xl, 3xl, 2xl, xl, base. A variant that stops shrinking means a step
        // was mapped to the wrong Tailwind size, which no single-variant assertion would catch.
        val heights = listOf(
            ShadcnTextVariant.H1,
            ShadcnTextVariant.H2,
            ShadcnTextVariant.H3,
            ShadcnTextVariant.H4,
            ShadcnTextVariant.P,
        ).map { variant -> glyphs(variant).maxOf { it.h } }

        heights.zipWithNext().forEach { (bigger, smaller) ->
            assertTrue(bigger > smaller, "the scale did not descend: $heights")
        }
    }

    @Test
    fun leadAndMutedTakeTheMutedForeground() {
        listOf(ShadcnTextVariant.Lead, ShadcnTextVariant.Muted).forEach { variant ->
            assertEquals(
                theme.palette.mutedForeground,
                glyphs(variant).first().color,
                "$variant is not muted",
            )
        }
    }

    @Test
    fun aPlainVariantDoesNotForceAColour() {
        // The inherit case. Upstream's AlertDescription is `text-sm` with no colour class, so inside
        // a destructive alert it stays red -- which only works if the variant leaves colour unset.
        val plain = glyphs(ShadcnTextVariant.P).first().color
        val muted = theme.palette.mutedForeground

        assertTrue(plain != muted, "a plain variant took the muted colour")
    }

    @Test
    fun anExplicitColourWinsOverTheVariants() {
        val red = Color(1f, 0f, 0f, 1f)

        assertEquals(red, glyphs(ShadcnTextVariant.Muted, color = red).first().color)
    }
}
