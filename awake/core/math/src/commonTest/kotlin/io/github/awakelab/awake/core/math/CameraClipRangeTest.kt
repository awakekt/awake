/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CameraClipRangeTest {
    @Test
    fun `standard preset leaves room beyond required far distance`() {
        val range = CameraClipRange.forRequiredFar(requiredFar = 120f)

        assertEquals(0.1f, range.near)
        assertEquals(240f, range.far)
        assertTrue(range.far > 120f)
    }

    @Test
    fun `range raises near plane to preserve depth precision`() {
        val range = CameraClipRange.forRequiredFar(requiredFar = 100_000f)

        assertEquals(20f, range.near)
        assertEquals(200_000f, range.far)
        assertEquals(10_000f, range.far / range.near)
    }

    @Test
    fun `range rejects invalid required far distance`() {
        assertFailsWith<IllegalArgumentException> {
            CameraClipRange.forRequiredFar(requiredFar = -1f)
        }
    }

    @Test
    fun `preset rejects invalid depth policy`() {
        assertFailsWith<IllegalArgumentException> {
            CameraClipPreset(preferredNear = 0f, farPaddingMultiplier = 1f, maxDepthRangeRatio = 2f)
        }
    }
}
