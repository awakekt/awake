/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.LayoutDirection
import com.awakekt.awake.compose.ui.unit.LocalLayoutDirection
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** [PointAnchor] puts a child's pivot point on a point of its box, moved by an offset. */
class PointAnchorTest {

    private fun place(
        pivot: Alignment,
        offsetX: Dp = 0.dp,
        offsetY: Dp = 0.dp,
        width: Dp = 20.dp,
        height: Dp = 10.dp,
        density: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): List<Int> {
        val out: FrameOutput = ComposeHost().frame(FrameInput(200, 100)) {
            CompositionLocalProvider(LocalDensity provides density, LocalLayoutDirection provides direction) {
                PointAnchor(POINT_X, POINT_Y, pivot = pivot, offsetX = offsetX, offsetY = offsetY) {
                    Box(Modifier.size(width, height).testTag("child"))
                }
            }
        }
        return assertNotNull(out.semantics.findTag("child")).let { listOf(it.x, it.y, it.width, it.height) }
    }

    @Test
    fun bottomCentreStandsTheChildOnThePoint() {
        assertEquals(listOf(40, 20, 20, 10), place(Alignment.BottomCenter))
    }

    @Test
    fun centreCentresTheChildOnThePoint() {
        assertEquals(listOf(40, 25, 20, 10), place(Alignment.Center))
    }

    @Test
    fun topStartHangsTheChildFromItsCornerAndEndReachesBack() {
        assertEquals(listOf(50, 30, 20, 10), place(Alignment.TopStart))
        assertEquals(listOf(30, 20, 20, 10), place(Alignment.BottomEnd))
    }

    @Test
    fun anOffsetMovesTheChildFromThePoint() {
        assertEquals(listOf(45, 12, 20, 10), place(Alignment.BottomCenter, offsetX = 5.dp, offsetY = (-8).dp))
    }

    @Test
    fun theOddPixelOfACentredChildGoesPastThePoint() {
        // 21 pixels about x = 50: ten before it, the middle pixel starting on it, and ten after.
        assertEquals(listOf(40, 20, 21, 10), place(Alignment.BottomCenter, width = 21.dp))
    }

    @Test
    fun thePointAndOffsetAreInDp() {
        assertEquals(listOf(80, 40, 40, 20), place(Alignment.BottomCenter, density = 2f))
        assertEquals(listOf(90, 44, 40, 20), place(Alignment.BottomCenter, offsetX = 5.dp, offsetY = 2.dp, density = 2f))
    }

    @Test
    fun aStartPivotIsTheRightEdgeRightToLeft() {
        assertEquals(listOf(30, 20, 20, 10), place(Alignment.BottomStart, direction = LayoutDirection.Rtl))
    }

    private companion object {
        val POINT_X = 50.dp
        val POINT_Y = 30.dp
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
