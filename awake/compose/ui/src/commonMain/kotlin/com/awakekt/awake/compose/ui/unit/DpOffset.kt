/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

/** A density-independent two-dimensional offset, matching Compose's `DpOffset`. */
data class DpOffset(val x: Dp, val y: Dp) {
    companion object {
        val Zero = DpOffset(0.dp, 0.dp)
    }
}
