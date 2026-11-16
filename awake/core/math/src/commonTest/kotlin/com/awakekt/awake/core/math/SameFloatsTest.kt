/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals

/** [sameFloats] answers as `contentEquals` does, without boxing on Kotlin/Wasm (#1621). */
class SameFloatsTest {
    @Test
    fun itAgreesWithContentEqualsIncludingNaNAndSignedZero() {
        val cases = listOf(
            floatArrayOf(1f, 2f, 3f) to floatArrayOf(1f, 2f, 3f),
            floatArrayOf(1f, 2f, 3f) to floatArrayOf(1f, 2f, 4f),
            floatArrayOf(1f, 2f) to floatArrayOf(1f, 2f, 3f),
            floatArrayOf(Float.NaN) to floatArrayOf(Float.NaN),
            floatArrayOf(0f) to floatArrayOf(-0f),
            FloatArray(0) to FloatArray(0),
        )
        for ((a, b) in cases) {
            assertEquals(a.contentEquals(b), a.sameFloats(b), "${a.toList()} vs ${b.toList()}")
        }
    }
}
