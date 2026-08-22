// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.platform

import io.github.ronjunevaldoz.awake.compose.runtime.compositionLocalOf
import io.github.ronjunevaldoz.awake.compose.ui.unit.Sp
import io.github.ronjunevaldoz.awake.compose.ui.unit.sp
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.ui.font.UiFonts
import io.github.ronjunevaldoz.awake.ui.theme.TextStyle

/**
 * Pixels per density-independent unit, and the user's text-size preference.
 *
 * `ui-core` reads these from `UiDensity`'s global mutable state; here they are provided, so a
 * subtree can be measured at a different density -- which is what a preview or a snapshot test at
 * 2x needs, and what a global cannot express.
 */
val LocalDensity = compositionLocalOf { 1f }

val LocalFontScale = compositionLocalOf { 1f }

/**
 * Text appearance inherited by descendants.
 *
 * One of the two locals that reach **measurement**: font size and scale decide how wide a string
 * is, so text cannot be measured without them. A composable resolves the value and hands a concrete
 * [TextStyle] to its measure policy -- the policy never reads a local, which is what keeps measuring
 * free of composition state.
 */
val LocalTextStyle = compositionLocalOf { TextStyle(size = DefaultFontSize) }

/**
 * Size text falls back to when nothing provides one.
 *
 * `TextStyle.Default` leaves `size` null for a theme to fill, but the engine has to be able to
 * measure text before any theme exists -- so the local's default carries a concrete size rather
 * than hiding the fallback inside a measure policy.
 */
val DefaultFontSize: Sp = 14.sp

/** The other measurement-reaching local: glyph metrics come from the font. */
val LocalFont = compositionLocalOf<UiFont> { UiFonts.default() }
