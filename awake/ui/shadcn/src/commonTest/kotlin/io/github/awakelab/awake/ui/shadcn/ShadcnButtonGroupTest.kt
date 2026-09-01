/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.BorderSides
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.core.graphics2d.PathCommand
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.graphics2d.bounds
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroupOrientation
import io.github.awakelab.awake.ui.shadcn.components.memberBorderSides
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroup
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class ShadcnButtonGroupTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun horizontalMembersKeepOnlyTheOuterStartAndEndBorders() {
        assertEquals(
            BorderSides(top = true, end = false, bottom = true, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 0, count = 3),
        )
        assertEquals(
            BorderSides(top = true, end = false, bottom = true, start = false),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 1, count = 3),
        )
        assertEquals(
            BorderSides(top = true, end = true, bottom = true, start = false),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 2, count = 3),
        )
    }

    @Test
    fun verticalMembersKeepOnlyTheOuterTopAndBottomBorders() {
        assertEquals(
            BorderSides(top = true, end = true, bottom = false, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Vertical, index = 0, count = 2),
        )
        assertEquals(
            BorderSides(top = false, end = true, bottom = true, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Vertical, index = 1, count = 2),
        )
    }

    @Test
    fun outlineGroupRendersNoDuplicateSharedBorder() {
        val paths = composeFrame(200, 50) {
            provideShadcnTheme(theme) {
                ShadcnButtonGroup {
                    button("Left", variant = ShadcnButtonVariant.Outline)
                    button("Right", variant = ShadcnButtonVariant.Outline)
                }
            }
        }.primitives.filterIsInstance<UiDrawPrimitive.Mesh>()

        assertEquals(2, paths.size)
        val first = paths.first().placedMesh()
        val bounds = first.bounds()
        // A stroked ring has no vertex on its centreline -- they sit half a stroke width either
        // side of it, plus the fringe -- so this asks whether any painted vertex lies within the
        // 2px band at an edge, over a y range well clear of the horizontal edges' own bands.
        fun paintsVerticalEdgeIn(from: Float, to: Float) = first.vertices.any { vertex ->
            vertex.color.a > 0f &&
                vertex.position.x >= from && vertex.position.x <= to &&
                vertex.position.y > bounds.y + 4f &&
                vertex.position.y < bounds.y + bounds.height - 4f
        }
        val startBand = bounds.x to bounds.x + 2f
        val endBand = bounds.x + bounds.width - 2f to bounds.x + bounds.width

        // Positive control: the same predicate must find the edge that IS painted, or the negative
        // assertion below would hold for a mesh that painted nothing at all.
        assertTrue(
            paintsVerticalEdgeIn(startBand.first, startBand.second),
            "the first button lost its outer start border",
        )
        assertTrue(
            !paintsVerticalEdgeIn(endBand.first, endBand.second),
            "the first button still paints the shared end border",
        )
    }

    /**
     * A separated group is as tall as its buttons, not as tall as whatever contains it.
     *
     * The separator is a hairline that fills the cross axis, so without an intrinsic height it read
     * the space the *parent* offered: in the scene hierarchy's header the group became as tall as
     * the panel and pushed every entity row off the bottom of the screen. A height-constrained bar
     * hides this completely, which is why it survived in the toolbar for as long as it did.
     */
    @Test
    fun aSeparatedGroupIsAsTallAsItsButtons() {
        val primitives = composeFrame(FRAME_WIDTH, FRAME_HEIGHT) {
            provideShadcnTheme(theme) {
                // Inside a parent that offers its whole height, which is the case that broke: a
                // sidebar header. A shrink-wrapping parent offers nothing to fill and hides this.
                Column(Modifier.fillMaxHeight()) {
                ShadcnButtonGroup {
                    button("Left", variant = ShadcnButtonVariant.Outline)
                    separator()
                    button("Right", variant = ShadcnButtonVariant.Outline)
                }
                }
            }
        }.primitives

        // The separator paints as a plain quad, not as a mesh -- measuring only the meshes was how
        // the first version of this test passed with the fix removed.
        val tallest = primitives.filterIsInstance<UiDrawPrimitive.Quad>().maxOf { it.h }

        assertTrue(
            tallest < FRAME_HEIGHT / 2,
            "the group filled its parent instead of wrapping its buttons: tallest primitive " +
                "$tallest in a $FRAME_HEIGHT-high frame",
        )
    }

    private companion object {
        const val FRAME_WIDTH = 200

        /** Far taller than a button, so a separator that fills its parent is unmistakable. */
        const val FRAME_HEIGHT = 400
    }
}
