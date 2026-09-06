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
    Ltr,
    Rtl,
}

/**
 * CompositionLocal providing the current [LayoutDirection].
 */
val LocalLayoutDirection = compositionLocalOf { LayoutDirection.Ltr }
