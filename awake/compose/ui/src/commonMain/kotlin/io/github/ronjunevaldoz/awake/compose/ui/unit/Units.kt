// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.unit

import io.github.ronjunevaldoz.awake.core.math2d.Dp as GraphicsDp
import io.github.ronjunevaldoz.awake.core.math2d.Sp as GraphicsSp

/**
 * One import surface for the engine's units, matching Compose's `androidx.compose.ui.unit`.
 *
 * These are aliases, not new types: `Dp` here **is** `io.github.ronjunevaldoz.awake.core.math2d.Dp`, so
 * there is exactly one `Dp` in the tree and no conversion anywhere. `:awake:ui:graphics` keeps
 * owning the declaration because 242 files import it from `ui.api` today, and those same imports
 * already change in the planned `io.github.awakelab.*` rename -- moving now would churn them twice.
 *
 * The alias is the seam: when that rename happens, the target changes here, in one file, instead of
 * at every call site in `:awake:compose:*`. See `docs/reference/compose-engine/14-density-resize.md`.
 */
typealias Dp = GraphicsDp

typealias Sp = GraphicsSp

val Float.dp: Dp get() = GraphicsDp(this)

val Int.dp: Dp get() = GraphicsDp(toFloat())

val Float.sp: Sp get() = GraphicsSp(this)

val Int.sp: Sp get() = GraphicsSp(toFloat())
