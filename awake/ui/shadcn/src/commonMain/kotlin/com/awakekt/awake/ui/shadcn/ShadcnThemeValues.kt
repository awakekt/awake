/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.ui.shadcn.theme.ShadcnMetrics

/** Complete design-system theme supplied to a UI subtree. */
data class ShadcnThemeValues(
    val resolved: ShadcnResolvedTheme,
) : ShadcnResolvedTheme by resolved {
    /** Resolved theme values. */
    val core get() = resolved
    override val metrics: ShadcnMetrics get() = resolved.metrics
}
