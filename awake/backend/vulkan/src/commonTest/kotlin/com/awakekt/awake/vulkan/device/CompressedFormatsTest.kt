/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.texture.CompressedTextureFormat.Astc4x4
import com.awakekt.awake.render.texture.CompressedTextureFormat.Bc1Rgba
import com.awakekt.awake.render.texture.CompressedTextureFormat.Bc3Rgba
import com.awakekt.awake.render.texture.CompressedTextureFormat.Bc7Rgba
import com.awakekt.awake.render.texture.CompressedTextureFormat.Etc2Rgba8
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures
import kotlin.test.Test
import kotlin.test.assertEquals

class CompressedFormatsTest {

    @Test
    fun eachEnabledFamilyMakesItsFormatsSamplable() {
        assertEquals(emptySet(), compressedFormats(VkPhysicalDeviceFeatures()))
        assertEquals(setOf(Bc1Rgba, Bc3Rgba, Bc7Rgba), compressedFormats(VkPhysicalDeviceFeatures(textureCompressionBC = true)))
        assertEquals(
            setOf(Etc2Rgba8, Astc4x4),
            compressedFormats(VkPhysicalDeviceFeatures(textureCompressionETC2 = true, textureCompressionASTC_LDR = true)),
        )
    }
}
