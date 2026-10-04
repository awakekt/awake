/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UITouch
import platform.UIKit.UIView

/**
 * Passes the [touches] UIKit reports to [input] as fingers, each identified by its `UITouch`, which
 * stays the same object while the finger is down. The first finger also drives the primary pointer
 * (see [Input.setTouch]).
 */
@OptIn(ExperimentalForeignApi::class)
fun UIView.syncAwakePointerInput(
    touches: Set<*>,
    down: Boolean,
    input: Input,
): Boolean {
    val scale = contentScaleFactor.toFloat()
    var synced = false
    for (item in touches) {
        val touch = item as? UITouch ?: continue
        touch.locationInView(this).useContents {
            input.setTouch(touch.hashCode().toLong(), x.toFloat() * scale, y.toFloat() * scale, down)
        }
        synced = true
    }
    return synced
}
