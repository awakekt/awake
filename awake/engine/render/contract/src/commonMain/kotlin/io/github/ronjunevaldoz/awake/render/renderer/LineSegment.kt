// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.renderer

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math.Vec3f

/**
 * One world-space debug line (e.g. a [io.github.ronjunevaldoz.awake.core.math.Frustum]
 * wireframe edge) -- unlike [io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive] (screen-space
 * pixels), these are transformed by the scene's own view-projection matrix and drawn inside
 * the main 3D render pass so they get real depth-testing against scene geometry.
 */
data class LineSegment(
    val start: Vec3f,
    val end: Vec3f,
    val color: Color,
)
