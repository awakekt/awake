/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.theme

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current

/**
 * How much room shadcn components take, set once for a subtree.
 *
 * [Default] is shadcn/ui's own geometry, which every recipe is checked against. [Compact] is for a
 * dense UI such as an editor: every component's *default* size drops a step, so the UI does not have
 * to pass a small size at each call.
 *
 * It scales **size and spacing only**. Colours, radii and the type scale are the theme's, and do not
 * change; the one typographic step is `text-xs` for form labels and descriptions where shadcn uses
 * `text-sm`.
 *
 * A size passed explicitly (`size = ShadcnButtonSizeVariant.Lg`) always wins over the density.
 */
enum class ShadcnDensity {
    /** shadcn/ui's own sizes: a 36dp button, input and select, 32dp menu rows. */
    Default,

    /** One step tighter: a 32dp button, 28dp input and select, 28dp menu rows. */
    Compact,
}

/**
 * The density in scope. [ShadcnDensity.Default] when nothing provides one, so a UI that never
 * mentions density is unchanged.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalShadcnDensity provides ShadcnDensity.Compact) {
 *     ShadcnButton("Apply")   // 32dp, as `size = Sm`
 * }
 * ```
 */
val LocalShadcnDensity: CompositionLocal<ShadcnDensity> = compositionLocalOf { ShadcnDensity.Default }

/** The density in scope, spelled like `shadcnTheme` is. */
context(_: Composer)
internal val shadcnDensity: ShadcnDensity get() = LocalShadcnDensity.current

internal val ShadcnDensity.isCompact: Boolean get() = this == ShadcnDensity.Compact
