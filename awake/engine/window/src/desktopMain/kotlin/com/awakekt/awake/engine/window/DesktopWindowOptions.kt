/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * How a desktop window opens, beyond the size and title in the app's `WindowConfig`. Pass it to the
 * [runDesktopWindow] that takes one, usually built with [desktopWindowOptions]:
 *
 * ```kotlin
 * runDesktopWindow(game, desktopWindowOptions { decoration = WindowDecoration.None }, onCreate = …, …)
 * ```
 *
 * Every property has a default that matches the [runDesktopWindow] without options. New options are
 * added as new properties with defaults, never as constructor parameters, so code compiled against
 * an older release keeps linking.
 */
class DesktopWindowOptions {
    /** Whether the window opens with the OS title bar and border. Change it later with [setWindowDecoration]. */
    var decoration: WindowDecoration = WindowDecoration.System

    /**
     * Where the UI density comes from: the OS display scale by default, followed as the window moves
     * between monitors, or [DesktopDisplayScale.Unscaled] for one framebuffer pixel per dp. Reported
     * through [runDesktopWindow]'s `onDensity`.
     */
    var displayScale: DesktopDisplayScale = DesktopDisplayScale.System
}

/** A [DesktopWindowOptions] with [configure] applied to the defaults. */
fun desktopWindowOptions(configure: DesktopWindowOptions.() -> Unit): DesktopWindowOptions =
    DesktopWindowOptions().apply(configure)
