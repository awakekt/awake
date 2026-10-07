/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The emitted text must evaluate the tree it was built from, and WGSL must accept it. WGSL is
 * stricter than C: it refuses to mix `&&` with `||` or to chain comparisons, and reads `--` as
 * a decrement, so the parentheses those need are kept even where C could drop them.
 */
class WgslPrecedenceTest {
    private val a = AslRef("a", GpuDataShape.Float)
    private val b = AslRef("b", GpuDataShape.Float)
    private val c = AslRef("c", GpuDataShape.Float)
    private val i = AslRef("i", AslType.I32)
    private val j = AslRef("j", AslType.I32)
    private val k = AslRef("k", AslType.I32)
    private val p = AslRef("p", AslType.Bool)
    private val q = AslRef("q", AslType.Bool)
    private val r = AslRef("r", AslType.Bool)

    private fun wgsl(expr: AslExpr): String = render(expr, AslStage.Fragment)

    @Test
    fun aNegatedSumKeepsItsParentheses() {
        assertEquals("-(a + b)", wgsl(-(a + b)))
        assertEquals("-(a * b)", wgsl(-(a * b)))
    }

    @Test
    fun aDoubleNegationIsNotADecrement() {
        assertEquals("-(-a)", wgsl(-(-a)))
        assertEquals("-(-1.0)", wgsl(-((-1f).lit)))
    }

    @Test
    fun aNegatedNameNeedsNoParentheses() {
        assertEquals("-a * b", wgsl(-a * b))
        assertEquals("a - -b", wgsl(a - -b))
    }

    @Test
    fun andAndOrAreNeverMixedWithoutParentheses() {
        assertEquals("(p && q) || r", wgsl((p and q) or r))
        assertEquals("p && (q || r)", wgsl(p and (q or r)))
        assertEquals("(p || q) && r", wgsl((p or q) and r))
        assertEquals("p || (q && r)", wgsl(p or (q and r)))
    }

    @Test
    fun aChainOfOneLogicalOperatorReadsWithoutParentheses() {
        assertEquals("p && q && r", wgsl((p and q) and r))
        assertEquals("p || q || r", wgsl((p or q) or r))
    }

    @Test
    fun comparisonsAreNeverChained() {
        assertEquals("(a < b) == p", wgsl((a lt b) eq p))
        assertEquals("p == (a < b)", wgsl(p eq (a lt b)))
        assertEquals("(a < b) == (b < c)", wgsl((a lt b) eq (b lt c)))
    }

    @Test
    fun comparisonsInsideALogicalOperatorNeedNoParentheses() {
        assertEquals("a < b && b <= c", wgsl((a lt b) and (b le c)))
        assertEquals("a + b > c", wgsl((a + b) gt c))
    }

    @Test
    fun aRightOperandKeepsItsParenthesesWhereRegroupingChangesTheValue() {
        assertEquals("a - (b - c)", wgsl(a - (b - c)))
        assertEquals("a - (b + c)", wgsl(a - (b + c)))
        assertEquals("a / (b * c)", wgsl(a / (b * c)))
        // Integer division truncates: i * (j / k) and i * j / k differ for i = 3, j = 5, k = 2.
        assertEquals("i * (j / k)", wgsl(i * (j / k)))
    }

    @Test
    fun aLeftChainAndTighterOperandsStayBare() {
        assertEquals("a + b + c", wgsl((a + b) + c))
        assertEquals("a - b - c", wgsl((a - b) - c))
        assertEquals("a * b + c", wgsl(a * b + c))
        assertEquals("a + b * c", wgsl(a + b * c))
        assertEquals("(a + b) * c", wgsl((a + b) * c))
    }
}
