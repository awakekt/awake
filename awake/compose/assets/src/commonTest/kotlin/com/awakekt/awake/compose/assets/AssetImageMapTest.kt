/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AssetImageMapTest {
    private val reads = mutableListOf<String>()

    private val source = AssetSource { path ->
        reads += path.value
        if (path.value == "ui/icons.png") Result.success(byteArrayOf(1, 2, 3, 4)) else Result.failure(NoSuchElementException(path.value))
    }

    private fun TestScope.cache() =
        AssetImages(source, backgroundScope) { bytes -> ImageBitmap(1, 1, bytes.copyOf(ImageBitmap.BYTES_PER_PIXEL)) }

    @Test
    fun aPathIsReadOnlyOnceItIsLookedUpAndIsThereOnceDecoded() = runTest {
        val images = cache()
        val map = images.asMap()
        runCurrent()
        assertEquals(emptyList(), reads, "nothing is read before it is asked for")
        assertTrue(map.isEmpty())

        assertNull(map["ui/icons.png"], "the frame that asks draws without it")
        runCurrent()

        assertEquals(listOf("ui/icons.png"), reads)
        assertEquals(0x01020304, assertNotNull(map["ui/icons.png"]).pixel(0, 0))
        assertEquals(setOf("ui/icons.png"), map.keys)
        assertEquals(listOf("ui/icons.png"), reads, "read once")
    }

    @Test
    fun theSeedIsThereAtOnceAndTheSameMapFollowsAReset() = runTest {
        val images = cache()
        val map = images.asMap()
        val seeded = ImageBitmap(1, 1, ByteArray(ImageBitmap.BYTES_PER_PIXEL))
        images.reset(mapOf("ui/frame.png" to seeded))

        assertSame(map, images.asMap())
        assertSame(seeded, map["ui/frame.png"])
        assertTrue(map.containsKey("ui/frame.png"))
        assertEquals(emptyList(), reads, "a seeded image is not read again")
    }

    @Test
    fun aBlankPathIsNeverRead() = runTest {
        val map = cache().asMap()
        assertNull(map[""])
        runCurrent()
        assertEquals(emptyList(), reads)
    }
}
