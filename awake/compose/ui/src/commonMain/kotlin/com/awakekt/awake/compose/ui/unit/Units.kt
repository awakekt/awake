/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import com.awakekt.awake.core.math2d.Dp as GraphicsDp
import com.awakekt.awake.core.math2d.Sp as GraphicsSp

/**
 * One import surface for the engine's units, matching Compose's `androidx.compose.ui.unit`.
 *
 * These are aliases, not new types: `Dp` here **is** `com.awakekt.awake.core.math2d.Dp`, so
 * there is exactly one `Dp` in the tree and no conversion anywhere. `:awake:core:math2d` keeps
 * owning the declaration since it's a math/geometry primitive, not a UI-framework concept -- non-UI
 * consumers (`DrawShape.kt`, `DrawStroke.kt`, render-pass code) use it without depending on the
 * compose engine at all. Aliasing here also means the `com.awakekt.awake.*` package rename
 * only has to change this one file, not every call site across `:awake:compose:*`.
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
