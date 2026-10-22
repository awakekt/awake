/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

/**
 * How overflowing text should be handled.
 */
enum class TextOverflow {
    /**
     * Clip the overflowing text to fix its container.
     */
    Clip,

    /**
     * Use an ellipsis (`…`) to indicate that the text has overflowed.
     */
    Ellipsis,
}
