/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val red = Color(1f, 0f, 0f, 1f)

/**
 * Every variant compares the same way, and equal ones hash the same.
 *
 * Seven of the eleven variants used to hand-write `equals`/`hashCode` on a `data class`, which
 * generates both. The four that did not ended up with different semantics from the seven that did --
 * and `Quad`'s pair was outright broken: `0.0f` and `-0.0f` compared **equal** with **different**
 * hash codes, which is the contract violation that silently corrupts any `Set` or `distinct()`.
 * Nothing hashed a draw command at the time, so it was a landmine rather than a fire.
 */
class DrawCommandEqualityTest {

    private fun quad(x: Float) = UiDrawPrimitive.Quad(x, 0f, 1f, 1f, red)

    private fun texture(x: Float) = UiDrawPrimitive.Texture(x, 0f, 1f, 1f, material = "m")

    private fun clip(x: Float) = UiDrawPrimitive.ClipPush(Rectangle(x, 0f, 1f, 1f))

    @Test
    fun equalCommandsHashTheSame() {
        // The contract. `Quad` broke it before the hand-written pair was deleted.
        val a = quad(0.0f)
        val b = quad(0.0f)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun everyVariantAgreesOnSignedZero() {
        // Previously `Quad` said equal and `Texture`/`ClipPush` said not -- one sealed type, two
        // answers, depending which variant you happened to hold.
        assertNotEquals(quad(0.0f), quad(-0.0f))
        assertNotEquals(texture(0.0f), texture(-0.0f))
        assertNotEquals(clip(0.0f), clip(-0.0f))
    }

    @Test
    fun everyVariantAgreesOnNaN() {
        val nan = Float.NaN

        assertEquals(quad(nan), quad(nan))
        assertEquals(texture(nan), texture(nan))
        assertEquals(clip(nan), clip(nan))
    }

    @Test
    fun aCommandCanBeUsedInAHashSet() {
        // What the broken contract would have corrupted the first time anyone tried it.
        val set = hashSetOf(quad(1f), quad(1f), quad(2f))

        assertEquals(2, set.size)
        assertTrue(quad(1f) in set)
    }

    @Test
    fun differingFieldsStillCompareUnequal() {
        assertNotEquals(quad(1f), quad(2f))
        assertNotEquals(
            UiDrawPrimitive.Quad(0f, 0f, 1f, 1f, red),
            UiDrawPrimitive.Quad(0f, 0f, 1f, 1f, Color(0f, 1f, 0f, 1f)),
        )
    }
}
