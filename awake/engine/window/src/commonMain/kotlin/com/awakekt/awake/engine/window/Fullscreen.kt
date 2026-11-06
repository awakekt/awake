/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * Puts the game's view in fullscreen and takes it back out. See [HostActions.fullscreen].
 *
 * The engine binds no key or button to it: when and how a player toggles it (a settings
 * checkbox, a hotkey, a button on the title screen) is the game's choice.
 *
 * The host can leave fullscreen without asking: on the web the player presses Escape, or the
 * browser leaves when the tab is hidden. Read [isActive] when you need the current state, such as
 * when a settings window draws, instead of keeping your own copy.
 */
interface Fullscreen {
    /** Whether the view is fullscreen now. It is read from the host each time, never cached. */
    val isActive: Boolean

    /**
     * Asks the host to go fullscreen. A browser allows this only while a recent click or key
     * press still counts as a user gesture, so call it from the frame that handled that input.
     * It does nothing when the view is already fullscreen.
     *
     * @return true when the request was handed to the host. This does not mean the view is
     *   fullscreen: the browser can still refuse it, and the change arrives later. Read
     *   [isActive] on a later frame.
     */
    fun enter(): Boolean

    /** Leaves fullscreen. It does nothing when the view is not fullscreen. */
    fun exit()

    /**
     * Enters fullscreen when the view is not in it and leaves it when it is.
     *
     * @return what [enter] returned, or true after [exit].
     */
    fun toggle(): Boolean = if (isActive) {
        exit()
        true
    } else {
        enter()
    }
}
