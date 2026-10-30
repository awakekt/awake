/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * Host input information shared with game systems, including on a frame with no input activity.
 * The host calls [update] before running systems. Readers keep [changeVersion] to notice a device
 * or host-preference change without allocating events or subscribing across scene lifetimes.
 *
 * [showTouchControls] is the host's preference, before any canvas-specific visibility policy.
 */
class HostInputState {
    private val devices = InputDeviceTracker()

    /** The host's touch-control preference as of the current frame. */
    var showTouchControls: Boolean = false
        private set

    /** The last device used; null until an input device has been used. */
    var lastInputDevice: InputDevice? = null
        private set

    /** Increments only when either public value changes; a polling system remembers its last version. */
    var changeVersion: Long = 0
        private set

    /** Observes [snapshot] and the host's preference, returning true only when their public state changed. */
    fun update(snapshot: InputSnapshot, showTouchControls: Boolean): Boolean {
        val device = devices.observe(snapshot)
        if (device == lastInputDevice && showTouchControls == this.showTouchControls) return false
        lastInputDevice = device
        this.showTouchControls = showTouchControls
        changeVersion++
        return true
    }
}
