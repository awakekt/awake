/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadMirrorTest {
    private fun UploadMirror.floats(vararg data: Float) = changedFloats(data).let { UploadMirror.first(it) to UploadMirror.count(it) }
    private fun UploadMirror.ints(vararg data: Int) = changedInts(data).let { UploadMirror.first(it) to UploadMirror.count(it) }

    @Test
    fun theFirstUploadWritesEverythingAndAnEqualOneNothing() {
        val mirror = UploadMirror()
        assertEquals(0 to 4, mirror.floats(1f, 2f, 3f, 4f))
        assertEquals(4, mirror.floats(1f, 2f, 3f, 4f).first, "an equal upload is empty")
        assertEquals(0, mirror.floats(1f, 2f, 3f, 4f).second)
    }

    @Test
    fun onlyTheSpanFromTheFirstToTheLastChangeIsWritten() {
        val mirror = UploadMirror()
        mirror.ints(0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(2 to 4, mirror.ints(0, 1, 9, 3, 4, 9, 6, 7))
        assertEquals(0, mirror.ints(0, 1, 9, 3, 4, 9, 6, 7).second, "the copy keeps what was written")
    }

    @Test
    fun growingWritesTheTailAndKeepsThePrefixAcrossTheCopysGrowth() {
        val mirror = UploadMirror()
        mirror.floats(1f, 2f)
        assertEquals(2 to 3, mirror.floats(1f, 2f, 3f, 4f, 5f))
        assertEquals(1 to 1, mirror.floats(1f, 7f, 3f, 4f, 5f))
    }

    @Test
    fun shrinkingWithAnUnchangedPrefixWritesNothingAndInvalidatingWritesAll() {
        val mirror = UploadMirror()
        mirror.floats(1f, 2f, 3f)
        assertEquals(0, mirror.floats(1f, 2f).second)
        mirror.invalidate()
        assertEquals(0 to 2, mirror.floats(1f, 2f))
        assertEquals(0, mirror.floats(Float.NaN, 2f).first, "a NaN differs from 1")
        assertEquals(0, mirror.floats(Float.NaN, 2f).second, "a NaN matches its own bits")
    }

    @Test
    fun releasingFreesTheCopyAndTheNextUploadWritesAll() {
        val mirror = UploadMirror()
        mirror.ints(1, 2, 3)
        mirror.release()
        assertEquals(0 to 3, mirror.ints(1, 2, 3))
    }
}
