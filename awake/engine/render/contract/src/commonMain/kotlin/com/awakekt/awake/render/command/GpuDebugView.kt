/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

/**
 * Which diagnostic a scene pass draws instead of its lit image, already lowered to numbers.
 *
 * A backend forwards this untouched to the shared uniform packing and to render features; it never
 * branches on [code]. `RenderDebugView` in `render:passes` defines the codes.
 *
 * @property code The view the scene shaders write; 0 is the lit image.
 * @property depthRange The world distance along the camera's forward axis a linear-depth view
 *   shows as white.
 * @property layer The texture-array layer a layer viewer shows.
 */
data class GpuDebugView(
    val code: Int = 0,
    val depthRange: Float = 0f,
    val layer: Int = 0,
) {
    companion object {
        /** The lit image. */
        val Off = GpuDebugView()
    }
}
