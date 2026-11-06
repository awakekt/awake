/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import platform.Foundation.NSThread
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The view controller that hosts a game's [AwakeMetalView], and owns what UIKit asks a controller
 * about: whether the status bar and the home indicator show. `makeVulkanGameViewController` returns
 * one. Pass it to [iosHostActions] to have [HostActions.fullscreen].
 *
 * iOS has no app fullscreen. Its nearest is hiding the system chrome, so [fullscreen] hides the
 * status bar and lets the home indicator fade after a moment, through `prefersStatusBarHidden` and
 * `prefersHomeIndicatorAutoHidden`. The safe area still holds the notch and the rounded corners:
 * the view keeps reporting it as `DisplayInsets`.
 *
 * A host app that shows the game inside its own controller passes that controller to
 * [iosHostActions] instead, and [HostActions.fullscreen] is null, since only this class answers
 * UIKit's questions.
 */
class AwakeViewController : UIViewController(nibName = null, bundle = null) {
    private var chromeHidden = false

    /**
     * This controller's [Fullscreen]. Any thread may call it: the change is made on the main queue,
     * and shows in [Fullscreen.isActive] once it has been.
     */
    val fullscreen: Fullscreen = SystemBarsFullscreen(ControllerChrome(this))

    override fun prefersStatusBarHidden(): Boolean = chromeHidden

    override fun prefersHomeIndicatorAutoHidden(): Boolean = chromeHidden

    /** Hides or shows the status bar and home indicator, and tells UIKit to ask again. Main thread only. */
    internal fun applyChromeHidden(hidden: Boolean) {
        chromeHidden = hidden
        setNeedsStatusBarAppearanceUpdate()
        setNeedsUpdateOfHomeIndicatorAutoHidden()
    }

    internal val isChromeHidden: Boolean
        get() = chromeHidden
}

/** [SystemBars] over an [AwakeViewController], made on the main queue as UIKit requires. */
private class ControllerChrome(private val controller: AwakeViewController) : SystemBars {
    override val hidden: Boolean
        get() = controller.isChromeHidden

    override fun hide() = onMain { controller.applyChromeHidden(true) }

    override fun show() = onMain { controller.applyChromeHidden(false) }
}

private fun onMain(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue(), block)
}
