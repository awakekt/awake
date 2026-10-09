/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

/**
 * A density-independent two-dimensional offset, matching Compose's `DpOffset`.
 *
 * @property x The horizontal component.
 * @property y The vertical component.
 */
data class DpOffset(val x: Dp, val y: Dp) {
    /** Constants for [DpOffset]. */
    companion object {
        /** An offset of zero on both axes. */
        val Zero = DpOffset(0.dp, 0.dp)
    }
}
