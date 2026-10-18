/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

/** Which way a [ShadcnProgressBar] runs. */
enum class ShadcnProgressOrientation {
    /** Across, filling from the left. */
    Horizontal,

    /** Up, filling from the bottom. */
    Vertical,

    /** Around, as a ring filling clockwise from 12 o'clock. */
    Radial,
}
