/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import kotlin.math.log10

/**
 * Textures the real cook wrote, for the loader to read, in `src/desktopTest/resources/cooked`:
 * `rock.ktx2` (ETC1S), `rock.astc.ktx2` and `rock_normal.ktx2` (UASTC with Zstandard).
 *
 * Provenance: [SIZE] x [SIZE] PNGs of the pattern [argb] draws, the normal map's with
 * `{"assets":{"rock_normal.png":{"role":"Normal"}}}` as the project's `awake.textures.json`, cooked by
 * `./gradlew :awake:asset:texture-cook:cookTextures --args="<project> rock.png rock_normal.png --output <dir>"`.
 * Recook them the same way when the cook's output changes.
 */
internal object CookedTextures {
    const val SIZE = 64

    /** The cooked file named [name], such as `rock.ktx2`. */
    fun bytes(name: String): ByteArray =
        requireNotNull(CookedTextures::class.java.getResourceAsStream("/cooked/$name")) { "no cooked/$name" }.use { it.readBytes() }

    /** The colour source's pixels as RGBA8, bottom row first, as a [com.awakekt.awake.render.texture.TextureAsset] holds them. */
    val sourceRgba: ByteArray by lazy {
        ByteArray(SIZE * SIZE * 4).also { pixels ->
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    val argb = argb(x, y)
                    val at = ((SIZE - 1 - y) * SIZE + x) * 4
                    for (c in 0..2) pixels[at + c] = (argb shr (16 - 8 * c)).toByte()
                    pixels[at + 3] = (argb ushr 24).toByte()
                }
            }
        }
    }

    /** Peak signal-to-noise ratio of [decoded] against [source] over red, green and blue, in dB. */
    fun psnr(source: ByteArray, decoded: ByteArray): Double {
        var squared = 0.0
        for (i in source.indices) {
            if (i % 4 == 3) continue
            val error = (source[i].toInt() and 0xFF) - (decoded[i].toInt() and 0xFF)
            squared += error * error
        }
        return 10 * log10(255.0 * 255.0 / (squared / (source.size / 4 * 3)))
    }

    /** The source image at [x], [y], top row first: smooth gradients with a hard-edged square. */
    private fun argb(x: Int, y: Int): Int {
        val square = x in SIZE / 4 until SIZE / 2 && y in SIZE / 4 until SIZE / 2
        val r = if (square) 255 else x * 255 / (SIZE - 1)
        val g = if (square) 32 else y * 255 / (SIZE - 1)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or 96
    }
}
