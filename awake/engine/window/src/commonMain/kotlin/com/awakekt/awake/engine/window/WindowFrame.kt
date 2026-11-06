/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * Whether a window has the OS title bar and border. Only a desktop window has either: a page sits in
 * the browser's own window, and Android and iOS have system bars instead, which fullscreen hides. So
 * shared code reaches it through [HostActions.windowFrame], which is null on those hosts.
 */
enum class WindowDecoration {
    /** The OS title bar and border: the player moves, resizes and closes the window with them. */
    System,

    /**
     * No title bar or border (undecorated, or frameless). The OS gives the player no way to move the
     * window, so a game that wants it movable draws its own drag area; on desktop, `glfwWindowDrag`
     * moves the window from it. Resizing from the edges is not offered.
     */
    None,
}

/**
 * The game window's OS title bar and border, which shared code such as a settings window can turn
 * off and on. See [HostActions.windowFrame].
 *
 * The engine binds no key or button to it: when and how a player changes it is the game's choice.
 */
interface WindowFrame {
    /**
     * The window's [WindowDecoration], read from the host each time. Setting it changes the window
     * at once, keeping its content area's size. A fullscreen window has no title bar either way, and
     * leaves fullscreen with the decoration set here.
     */
    var decoration: WindowDecoration
}
