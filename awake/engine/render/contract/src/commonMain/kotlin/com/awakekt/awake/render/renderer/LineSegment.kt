/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f

/**
 * One world-space debug line (e.g. a [com.awakekt.awake.core.math.Frustum]
 * wireframe edge) -- unlike [com.awakekt.awake.core.graphics2d.UiDrawPrimitive] (screen-space
 * pixels), these are transformed by the scene's own view-projection matrix and drawn inside
 * the main 3D render pass so they get real depth-testing against scene geometry.
 * @property start World-space starting coordinate of the line segment.
 * @property end World-space ending coordinate of the line segment.
 * @property color Tint color and opacity applied to the line.
 */
data class LineSegment(
    /** World-space starting coordinate of the line segment. */
    val start: Vec3f,
    /** World-space ending coordinate of the line segment. */
    val end: Vec3f,
    /** Tint color and opacity applied to the line. */
    val color: Color,
)
