/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnCardTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun card(
        content: (
            context(io.github.awakelab.awake.compose.runtime.Composer)
            () -> Unit
        )? = null,
    ) =
        composeFrame(300, 200) {
            provideShadcnTheme(theme) {
                ShadcnCard(Modifier.size(200.dp, 100.dp), content = content)
            }
        }

    @Test
    fun aCardFillsAndBordersItself() {
        val frame = card()
        val fill = frame.primitivesOf<DrawCommand.RoundedQuad>()
        val border = frame.meshColors()

        assertTrue(fill.isNotEmpty(), "expected a fill, got none")
        assertTrue(border.isNotEmpty(), "expected a border, got none")
        assertEquals(theme.palette.card, fill.first().color, "the fill is not bg-card")
    }

    @Test
    fun theRadiusIsTheXlStepNotLg() {
        // Upstream card.tsx is `rounded-xl`. The recipe this replaces used the lg step, which in
        // shadcn v4 is base radius while xl is base + 4px -- so the card was 4px squarer at every
        // radius setting.
        val radius = card().primitivesOf<DrawCommand.RoundedQuad>().first().radius

        assertEquals(theme.radii.xl.value, radius, 0.5f, "the card is not rounded-xl")
        assertTrue(theme.radii.xl.value > theme.radii.lg.value, "the scale itself collapsed xl into lg")
    }

    @Test
    fun contentIsInsetByTheTailwindStepNotARawNumber() {
        // `py-6` is spacing step 6, vertical only -- upstream's Card container carries no
        // horizontal inset at all; that lives on CardHeader/CardContent/CardFooter children.
        // Asserting against Tw rather than 24 means a scale change moves this with it instead of
        // silently disagreeing.
        val frame = composeFrame(300, 200) {
            provideShadcnTheme(theme) {
                ShadcnCard(Modifier.size(200.dp, 100.dp)) { ShadcnText("Inside") }
            }
        }
        val fill = frame.primitivesOf<DrawCommand.RoundedQuad>().first()
        val firstGlyph = frame.primitivesOf<DrawCommand.Glyph>().first()

        assertTrue(
            firstGlyph.y >= fill.y + Tw.Spacing.s6.value - 0.5f,
            "content started at ${firstGlyph.y}, inside the card's own py-6 inset",
        )
    }
}
