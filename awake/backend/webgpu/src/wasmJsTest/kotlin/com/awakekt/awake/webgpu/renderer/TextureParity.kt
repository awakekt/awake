/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.texture.Texture
import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.fail

/** [level] of [texture] read back as tightly packed RGBA8, bottom row first as uploaded. */
internal suspend fun Renderer.readLevel(texture: Texture, level: Int): TextureAsset =
    readColorPixels(texture.texture, (texture.width shr level).coerceAtLeast(1), (texture.height shr level).coerceAtLeast(1), GPUTextureFormat.RGBA8Unorm, level)

/**
 * Fails at the first texel of [got] whose channel is more than [tolerance] from [want]'s, naming where:
 * the GPU path's texel against the CPU path's.
 */
internal fun assertTexels(name: String, want: TextureAsset, got: TextureAsset, tolerance: Int) {
    assertEquals(want.width to want.height, got.width to got.height, name)
    val first = want.data.indices.firstOrNull { abs((want.data[it].toInt() and 0xFF) - (got.data[it].toInt() and 0xFF)) > tolerance } ?: return
    val at = first - first % 4
    val texel = at / 4
    fail("$name texel $texel (x ${texel % want.width}, row ${texel / want.width} from the bottom): GPU ${rgba(got, at)}, CPU ${rgba(want, at)}")
}

/**
 * Compares every mip level the two textures share: level 0 exact, level n within `(n + 1) / 2` per
 * channel, the rounding `ExternalImageUploadBrowserTest` measured between GPU mips and the CPU chain.
 */
internal suspend fun Renderer.assertSameTexture(name: String, cpu: Texture, gpu: Texture, levels: Int) {
    for (level in 0 until levels) assertTexels("$name level $level", readLevel(cpu, level), readLevel(gpu, level), tolerance = (level + 1) / 2)
}

private fun rgba(asset: TextureAsset, at: Int) = (0..3).joinToString(",", "(", ")") { (asset.data[at + it].toInt() and 0xFF).toString() }
