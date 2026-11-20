/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.io.AssetSource
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Asking the image map its size allocates nothing (#1742).
 *
 * A scene canvas with an asset source reads `images.size` on every poll, which a skip-enabled host
 * makes every frame. `AbstractMap.size` is `entries.size`, and the map's entries were a map of every
 * loaded image built on each call: a frame's garbage, still or not. Escape analysis is off for this
 * module's tests, as on a runtime without it.
 */
class AssetImageMapAllocationTest {
    private val source = AssetSource { path ->
        if (path.value.startsWith("missing/")) Result.failure(NoSuchElementException(path.value)) else Result.success(ByteArray(PIXEL))
    }
    private val images = AssetImages(source, CoroutineScope(Dispatchers.Unconfined)) { bytes -> ImageBitmap(1, 1, bytes) }
    private var sink = 0

    @Test
    fun aPollWithAnAssetSourceAllocatesNothing() {
        images.reset(mapOf("seeded.png" to ImageBitmap(1, 1, ByteArray(PIXEL))))
        val map = images.asMap()
        // One that loads, one that fails, one seeded: what a canvas's elements ask for.
        map["ui/icons.png"]
        map["missing/portrait.png"]
        assertEquals(2, map.size, "the seeded image and the one that loaded")

        repeat(WARM_UP) { sink += map.size }
        val windows = List(WINDOWS) { measure { repeat(POLLS) { sink += map.size } } }
        println("asset image map: bytes per window of $POLLS size reads: $windows")
        assertEquals(0L, windows.min(), "reading the size allocated, minimum of $WINDOWS windows")
    }

    private fun measure(block: () -> Unit): Long {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val before = bean.currentThreadAllocatedBytes
        block()
        return bean.currentThreadAllocatedBytes - before
    }

    private companion object {
        const val PIXEL = 4
        const val WARM_UP = 20_000
        const val POLLS = 10_000
        const val WINDOWS = 7
    }
}
