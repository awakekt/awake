/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * The native library behind the desktop window. It lives in common code so every target this module
 * publishes compiles a library: a target with no sources produces none, and its publication fails.
 */
internal const val WINDOW_LIBRARY = "awake-window"
