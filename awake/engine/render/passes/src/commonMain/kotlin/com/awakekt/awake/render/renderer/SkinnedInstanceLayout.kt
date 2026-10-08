/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.geometry.GpuDataShape

/** Shared storage ABI: unchanged fixed-stride joint palettes followed by a separate RGBA array. */
object SkinnedInstanceLayout {
    /** Storage binding for the fixed-stride joint palette array. */
    const val PALETTE_BINDING = 0

    /** Storage binding for the separate RGBA array. */
    const val TINT_BINDING = 1

    /** Floats in one palette record, independent of the number of populated joints. */
    val PALETTE_FLOATS: Int = MAX_JOINTS * GpuDataShape.Mat4.componentCount

    /** Floats in one tint record. */
    val TINT_FLOATS: Int = GpuDataShape.Vec4.componentCount
}
