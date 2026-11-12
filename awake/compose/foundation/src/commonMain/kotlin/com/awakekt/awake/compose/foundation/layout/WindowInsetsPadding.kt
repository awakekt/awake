/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.EdgeInsets
import com.awakekt.awake.compose.ui.platform.LocalWindowInsets
import com.awakekt.awake.compose.ui.unit.dp

/**
 * Pads this node clear of the on-screen keyboard: its height on the bottom edge while it is
 * shown, nothing while hidden. Read during composition from [LocalWindowInsets], so it follows the
 * host's report on the next frame. Does not scroll a focused field into view.
 *
 * Use it alone or [safeDrawingPadding] alone, not stacked with [systemBarsPadding]: the keyboard's
 * height already includes the bar behind it.
 */
context(_: Composer)
fun Modifier.imePadding(): Modifier = paddedBy(LocalWindowInsets.current.ime)

/** Pads this node clear of everything that must stay visible: the system bars and cutouts, and the keyboard. */
context(_: Composer)
fun Modifier.safeDrawingPadding(): Modifier = paddedBy(LocalWindowInsets.current.safeDrawing)

/** Pads this node clear of the system bars, display cutout and rounded corners, not the keyboard. */
context(_: Composer)
fun Modifier.systemBarsPadding(): Modifier = paddedBy(LocalWindowInsets.current.systemBars)

// Nothing covered adds no node at all, so a desktop window pays nothing for a modifier left in place.
private fun Modifier.paddedBy(insets: EdgeInsets): Modifier =
    if (insets.isEmpty) this else padding(insets.left.dp, insets.top.dp, insets.right.dp, insets.bottom.dp)
