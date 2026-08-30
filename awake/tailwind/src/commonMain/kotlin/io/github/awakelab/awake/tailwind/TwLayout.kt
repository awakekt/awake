/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.tailwind

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.ui.Alignment

/**
 * Direct Tailwind flex alignment mappings for Awake Row and Column containers.
 */
public object TwLayout {
    /** `items-center` in a Row (cross-axis centering). */
    public val itemsCenterRow: Alignment.Vertical = Alignment.CenterVertically

    /** `items-center` in a Column / Surface (cross-axis centering). */
    public val itemsCenterColumn: Alignment.Horizontal = Alignment.CenterHorizontally

    /** `justify-center` in a Row (main-axis centering). */
    public val justifyCenterRow: Arrangement.Horizontal = Arrangement.CenterHorizontally

    /** `justify-center` in a Column / Surface (main-axis centering). */
    public val justifyCenterColumn: Arrangement.Vertical = Arrangement.Center
}
