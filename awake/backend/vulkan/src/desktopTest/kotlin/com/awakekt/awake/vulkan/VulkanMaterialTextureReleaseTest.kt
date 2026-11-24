/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Destroying a Vulkan material releases every GPU texture uploaded specifically for it (base color
 * and non-neutral PBR maps) while keeping shared neutral fallback textures intact (#awake-memory-lifecycle).
 */
class VulkanMaterialTextureReleaseTest {
    @Test
    fun destroyingMaterialsReleasesOwnedTexturesAndKeepsSharedNeutrals() = withProbeRenderer(scale = 1f to 1f) { renderer, _ ->
        // Seed the shared neutral PBR textures with a baseline material that stays alive.
        val baseline = renderer.createMaterial(texture = solidAsset(1, 2, 3))
        val baselineCount = renderer.textureResources.count

        // Creating and destroying a textured material with custom PBR maps must return to baseline.
        val custom = renderer.createMaterial(
            texture = solidAsset(10, 20, 30),
            pbrTextures = PbrTextureSet(
                metallicRoughness = solidAsset(40, 50, 60),
                normal = solidAsset(128, 128, 255),
            ),
        )
        assertEquals(baselineCount + 3, renderer.textureResources.count, "base + 2 custom PBR maps uploaded")

        custom.destroy()
        assertEquals(baselineCount, renderer.textureResources.count, "material-owned textures released on destroy")

        // Double destroy must be a safe no-op.
        custom.destroy()
        assertEquals(baselineCount, renderer.textureResources.count, "double destroy keeps shared textures intact")

        // Repeatedly creating and destroying transient materials (e.g., UI ImageBitmaps) never grows count.
        repeat(10) { i ->
            val transient = renderer.createMaterial(texture = solidAsset(i * 10, i * 5, 255))
            assertEquals(baselineCount + 1, renderer.textureResources.count)
            transient.destroy()
            assertEquals(baselineCount, renderer.textureResources.count)
        }

        baseline.destroy()
        assertEquals(baselineCount - 1, renderer.textureResources.count, "baseline base texture released; shared neutrals stay")
    }

    private fun solidAsset(r: Int, g: Int, b: Int): TextureAsset {
        val pixels = ByteArray(SIDE * SIDE * RGBA_BYTES)
        for (i in 0 until SIDE * SIDE) {
            pixels[i * RGBA_BYTES] = r.toByte()
            pixels[i * RGBA_BYTES + 1] = g.toByte()
            pixels[i * RGBA_BYTES + 2] = b.toByte()
            pixels[i * RGBA_BYTES + 3] = ALPHA_OPAQUE
        }
        return TextureAsset(pixels, SIDE, SIDE)
    }

    private companion object {
        const val SIDE = 8
        const val RGBA_BYTES = 4
        const val ALPHA_OPAQUE: Byte = -1
    }
}
