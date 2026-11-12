/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIView

/**
 * Reports how far the on-screen keyboard rises into [view], in points (dp), from UIKit's keyboard
 * frame notifications: [DisplayInsets.None] once it hides or sits below the view.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosKeyboardInset(private val view: UIView, private val onChange: (DisplayInsets) -> Unit) {
    private val observers = mutableListOf<Any>()

    /** Starts listening; a second call does nothing until [stop]. */
    fun start() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIKeyboardWillChangeFrameNotification, null, NSOperationQueue.mainQueue) { report(it) }
            .let(observers::add)
        center.addObserverForName(UIKeyboardWillHideNotification, null, NSOperationQueue.mainQueue) { onChange(DisplayInsets.None) }
            .let(observers::add)
    }

    /** Stops listening and reports no keyboard, so a view leaving its window does not leave a stale inset. */
    fun stop() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        onChange(DisplayInsets.None)
    }

    private fun report(notification: NSNotification?) {
        val end = (notification?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue)?.CGRectValue() ?: return
        // The notification's frame is in screen coordinates; the view's own bottom edge is what it covers.
        val inView = view.convertRect(end, fromView = null)
        val viewBottom = view.bounds.useContents { origin.y + size.height }
        val covered = viewBottom - inView.useContents { origin.y }
        onChange(DisplayInsets(bottom = covered.toFloat().coerceAtLeast(0f)))
    }
}
