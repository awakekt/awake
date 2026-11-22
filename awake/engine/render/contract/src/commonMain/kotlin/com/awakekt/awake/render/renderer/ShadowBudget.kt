/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * What a directional light's shadows cost (#1688): how many cascades split the view, each one more
 * depth pass over its casters, and the side of each cascade's square depth map in texels.
 *
 * [cascades] takes effect on the next planned frame. [mapSize] is the size a backend allocates its
 * shadow maps at; a backend that does not reallocate them yet keeps [Default]'s.
 *
 * @property cascades Cascades the view is split into, 1 to [MAX_CASCADES].
 * @property mapSize Texels on each side of a cascade's depth map, a power of two from [MIN_MAP_SIZE]
 *   to [MAX_MAP_SIZE].
 */
data class ShadowBudget(val cascades: Int = DEFAULT_CASCADES, val mapSize: Int = DEFAULT_MAP_SIZE) {
    init {
        require(cascades in 1..MAX_CASCADES) { "A shadow budget has 1 to $MAX_CASCADES cascades; was $cascades." }
        require(mapSize in MIN_MAP_SIZE..MAX_MAP_SIZE && mapSize and (mapSize - 1) == 0) {
            "A shadow map's side is a power of two from $MIN_MAP_SIZE to $MAX_MAP_SIZE; was $mapSize."
        }
    }

    /** The limits, and the budget every renderer starts at. */
    companion object {
        /** Cascades the lit shaders sample at most: [MAX_SHADOW_CASCADES]. */
        const val MAX_CASCADES: Int = MAX_SHADOW_CASCADES

        /** The cascades a renderer plans by default. */
        const val DEFAULT_CASCADES: Int = 3

        /** A cascade's depth map side by default, in texels. */
        const val DEFAULT_MAP_SIZE: Int = 2048

        /** The smallest depth map side, in texels. */
        const val MIN_MAP_SIZE: Int = 256

        /** The largest depth map side, in texels. */
        const val MAX_MAP_SIZE: Int = 4096

        /** Three cascades of 2048 texels: what every renderer did before shadow budgets. */
        val Default: ShadowBudget = ShadowBudget()

        /** Two cascades of 1024 texels: a phone's, or [RenderQuality.Performance]'s. */
        val Reduced: ShadowBudget = ShadowBudget(cascades = 2, mapSize = 1024)

        /** One cascade of 512 texels (0.5 MiB in D16): budget 4 GB phones that want shadows at minimal GPU cost (#1709). */
        val Minimal: ShadowBudget = ShadowBudget(cascades = 1, mapSize = 512)
    }
}
