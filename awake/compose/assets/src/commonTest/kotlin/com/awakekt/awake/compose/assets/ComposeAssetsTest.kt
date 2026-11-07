/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.reconcile
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ComposeAssetsTest {

    private class TestNode : RememberHolder {
        private val slots = mutableListOf<Any?>()
        override val rememberSlots: List<Any?> get() = slots
    }

    private class TestApplier : Applier {
        override fun childCount(slot: Slot) = 0
        override fun typeAt(slot: Slot, index: Int): Any? = null
        override fun keyAt(slot: Slot, index: Int): Any? = null
        override fun nodeAt(slot: Slot, index: Int): Any = Unit
        override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any = Unit
        override fun moveTo(slot: Slot, from: Int, to: Int) = Unit
        override fun truncateFrom(slot: Slot, index: Int) = Unit
        override fun down(slot: Slot, index: Int) = Unit
        override fun up() = Unit
    }

    private val root = TestNode()

    /** One frame: Awake Compose composes the whole tree each one. */
    private fun frame(content: context(Composer) () -> Unit) = reconcile(TestApplier(), root, content)

    private val source = AssetSource { path ->
        if (path.value == "ui/frame.png") Result.success(byteArrayOf(1, 2, 3, 4)) else Result.failure(NoSuchElementException(path.value))
    }

    @Test
    fun theUiShowsTheImageOnAFrameAfterItLoads() = runTest {
        val images = AssetImages(source, backgroundScope) { bytes -> ImageBitmap(1, 1, bytes) }
        var shown: ImageBitmap? = null
        val ui: context(Composer) () -> Unit = { ProvideAssets(source, images) { shown = rememberAssetImage("ui/frame.png") } }

        frame(ui)
        assertNull(shown)
        runCurrent()
        frame(ui)

        assertSame(images["ui/frame.png"], shown)
        assertEquals(0x01020304, shown?.pixel(0, 0))
    }

    @Test
    fun theProvidedSourceIsReadableOnItsOwn() = runTest {
        var provided: AssetSource? = null
        frame { ProvideAssets(source) { provided = LocalAssetSource.current } }

        assertSame(source, provided)
    }

    @Test
    fun withNoCacheProvidedAnImageIsNullAndNothingThrows() = runTest {
        var shown: ImageBitmap? = ImageBitmap(1, 1, ByteArray(ImageBitmap.BYTES_PER_PIXEL))

        frame { shown = rememberAssetImage("ui/frame.png") }

        assertNull(shown)
    }

    @Test
    fun aMissingPathShowsNothingAndTheFrameGoesOn() = runTest {
        val images = AssetImages(source, backgroundScope) { bytes -> ImageBitmap(1, 1, bytes) }
        var frames = 0
        val ui: context(Composer) () -> Unit = {
            ProvideAssets(source, images) {
                assertNull(rememberAssetImage("ui/missing.png"))
                frames++
            }
        }

        frame(ui)
        runCurrent()
        frame(ui)

        assertEquals(2, frames)
    }
}
