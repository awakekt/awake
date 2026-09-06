/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

/**
 * shadcn's alert variants.
 *
 * **Destructive keeps `bg-card`.** Only the text turns red -- `bg-card text-destructive` -- so a
 * destructive alert is a normal card with red type, not a red box. Easy to get wrong from memory,
 * and the kind of thing that looks deliberate once shipped.
 */
enum class ShadcnAlertVariant {
    /** Translates `bg-card text-card-foreground`. */
    Default,

    /** Translates `bg-card text-destructive` -- the fill does not change. */
    Destructive,
}
