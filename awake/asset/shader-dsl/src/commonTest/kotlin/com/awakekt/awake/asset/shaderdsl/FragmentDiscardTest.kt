/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import kotlin.test.Test
import kotlin.test.assertTrue

class FragmentDiscardTest {
    @Test
    fun emitsFragmentDiscardStatement() {
        val shader = shader("masked_depth_probe") {
            vertex {
                returnPosition(vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit))
            }
            fragment {
                discard()
            }
        }

        assertTrue(shader.emitWgsl().contains("discard;"))
    }
}
