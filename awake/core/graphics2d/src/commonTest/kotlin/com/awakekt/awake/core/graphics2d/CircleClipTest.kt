/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertTrue

/** Negative control: 116 of these 196 circle clips were rejected before the seam fixes. */
class CircleClipTest {
    @Test
    fun circlesRemainConvexAcrossSizesScalesAndOrigins() {
        val failures = mutableListOf<String>()
        val diameters = listOf(32f, 64f, 128f, 137f, 192f, 256f, 512f).flatMap { size ->
            listOf(0.75f, 0.83f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { scale -> size * scale }
        }
        for (diameter in diameters) {
            for (origin in listOf(0f, 13.25f, 141.5f, 1024f)) {
                val path = DrawShape.Circle.toPath(Rectangle(origin, origin, diameter, diameter))
                if (path.convexClipContour() == null) failures += "diameter=$diameter origin=$origin"
            }
        }
        assertTrue(failures.isEmpty(), "Circle clips were discarded:\n${failures.joinToString("\n")}")
    }
}
