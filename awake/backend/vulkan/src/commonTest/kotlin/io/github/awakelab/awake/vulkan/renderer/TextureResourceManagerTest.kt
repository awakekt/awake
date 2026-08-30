/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.renderer

import io.github.awakelab.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals

class TextureResourceManagerTest {
    @Test
    fun neutralAssetsAreCachedAndDestroyedOnce() {
        val destroyed = mutableListOf<Int>()
        val manager = TextureResourceManager<Int> { destroyed += it }
        val asset = TextureAsset(ByteArray(4), 1, 1)

        assertEquals(7, manager.neutral(asset) { 7 })
        assertEquals(7, manager.neutral(asset) { 8 })
        manager.destroy()

        assertEquals(listOf(7), destroyed)
    }

    @Test
    fun releaseRemovesResourceFromShutdownOwnership() {
        val destroyed = mutableListOf<Int>()
        val manager = TextureResourceManager<Int> { destroyed += it }
        val texture = manager.register(3)

        manager.release(texture)
        manager.destroy()

        assertEquals(listOf(3), destroyed)
    }
}
