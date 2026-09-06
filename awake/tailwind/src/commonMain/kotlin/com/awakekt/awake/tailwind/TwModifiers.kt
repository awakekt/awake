/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.tailwind

import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.math2d.Dp

/**
 * Tailwind-style fluent layout extension functions wrapping compose foundation Modifier primitives.
 */

// Padding
fun Modifier.p(all: Dp): Modifier = padding(all)
fun Modifier.px(horizontal: Dp): Modifier = padding(horizontal = horizontal)
fun Modifier.py(vertical: Dp): Modifier = padding(vertical = vertical)
fun Modifier.pt(top: Dp): Modifier = padding(top = top)
fun Modifier.pb(bottom: Dp): Modifier = padding(bottom = bottom)
fun Modifier.pl(start: Dp): Modifier = padding(start = start)
fun Modifier.pr(end: Dp): Modifier = padding(end = end)

// Sizing
fun Modifier.w(width: Dp): Modifier = width(width)
fun Modifier.h(height: Dp): Modifier = height(height)
fun Modifier.sz(size: Dp): Modifier = size(size)
fun Modifier.sz(width: Dp, height: Dp): Modifier = size(width, height)
fun Modifier.wFull(): Modifier = fillMaxWidth()
fun Modifier.hFull(): Modifier = fillMaxHeight()
fun Modifier.sizeFull(): Modifier = fillMaxSize()

// Corner radius shortcuts
fun Modifier.rounded(radius: Dp = Tw.Radius.md): Modifier = this
fun Modifier.roundedSm(): Modifier = rounded(Tw.Radius.sm)
fun Modifier.roundedMd(): Modifier = rounded(Tw.Radius.md)
fun Modifier.roundedLg(): Modifier = rounded(Tw.Radius.lg)
fun Modifier.roundedXl(): Modifier = rounded(Tw.Radius.xl)
fun Modifier.roundedFull(): Modifier = rounded(Tw.Radius.full)
