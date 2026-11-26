/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import com.awakekt.awake.render.renderer.TextureSampling

/**
 * Full mip chain for this [TextureAsset], base level first, down to a final 1x1 level. Each
 * array layer is downsampled on its own, and every level keeps all layers back to back.
 * Backend-neutral box-filter downsampling -- neither backend has GPU-side mip generation
 * (Vulkan has no `vkCmdBlitImage` binding, WebGPU has no native blit-based mip generation at
 * all), so this generates the whole chain once on the CPU at texture-load time and each
 * backend uploads every level directly (Vulkan: `vkCmdCopyBufferToImage` per mip level,
 * already supports a `mipLevel` target; WebGPU: `queue.writeTexture` per mip level).
 */
fun TextureAsset.mipChain(): List<TextureAsset> = mipChain(mipSkip = 0)

/**
 * Mip chain for this [TextureAsset] starting after skipping up to [mipSkip] top mip levels (per
 * [TextureSampling.levelsToSkip]), down to a final 1x1 level.
 *
 * When skipping levels (`mipSkip > 0` on a linear-filtered texture), downsamples directly from
 * this base level to the first uploaded level in one box-filter pass, avoiding allocating and
 * computing intermediate levels that would immediately be discarded.
 */
fun TextureAsset.mipChain(mipSkip: Int): List<TextureAsset> {
    val totalLevels = mipLevelCount(width, height)
    val skip = TextureSampling.levelsToSkip(filtering, totalLevels, mipSkip)
    var current = if (skip == 0) {
        this
    } else {
        var targetWidth = width
        var targetHeight = height
        repeat(skip) {
            targetWidth = (targetWidth / 2).coerceAtLeast(1)
            targetHeight = (targetHeight / 2).coerceAtLeast(1)
        }
        downsampleLayers(targetWidth, targetHeight)
    }
    val levels = ArrayList<TextureAsset>(totalLevels - skip)
    levels += current
    while (current.width > 1 || current.height > 1) {
        val nextWidth = (current.width / 2).coerceAtLeast(1)
        val nextHeight = (current.height / 2).coerceAtLeast(1)
        current = current.downsampleLayers(nextWidth, nextHeight)
        levels += current
    }
    return levels
}

private fun mipLevelCount(width: Int, height: Int): Int {
    var count = 1
    var w = width
    var h = height
    while (w > 1 || h > 1) {
        w = (w / 2).coerceAtLeast(1)
        h = (h / 2).coerceAtLeast(1)
        count++
    }
    return count
}

private fun TextureAsset.downsampleLayers(newWidth: Int, newHeight: Int): TextureAsset {
    val layerBytes = newWidth * newHeight * 4
    val out = ByteArray(layerBytes * layerCount)
    for (layer in 0 until layerCount) boxDownsample(layerOffset(layer), newWidth, newHeight, out, layer * layerBytes)
    return TextureAsset(out, newWidth, newHeight, layerCount, isCubemap, filtering)
}

private fun TextureAsset.boxDownsample(source: Int, newWidth: Int, newHeight: Int, out: ByteArray, target: Int) {
    for (y in 0 until newHeight) {
        val srcY0 = (y * height) / newHeight
        val srcY1 = (((y + 1) * height) / newHeight).coerceAtLeast(srcY0 + 1).coerceAtMost(height)
        for (x in 0 until newWidth) {
            val srcX0 = (x * width) / newWidth
            val srcX1 = (((x + 1) * width) / newWidth).coerceAtLeast(srcX0 + 1).coerceAtMost(width)
            averageBlock(source, srcX0 until srcX1, srcY0 until srcY1, out, target + (y * newWidth + x) * 4)
        }
    }
}

/** Averages each RGBA channel over one source block into [out] at [outOffset]. */
private fun TextureAsset.averageBlock(source: Int, columns: IntRange, rows: IntRange, out: ByteArray, outOffset: Int) {
    for (channel in 0 until 4) {
        var sum = 0
        for (sy in rows) for (sx in columns) sum += data[source + (sy * width + sx) * 4 + channel].toInt() and 0xFF
        out[outOffset + channel] = (sum / (columns.count() * rows.count())).toByte()
    }
}
