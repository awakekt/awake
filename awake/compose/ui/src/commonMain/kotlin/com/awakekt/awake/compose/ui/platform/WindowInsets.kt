/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.compose.runtime.compositionLocalOf
import kotlin.math.max

/**
 * How far something covers each edge of the window, in dp from that edge.
 *
 * @property left Dp covered along the left edge.
 * @property top Dp covered along the top edge.
 * @property right Dp covered along the right edge.
 * @property bottom Dp covered along the bottom edge.
 */
data class EdgeInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    /** Whether no edge is covered. */
    val isEmpty: Boolean get() = left <= 0f && top <= 0f && right <= 0f && bottom <= 0f

    /** Holds [None]. */
    companion object {
        /** Nothing covered. */
        val None: EdgeInsets = EdgeInsets()
    }
}

/**
 * What the window's host reports covering its edges: the system bars and cutouts, and the on-screen
 * keyboard. Read through [LocalWindowInsets]; `Modifier.systemBarsPadding`, `safeDrawingPadding` and
 * `imePadding` turn it into padding.
 *
 * Built once when a host reports a change, so reading it each frame allocates nothing.
 *
 * @property systemBars The notch, cutout, rounded corners and system bars drawn over the window.
 * @property ime The on-screen keyboard: its height on the bottom edge while it is shown.
 */
class WindowInsets(val systemBars: EdgeInsets = EdgeInsets.None, val ime: EdgeInsets = EdgeInsets.None) {
    /**
     * What stays clear of anything that must be seen: per side, the larger of [systemBars] and
     * [ime], since the keyboard covers the bar behind it rather than sitting beside it.
     */
    val safeDrawing: EdgeInsets = EdgeInsets(
        max(systemBars.left, ime.left),
        max(systemBars.top, ime.top),
        max(systemBars.right, ime.right),
        max(systemBars.bottom, ime.bottom),
    )

    override fun equals(other: Any?): Boolean = other is WindowInsets && systemBars == other.systemBars && ime == other.ime

    override fun hashCode(): Int = 31 * systemBars.hashCode() + ime.hashCode()

    override fun toString(): String = "WindowInsets(systemBars=$systemBars, ime=$ime)"

    /** Holds [None]. */
    companion object {
        /** Nothing covered, as on a desktop window. */
        val None: WindowInsets = WindowInsets()
    }
}

/**
 * The window's insets, provided by [ComposeHost.windowInsets]; [WindowInsets.None] where the host
 * sets none.
 */
val LocalWindowInsets = compositionLocalOf { WindowInsets.None }
