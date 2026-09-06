/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AlignmentTest {

    @Test
    fun horizontalAlignmentsSpanTheAvailableSpace() {
        assertEquals(0, Alignment.Start.align(size = 20, space = 100))
        assertEquals(40, Alignment.CenterHorizontally.align(size = 20, space = 100))
        assertEquals(80, Alignment.End.align(size = 20, space = 100))
    }

    @Test
    fun verticalAlignmentsSpanTheAvailableSpace() {
        assertEquals(0, Alignment.Top.align(size = 20, space = 100))
        assertEquals(40, Alignment.CenterVertically.align(size = 20, space = 100))
        assertEquals(80, Alignment.Bottom.align(size = 20, space = 100))
    }

    @Test
    fun aChildLargerThanItsSpaceIsNotPushedOffTheLeadingEdge() {
        // Centring a 100px child in 60px would otherwise place it at -20, clipping its start.
        assertEquals(-20, Alignment.CenterHorizontally.align(size = 100, space = 60))
        assertEquals(-40, Alignment.End.align(size = 100, space = 60))
    }

    @Test
    fun centringAnOddRemainderFavoursTheLeadingEdge() {
        // 100 - 25 = 75, halved to 37 not 38. Int layout has to pick a side; picking consistently
        // is what keeps a centred child from jittering by a pixel as its container resizes.
        assertEquals(37, Alignment.CenterHorizontally.align(size = 25, space = 100))
    }
}
