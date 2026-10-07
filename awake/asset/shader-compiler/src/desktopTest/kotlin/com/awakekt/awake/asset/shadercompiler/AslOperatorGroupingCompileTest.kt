/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.and
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.eq
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.or
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every grouping the emitter has to parenthesize, in one shader naga validates: a negated sum, a
 * double negation, `&&` mixed with `||`, one comparison read by another, and a regrouping that
 * changes the value. The exact text is pinned by the shader DSL's own tests; this proves it parses.
 */
class AslOperatorGroupingCompileTest {

    private val probe = shader("operator_grouping") {
        vertex {
            returnPosition(vec4(0f.lit, 0f.lit, 0f.lit, 1f.lit))
        }
        fragment {
            val px = let("px", position().x)
            val py = let("py", position().y)
            val i = let("i", 3.lit)
            val j = let("j", 5.lit)
            val k = let("k", 2.lit)
            val near = let("near", px lt 1f.lit)
            discardIf(((px lt py) eq near) or (near and ((py gt 2f.lit) or ((i * (j / k)) eq 6.lit))))
            colorOutput(vec4(-(px + py), -(-px), px - (py - 1f.lit), px / (py * 2f.lit)))
        }
    }

    @Test
    fun theGroupedOperatorsValidate() {
        val wgsl = probe.emitWgsl()

        listOf("-(px + py)", "-(-px)", "(px < py) == near", "near && (py > 2.0 || i * (j / k) == 6)").forEach {
            assertTrue(it in wgsl, "'$it' is missing from:\n$wgsl")
        }
        assertNull(NagaShaderCompiler.validate(wgsl), "naga rejected the grouped operators:\n$wgsl")
    }
}
