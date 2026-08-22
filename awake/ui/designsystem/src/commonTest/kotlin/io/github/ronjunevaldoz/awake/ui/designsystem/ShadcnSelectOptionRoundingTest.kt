// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.designsystem

import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.ui.designsystem.components.shadcnSelect
import io.github.ronjunevaldoz.awake.ui.headless.column
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A hovered option must paint with the same rounding a selected one does.
 *
 * The two came from different styles -- `shadcnSelectOptionStyle` carried the hover/active fill,
 * `shadcnSelectedOptionStyle` carried `shape(sm)` -- so the hover highlight painted square
 * corners inside a rounded menu while the selected row above it was rounded. shadcn has one
 * shape for both: `SelectItem` is `rounded-sm` and its highlight is a `focus:bg-accent` fill on
 * that same rounded element.
 *
 * Asserted on the emitted primitive rather than the style value, because the defect is only
 * visible after resolution: a shapeless fill emits `Quad`, a rounded one emits `RoundedQuad`.
 */
class ShadcnSelectOptionRoundingTest {

    @Test
    fun hoveredOptionPaintsRounded() {
        shadcnTestSession(width = 240f, height = 260f) {
            fun render(x: Float, y: Float) = frame(x = x, y = y) {
                column {
                    shadcnSelect(id = SELECT_ID, options = OPTIONS, selectedIndex = 0)
                }
            }

            val trigger = render(-100f, -100f).bounds(SELECT_ID)
            click(trigger.x + trigger.width / 2f, trigger.y + trigger.height / 2f) {
                column {
                    shadcnSelect(id = SELECT_ID, options = OPTIONS, selectedIndex = 0)
                }
            }

            // Row 1, not row 0: row 0 is the selected one, already rounded by its own style, so
            // it cannot catch this regression.
            val row = render(-100f, -100f).node("$SELECT_ID.option1").bounds
            val hovered = render(row.x + row.width / 2f, row.y + row.height / 2f)

            val roundedOverRow = hovered.primitivesOf<UiDrawPrimitive.RoundedQuad>().any {
                it.h <= row.height + 2f && it.y >= row.y - 2f && it.y <= row.y + 2f
            }
            assertTrue(
                roundedOverRow,
                "hovered option row painted no RoundedQuad of its own height -- the hover fill " +
                    "lost shape(sm), so it renders square corners beside a rounded selected row",
            )
        }
    }

    private companion object {
        const val SELECT_ID = "rounding-select"
        val OPTIONS = listOf("Alpha", "Beta")
    }
}
