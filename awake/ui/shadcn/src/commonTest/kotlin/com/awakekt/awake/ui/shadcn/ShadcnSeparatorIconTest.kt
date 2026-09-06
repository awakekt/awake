/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.heroicons.icon.HeroIcons
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparatorOrientation
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnSeparatorIconTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun aHorizontalSeparatorIsOneUnitTallAndFillsItsWidth() {
        val frame = composeFrame(200, 60) {
            provideShadcnTheme(theme) { ShadcnSeparator() }
        }
        val rule = frame.primitivesOf<DrawCommand.Quad>().first()

        assertEquals(1f, rule.h, 0.5f, "upstream's `h-px` is one density-independent unit")
        assertEquals(200f, rule.w, 0.5f, "`w-full` did not fill")
        assertEquals(theme.palette.border, rule.color, "the rule is not bg-border")
    }

    @Test
    fun aVerticalSeparatorSwapsTheAxes() {
        // Inside a Row, not the harness's Column: a Column loosens its main axis for measuring, so
        // `fillMaxHeight` there resolves against an unbounded constraint and collapses to zero. That
        // is the engine behaving correctly -- a vertical rule needs a parent that bounds its height,
        // which is the Row it would sit in anyway.
        val frame = composeFrame(200, 60) {
            provideShadcnTheme(theme) {
                Row(Modifier.fillMaxWidth().height(60.dp)) {
                    ShadcnSeparator(orientation = ShadcnSeparatorOrientation.Vertical)
                }
            }
        }
        val rule = frame.primitivesOf<DrawCommand.Quad>().first()

        assertEquals(1f, rule.w, 0.5f, "`w-px` did not apply on the vertical axis")
        assertEquals(60f, rule.h, 0.5f, "`h-full` did not fill its bounded parent")
    }

    @Test
    fun anIconRendersItsVectorAsATessellatedMesh() {
        // The first ported recipe to draw a vector rather than rects or glyphs -- the thing an
        // earlier pass wrongly recorded as blocked on a missing primitive. It arrives as triangles
        // rather than a path because `rememberVectorPainter` tessellates it once and redraws that;
        // see VectorPainter for why an icon must not be re-tessellated per frame.
        val frame = composeFrame(64, 64) {
            provideShadcnTheme(theme) { ShadcnIcon(HeroIcons.Solid20Mini.chevronDown) }
        }

        assertTrue(
            frame.primitivesOf<DrawCommand.Mesh>().isNotEmpty(),
            "the icon drew no vector",
        )
    }

    @Test
    fun anIconTakesItsTint() {
        val red = Color(1f, 0f, 0f, 1f)
        val frame = composeFrame(64, 64) {
            provideShadcnTheme(theme) { ShadcnIcon(HeroIcons.Solid20Mini.chevronDown, tint = red) }
        }

        assertEquals(listOf(red), frame.meshColors())
    }

    @Test
    fun anExplicitSizeScalesTheDrawnVector() {
        // Asserted on the painted extent rather than the node's own width: the vector is fitted into
        // the slot, so a size that reached the node but not `fitTo` would still measure right and
        // draw wrong.
        fun extent(size: Dp): Float {
            val frame = composeFrame(128, 128) {
                provideShadcnTheme(theme) {
                    ShadcnIcon(
                        HeroIcons.Solid20Mini.chevronDown,
                        size = size,
                    )
                }
            }
            val xs = frame.meshPoints().map { it.x }
            return xs.max() - xs.min()
        }

        assertTrue(extent(32.dp) > extent(16.dp), "a larger size drew the same vector extent")
    }
}
