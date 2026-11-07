/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

/**
 * The rectangle of samples one bake covers: [width] by [depth], [sampleSize] apart, with sample
 * (0, 0) at world ([originX], [originZ]).
 *
 * @property originX World X of sample column 0.
 * @property originZ World Z of sample row 0.
 * @property width Samples along X.
 * @property depth Samples along Z.
 * @property sampleSize Metres between samples.
 */
class NavRegion(val originX: Float, val originZ: Float, val width: Int, val depth: Int, val sampleSize: Float) {
    init {
        require(width > 0 && depth > 0) { "NavRegion size must be positive; was $width x $depth." }
        require(sampleSize > 0f && sampleSize.isFinite()) { "NavRegion sampleSize must be positive and finite; was $sampleSize." }
    }
}
