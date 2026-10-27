/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets

/** Window-edge coverage still overlapping a view after its distances from those edges, all in dp. */
internal fun DisplayInsets.inView(margins: DisplayInsets): DisplayInsets = DisplayInsets(
    left = (left - margins.left).coerceAtLeast(0f),
    top = (top - margins.top).coerceAtLeast(0f),
    right = (right - margins.right).coerceAtLeast(0f),
    bottom = (bottom - margins.bottom).coerceAtLeast(0f),
)
