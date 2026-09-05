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
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroupOrientation
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.memberBorderSides
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnButtonGroupTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun horizontalMembersRetainEndBordersForSeamlessDividers() {
        assertEquals(
            BorderSides(top = true, end = true, bottom = true, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 0, count = 3),
        )
        assertEquals(
            BorderSides(top = true, end = true, bottom = true, start = false),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 1, count = 3),
        )
        assertEquals(
            BorderSides(top = true, end = true, bottom = true, start = false),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 2, count = 3),
        )
        assertEquals(
            BorderSides(top = true, end = false, bottom = true, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Horizontal, index = 0, count = 3, followedBySeparator = true),
        )
    }

    @Test
    fun verticalMembersRetainBottomBordersForSeamlessDividers() {
        assertEquals(
            BorderSides(top = true, end = true, bottom = true, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Vertical, index = 0, count = 2),
        )
        assertEquals(
            BorderSides(top = false, end = true, bottom = true, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Vertical, index = 1, count = 2),
        )
        assertEquals(
            BorderSides(top = true, end = true, bottom = false, start = true),
            memberBorderSides(ShadcnButtonGroupOrientation.Vertical, index = 0, count = 2, followedBySeparator = true),
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
        val boundsFirst = first.bounds()
        val second = paths.last().placedMesh()
        val boundsSecond = second.bounds()

        fun paintsVerticalEdgeIn(
            mesh: io.github.awakelab.awake.core.graphics2d.ColoredTriangleMesh,
            bounds: io.github.awakelab.awake.core.math2d.Rectangle,
            from: Float,
            to: Float,
        ): Boolean = mesh.vertices.any { vertex ->
            vertex.color.a > 0f &&
                vertex.position.x >= from && vertex.position.x <= to &&
                vertex.position.y > bounds.y + 4f &&
                vertex.position.y < bounds.y + bounds.height - 4f
        }
        val firstStart = boundsFirst.x to boundsFirst.x + 2f
        val firstEnd = boundsFirst.x + boundsFirst.width - 2f to boundsFirst.x + boundsFirst.width
        val secondStart = boundsSecond.x to boundsSecond.x + 2f
        val secondEnd = boundsSecond.x + boundsSecond.width - 2f to boundsSecond.x + boundsSecond.width

        // The first button paints both its outer start border and its shared end divider
        assertTrue(
            paintsVerticalEdgeIn(first, boundsFirst, firstStart.first, firstStart.second),
            "the first button lost its outer start border",
        )
        assertTrue(
            paintsVerticalEdgeIn(first, boundsFirst, firstEnd.first, firstEnd.second),
            "the first button must paint the shared divider border",
        )
        // The second button omits its start border so there is no duplicate 2px divider
        assertTrue(
            !paintsVerticalEdgeIn(second, boundsSecond, secondStart.first, secondStart.second),
            "the second button must not paint a duplicate start border",
        )
        assertTrue(
            paintsVerticalEdgeIn(second, boundsSecond, secondEnd.first, secondEnd.second),
            "the second button must paint its outer end border",
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
