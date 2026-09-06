/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math2d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectangleTest {
    @Test
    fun intersectReturnsOnlySharedArea() {
        assertEquals(
            Rectangle(x = 20f, y = 20f, width = 80f, height = 40f),
            Rectangle(x = 0f, y = 0f, width = 100f, height = 60f)
                .intersect(Rectangle(x = 20f, y = 20f, width = 100f, height = 100f)),
        )
    }

    @Test
    fun containsRecognizesBoundsOnItsEdge() {
        val outer = Rectangle(x = 0f, y = 0f, width = 100f, height = 60f)

        assertTrue(outer.contains(Rectangle(x = 0f, y = 0f, width = 100f, height = 60f)))
        assertFalse(outer.contains(Rectangle(x = 90f, y = 20f, width = 20f, height = 20f)))
    }
}
