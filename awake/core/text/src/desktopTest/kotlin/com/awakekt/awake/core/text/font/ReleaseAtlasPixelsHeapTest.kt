/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the bundled weighted family holds for its atlas: the combined atlas and the seven face atlases
 * it is stacked from, about 31 MB, until a renderer that has uploaded it releases them (#1619).
 */
class ReleaseAtlasPixelsHeapTest {
    @Test
    fun theWeightedFamilysAtlasPixelsLeaveTheHeapOnRelease() {
        val family = UiFonts.weightedSans()
        val baseline = heapInUse()
        val combinedBytes = family.atlasPixelsRgba.size.toLong()
        val read = heapInUse() - baseline

        family.releaseAtlasPixels()
        val released = heapInUse() - baseline

        assertTrue(read >= combinedBytes * 3 / 2, "the family holds its combined atlas and its faces': $read bytes for a $combinedBytes byte atlas")
        assertTrue(released < combinedBytes / 2, "the atlas pixels are released: $released bytes still held")
    }

    // The heap in use once everything unreachable is collected, so two readings differ by what is retained.
    private fun heapInUse(): Long {
        val runtime = Runtime.getRuntime()
        repeat(GC_PASSES) { System.gc() }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private companion object {
        const val GC_PASSES = 3
    }
}
