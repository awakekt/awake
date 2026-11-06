/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * A phone's or tablet's system chrome: the status and navigation bars on Android, the status bar on
 * iOS. Fullscreen there hides them. [SystemBarsFullscreen] is the one [Fullscreen]
 * over both, and this seam keeps it testable on every target without a device.
 */
internal interface SystemBars {
    /** Whether the bars are hidden now. */
    val hidden: Boolean

    /** Hides the bars, on the platform's UI thread. */
    fun hide()

    /** Shows the bars again, on the platform's UI thread. */
    fun show()
}

/** [Fullscreen] as the system bars hidden. [enter] reports the request handed over, as on the web. */
internal class SystemBarsFullscreen(private val bars: SystemBars) : Fullscreen {
    override val isActive: Boolean
        get() = bars.hidden

    override fun enter(): Boolean {
        if (!isActive) bars.hide()
        return true
    }

    override fun exit() {
        if (isActive) bars.show()
    }
}
