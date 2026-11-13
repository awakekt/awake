/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadShadowTest {
    private fun UploadShadow.floats(vararg data: Float) = changedFloats(data).let { UploadShadow.first(it) to UploadShadow.count(it) }
    private fun UploadShadow.ints(vararg data: Int) = changedInts(data).let { UploadShadow.first(it) to UploadShadow.count(it) }

    @Test
    fun theFirstUploadWritesEverythingAndAnEqualOneNothing() {
        val shadow = UploadShadow()
        assertEquals(0 to 4, shadow.floats(1f, 2f, 3f, 4f))
        assertEquals(4, shadow.floats(1f, 2f, 3f, 4f).first, "an equal upload is empty")
        assertEquals(0, shadow.floats(1f, 2f, 3f, 4f).second)
    }

    @Test
    fun onlyTheSpanFromTheFirstToTheLastChangeIsWritten() {
        val shadow = UploadShadow()
        shadow.ints(0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(2 to 4, shadow.ints(0, 1, 9, 3, 4, 9, 6, 7))
        assertEquals(0, shadow.ints(0, 1, 9, 3, 4, 9, 6, 7).second, "the copy keeps what was written")
    }

    @Test
    fun growingWritesTheTailAndKeepsThePrefixAcrossTheCopysGrowth() {
        val shadow = UploadShadow()
        shadow.floats(1f, 2f)
        assertEquals(2 to 3, shadow.floats(1f, 2f, 3f, 4f, 5f))
        assertEquals(1 to 1, shadow.floats(1f, 7f, 3f, 4f, 5f))
    }

    @Test
    fun shrinkingWithAnUnchangedPrefixWritesNothingAndInvalidatingWritesAll() {
        val shadow = UploadShadow()
        shadow.floats(1f, 2f, 3f)
        assertEquals(0, shadow.floats(1f, 2f).second)
        shadow.invalidate()
        assertEquals(0 to 2, shadow.floats(1f, 2f))
        assertEquals(0, shadow.floats(Float.NaN, 2f).first, "a NaN differs from 1")
        assertEquals(0, shadow.floats(Float.NaN, 2f).second, "a NaN matches its own bits")
    }
}
