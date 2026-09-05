/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.platform

import io.github.awakelab.awake.compose.runtime.compositionLocalOf
import io.github.awakelab.awake.compose.ui.input.pointer.PointerModifiers
import io.github.awakelab.awake.compose.ui.unit.Sp
import io.github.awakelab.awake.compose.ui.unit.sp
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.core.text.theme.TextStyle

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

/**
 * The size the host is laying this composition out at, in pixels.
 *
 * Provided rather than measured, because it is known before composition runs and nothing in the
 * tree can change it. Content that needs real numbers -- a 3D viewport rect carved out of the
 * window, a breakpoint -- reads it here.
 *
 * **This is not `BoxWithConstraints`, and does not replace it.** Compose composes that content
 * *with* its measured constraints, which needs subcomposition -- composing during measure -- and
 * this engine composes the whole tree before measuring any of it. Reporting the previous frame's
 * constraints instead would reintroduce the one-frame lag the input design exists to remove. So
 * this answers the root-sized question exactly and leaves the nested one open.
 */
val LocalViewportSize = compositionLocalOf { ViewportSize(0, 0) }

/** The host's viewport, in pixels. */
data class ViewportSize(val width: Int, val height: Int)

/**
 * Modifier keys held during the frame being composed.
 *
 * Ambient rather than threaded through every `clickable`, because the alternative is a
 * modifier-aware overload of each click entry point and a matching parameter on every component
 * that forwards one -- a wide change to shared code for something only a handful of callers ask.
 *
 * Reading it from a click handler is exact, not approximate: the handler runs inside the same
 * frame's pointer dispatch, so "held now" and "held when the click landed" are the same instant.
 * [PointerEvent.modifiers] carries the same values for a node that handles pointer events itself.
 */
val LocalPointerModifiers = compositionLocalOf { PointerModifiers.None }
