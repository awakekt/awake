/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnCardTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun card(
        content: (
            context(com.awakekt.awake.compose.runtime.Composer)
            com.awakekt.awake.ui.shadcn.components.ShadcnCardScope.() -> Unit
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
        assertTrue(
            theme.radii.xl.value > theme.radii.lg.value,
            "the scale itself collapsed xl into lg",
        )
    }

    @Test
    fun contentIsInsetByTheTailwindStepNotARawNumber() {
        // Uniform Tw.Spacing.s6 padding across both horizontal and vertical axes ensures
        // raw content and sectioned items have proper resilient insets.
        val frame = composeFrame(300, 200) {
            provideShadcnTheme(theme) {
                ShadcnCard(Modifier.size(200.dp, 100.dp)) { ShadcnText("Inside") }
            }
        }
        val fill = frame.primitivesOf<DrawCommand.RoundedQuad>().first()
        val firstGlyph = frame.primitivesOf<DrawCommand.Glyph>().first()

        assertTrue(
            firstGlyph.y >= fill.y + Tw.Spacing.s6.value - 0.5f,
            "content started at ${firstGlyph.y}, inside the card's own vertical inset",
        )
        assertTrue(
            firstGlyph.x >= fill.x + Tw.Spacing.s6.value - 0.5f,
            "content started at ${firstGlyph.x}, inside the card's own horizontal inset",
        )
    }
}
