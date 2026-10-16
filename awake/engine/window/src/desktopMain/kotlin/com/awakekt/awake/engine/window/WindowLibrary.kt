/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/** The native library behind the desktop window. */
internal const val WINDOW_LIBRARY = "awake-window"

/**
 * GLFW's own DLL, which the jar ships beside [WINDOW_LIBRARY] on Windows and which is loaded first:
 * Windows looks for a library's DLLs beside the program, not beside the library, but takes one already
 * loaded by its name. libawake-vulkan then shares it. Elsewhere the system's GLFW is linked.
 */
internal const val WINDOWS_GLFW_LIBRARY = "glfw3"
