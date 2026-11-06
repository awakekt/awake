/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import android.app.Activity
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * The [activity]'s [Fullscreen]: immersive mode, with the status and navigation bars hidden. A swipe
 * from the edge shows them for a moment without leaving fullscreen
 * (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), and the surface follows the change of insets as a resize.
 *
 * Any thread may call it. The game loop runs on the surface's render thread, so [Fullscreen.enter]
 * and [Fullscreen.exit] hand the change to the activity's UI thread, and it shows in
 * [Fullscreen.isActive] a frame or two later. [Fullscreen.isActive] reads the window's insets each
 * time, because the system can bring the bars back by itself, such as when another window takes focus.
 * [androidHostActions] given an `Activity` fills [HostActions.fullscreen] with it.
 */
fun androidFullscreen(activity: Activity): Fullscreen = SystemBarsFullscreen(ActivitySystemBars(activity))

/** [SystemBars] over the [activity]'s window, through the platform `WindowInsetsController` (API 30+). */
private class ActivitySystemBars(private val activity: Activity) : SystemBars {
    override val hidden: Boolean
        get() = activity.window?.decorView?.rootWindowInsets?.isVisible(WindowInsets.Type.systemBars()) == false

    override fun hide() = activity.runOnUiThread {
        activity.window?.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    override fun show() = activity.runOnUiThread {
        activity.window?.insetsController?.show(WindowInsets.Type.systemBars())
    }
}
