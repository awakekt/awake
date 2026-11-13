/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_DISTANCE
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_FADE

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
 *
 * @property color The RGB color tint of the light.
 * @property intensity Intensity multiplier applied to the emitted light.
 * @property type The type of light emission (directional or point).
 * @property direction World-space direction the light shines from (for directional lights).
 * @property range Attenuation radius in world units where point light falloff reaches zero.
 * @property shadowsEnabled Whether this light casts shadows.
 * @property shadowDistance How far from the camera directional shadow cascades reach.
 * @property ambient Ambient lighting factor applied to unlit surfaces.
 * @property shadowFade The share of [shadowDistance] over which directional shadows fade out at its end.
 */
data class Light(
    val color: Vec3f = Vec3f(1f, 1f, 1f),
    var intensity: Float = 1f,
    val type: Type = Type.Directional,
    val direction: Vec3f = Vec3f(DEFAULT_DIRECTION_X, DEFAULT_DIRECTION_Y, DEFAULT_DIRECTION_Z),
    val range: Float = DEFAULT_POINT_RANGE,
    val shadowsEnabled: Boolean = true,
    val shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
    var ambient: Float? = null,
    val shadowFade: Float = DEFAULT_SHADOW_FADE,
) {
    /** The constructor as released before [shadowFade], kept so compiled callers still link. */
    @Deprecated("Use the constructor with shadowFade.", level = DeprecationLevel.HIDDEN)
    constructor(
        color: Vec3f = Vec3f(1f, 1f, 1f),
        intensity: Float = 1f,
        type: Type = Type.Directional,
        direction: Vec3f = Vec3f(DEFAULT_DIRECTION_X, DEFAULT_DIRECTION_Y, DEFAULT_DIRECTION_Z),
        range: Float = DEFAULT_POINT_RANGE,
        shadowsEnabled: Boolean = true,
        shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
        ambient: Float? = null,
    ) : this(color, intensity, type, direction, range, shadowsEnabled, shadowDistance, ambient, DEFAULT_SHADOW_FADE)

    /** [copy] as released before [shadowFade]. */
    @Deprecated("Use copy with shadowFade.", level = DeprecationLevel.HIDDEN)
    fun copy(
        color: Vec3f = this.color,
        intensity: Float = this.intensity,
        type: Type = this.type,
        direction: Vec3f = this.direction,
        range: Float = this.range,
        shadowsEnabled: Boolean = this.shadowsEnabled,
        shadowDistance: Float = this.shadowDistance,
        ambient: Float? = this.ambient,
    ): Light = copy(color, intensity, type, direction, range, shadowsEnabled, shadowDistance, ambient, shadowFade)

    /** Light emission type classification. */
    enum class Type {
        /** Directional light simulating infinite distance (e.g. sun or moon). */
        Directional,

        /** Omnidirectional point light radiating from entity position with radial falloff. */
        Point,
    }
}
