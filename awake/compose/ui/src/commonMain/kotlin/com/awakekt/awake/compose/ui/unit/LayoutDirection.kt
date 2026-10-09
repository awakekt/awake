/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import com.awakekt.awake.compose.runtime.compositionLocalOf

/**
 * Layout direction for horizontal placement and alignment resolution.
 */
enum class LayoutDirection {
    /** Content flows from left to right; the start edge is the left. */
    Ltr,

    /** Content flows from right to left; the start edge is the right. */
    Rtl,
}

/**
 * CompositionLocal providing the current [LayoutDirection].
 */
val LocalLayoutDirection = compositionLocalOf { LayoutDirection.Ltr }
