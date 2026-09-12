/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.Vec3f

private const val DEFAULT_DIRECTION_X = 0.4f
private const val DEFAULT_DIRECTION_Y = 0.8f
private const val DEFAULT_DIRECTION_Z = 0.4f

/** Reaches a room, not a level. A point light with no authored range should light its
 * surroundings without quietly costing every fragment in the scene. */
private const val DEFAULT_POINT_RANGE = 10f

/**
 * A light in the scene.
 *
 * [direction] applies to [Type.Directional] only, read as the world-space direction the light
 * shines FROM -- the convention every lit shader used before this component existed.
 *
 * A [Type.Point] light takes its position from the entity's own `Transform`, not from a field
 * here: a light is placed the same way anything else in the scene is. [range] is where its
 * contribution reaches zero, and is ignored for a directional light, which has no falloff.
 *
 * `RenderSystem3D` collects the first directional light plus up to `MAX_POINT_LIGHTS` point lights;
 * past that cap the nearest to the camera win.
 */
data class Light(
    val color: Vec3f = Vec3f(1f, 1f, 1f),
    val intensity: Float = 1f,
    val type: Type = Type.Directional,
    val direction: Vec3f = Vec3f(DEFAULT_DIRECTION_X, DEFAULT_DIRECTION_Y, DEFAULT_DIRECTION_Z),
    val range: Float = DEFAULT_POINT_RANGE,
    /** Whether this light casts shadows. Point lights use six cube-face depth projections. */
    val shadowsEnabled: Boolean = true,
) {
    enum class Type {
        Directional,
        Point,
    }
}
