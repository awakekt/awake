/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Negative control: keeping only the endpoint colours fails the first interior stop, [255,82,82] instead of green. */
class GradientBackgroundTest {
    @Test
    fun fourEvenStopsFollowTheNodesPlacementAndKeepAnInputSnapshot() {
        val colors = mutableListOf(Color.fromHex("#FF0000"), Color.fromHex("#00FF00"), Color.fromHex("#0000FF"), Color.White)
        val brush = Brush.horizontalGradient(colors)
        colors[1] = Color.Black
        val pixels = ComposeHost().frame(FrameInput(96, 48)) {
            Box(Modifier.padding(7.dp)) {
                Spacer(Modifier.width(63.dp).height(20.dp).background(brush))
            }
        }.primitives.rasterize(96, 48, Color.Black)
        fun rgb(x: Int): List<Int> = (0..2).map { pixels[(16 * 96 + x) * 4 + it].toInt() and 255 }
        assertTrue(rgb(3).all { it == 0 }, "nothing draws before the child's origin")
        assertTrue(rgb(7)[0] > 240, "red at the child's left edge")
        assertTrue(rgb(27)[1] > 240, "the first interior stop remains green after the input list changes: ${rgb(27)}")
        assertTrue(rgb(49)[2] > 240 && rgb(49)[0] < 20, "the second interior stop is blue")
        assertTrue(rgb(69).all { it > 240 }, "white at the child's right edge")
    }

    @Test
    fun aGradientWithNoIntervalFailsBeforeDrawing() {
        assertFailsWith<IllegalArgumentException> { Brush.horizontalGradient(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Brush.verticalGradient(listOf(Color.White)) }
    }
}
