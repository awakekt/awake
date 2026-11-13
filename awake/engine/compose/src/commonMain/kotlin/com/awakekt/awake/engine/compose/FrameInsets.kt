/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.EdgeInsets
import com.awakekt.awake.compose.ui.platform.WindowInsets
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets

/**
 * Hands this host what covers the window this [frame]: [AppFrame.safeArea] as the system bars and
 * [AppFrame.ime] as the keyboard, so `Modifier.imePadding()` and `safeDrawingPadding()` see them.
 * Call it each frame before [ComposeHost.frame]; every host of a frame, whoever owns it, shares it.
 *
 * [ComposeHost.windowInsets] is rebuilt only when the platform reports a change, so a steady frame
 * allocates nothing.
 */
fun ComposeHost.applyInsets(frame: AppFrame) {
    val current = windowInsets
    if (current.systemBars.matches(frame.safeArea) && current.ime.matches(frame.ime)) return
    windowInsets = WindowInsets(frame.safeArea.toEdges(), frame.ime.toEdges())
}

private fun EdgeInsets.matches(insets: DisplayInsets) =
    left == insets.left && top == insets.top && right == insets.right && bottom == insets.bottom

private fun DisplayInsets.toEdges() = EdgeInsets(left, top, right, bottom)
