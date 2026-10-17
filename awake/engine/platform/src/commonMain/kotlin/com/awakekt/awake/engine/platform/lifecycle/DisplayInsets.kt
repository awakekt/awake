/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

/**
 * What covers the edges of the window the game draws in, in dp from each edge: a notch or camera
 * cutout, rounded corners, a status bar or home indicator drawn over the game, a TV's overscan. The
 * window's host reads it from its platform and hands it to each [AppFrame] as [AppFrame.safeArea],
 * so UI keeps clear of it.
 *
 * @property left How far the safe area starts from the window's left edge, in dp.
 * @property top How far the safe area starts from the window's top edge, in dp.
 * @property right How far the safe area ends from the window's right edge, in dp.
 * @property bottom How far the safe area ends from the window's bottom edge, in dp.
 */
data class DisplayInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    /** These insets with every side held at 0 or more, and anything not a number as 0. */
    fun clamped(): DisplayInsets = DisplayInsets(left.atLeastZero(), top.atLeastZero(), right.atLeastZero(), bottom.atLeastZero())

    private fun Float.atLeastZero(): Float = if (this > 0f && isFinite()) this else 0f

    /** Holds [None]. */
    companion object {
        /** Nothing covered: the whole window is safe, as on a desktop window. */
        val None: DisplayInsets = DisplayInsets()
    }
}
