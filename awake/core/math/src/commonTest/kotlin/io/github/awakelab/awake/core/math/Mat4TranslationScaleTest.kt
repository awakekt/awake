/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** [Mat4.setTranslationScale] exists purely to avoid the five `Mat4` allocations
 * `translate(...).scale(...)` costs per call, so the contract that matters is that it produces
 * a byte-identical matrix. */
class Mat4TranslationScaleTest {

    @Test
    fun matchesTheAllocatingTranslateThenScaleForm() {
        val cases = listOf(
            floatArrayOf(0f, 0f, 0f, 1f),
            floatArrayOf(1f, 2f, 3f, 0.25f),
            floatArrayOf(-4.5f, 0.5f, 7.25f, 2f),
            floatArrayOf(1f, 2f, 3f, 0f),
        )
        for (case in cases) {
            val (x, y, z, scale) = case.toList()
            val expected = Mat4().translate(x, y, z).scale(scale, scale, scale)
            val actual = Mat4().setTranslationScale(x, y, z, scale)
            assertEquals(
                expected.data.toList(),
                actual.data.toList(),
                "setTranslationScale($x, $y, $z, $scale) must match translate().scale()",
            )
        }
    }

    @Test
    fun reusesTheReceiverInsteadOfAllocating() {
        val target = Mat4()
        assertSame(target, target.setTranslationScale(1f, 2f, 3f, 4f))
    }

    @Test
    fun overwritesPreviousContentRatherThanComposing() {
        val target = Mat4().setTranslationScale(9f, 9f, 9f, 9f)
        target.setTranslationScale(1f, 2f, 3f, 0.5f)

        assertEquals(Mat4().setTranslationScale(1f, 2f, 3f, 0.5f).data.toList(), target.data.toList())
    }
}
