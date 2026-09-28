/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    @Test
    fun discardIfDiscardsOnlyWhereItsConditionHolds() {
        val wgsl = shader("masked_probe") {
            vertex {
                returnPosition(vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit))
            }
            fragment {
                discardIf(position().x lt 1f.lit)
            }
        }.emitWgsl()

        assertTrue(Regex("""if \(position\.x < 1(\.0)?f?\) \{\s*discard;\s*}""").containsMatchIn(wgsl), wgsl)
    }

    /** It used to compile, and emitted an unconditional discard ahead of an empty `if`. */
    @Test
    fun discardFromInsideANestedBlockIsRefused() {
        assertFailsWith<AslDefinitionException> {
            shader("leaky_probe") {
                vertex {
                    returnPosition(vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit))
                }
                fragment {
                    iff(position().x lt 1f.lit) { this@fragment.discard() }
                }
            }
        }
    }
}
