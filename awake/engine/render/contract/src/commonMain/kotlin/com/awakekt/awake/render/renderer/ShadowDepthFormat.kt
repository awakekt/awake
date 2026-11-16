/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * The depth format a backend stores its shadow maps in (#1698). Chosen when the engine starts: the
 * depth pass's pipelines are built for one format.
 *
 * [Depth32Float] is the default and what every renderer used before this choice existed.
 * [Depth16Unorm] halves the memory for a mobile tier: 1024 texels on a side cost 2 MiB a layer in it,
 * against 16 MiB for a 2048 [Depth32Float] layer. It resolves depth more coarsely, so the shadow
 * pass's rasterizer bias steps are coarser too.
 *
 * @property bytesPerTexel What one texel of the format occupies.
 */
enum class ShadowDepthFormat(val bytesPerTexel: Int) {
    /** 32-bit float depth. The default. */
    Depth32Float(4),

    /** 16-bit normalized depth: half the memory of [Depth32Float], for mobile tiers. */
    Depth16Unorm(2),
    ;

    /**
     * The bytes [layers] square layers of [mapSize] texels a side hold in this format, before any
     * driver alignment: what a shadow array costs.
     */
    fun layerBytes(mapSize: Int, layers: Int): Long = mapSize.toLong() * mapSize * bytesPerTexel * layers

    /** The format every renderer starts with. */
    companion object {
        /** [Depth32Float]. */
        val Default: ShadowDepthFormat = Depth32Float
    }
}
