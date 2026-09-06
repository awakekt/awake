/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.inverse

/** `skybox.wgsl`'s uniform block, in the order the shader reads it. Same "one number, not N
 * hand-copied literals" reason as [UniformLayout]. */
val SkyboxUniformLayout = UniformLayout(
    SkyboxFields.InverseViewProjection,
    SkyboxFields.CameraEye,
    SkyboxFields.SunDirection,
    SkyboxFields.HorizonColor,
    SkyboxFields.ZenithColor,
    SkyboxFields.SunColor,
    SkyboxFields.MoonColor,
)

/** Warm sun, cooler and dimmer moon. Not [Renderer] fields: they are derived decoration, and
 * two more toggles would not buy a caller anything the horizon/zenith pair doesn't. */
@Suppress("MagicNumber") // Colour components.
val SUN_DISC_COLOR = Color(r = 1f, g = 0.92f, b = 0.72f, a = 1f)

@Suppress("MagicNumber") // Colour components.
val MOON_DISC_COLOR = Color(r = 0.72f, g = 0.78f, b = 0.88f, a = 1f)

/**
 * The float block both backends' skybox pipelines upload, assembled from data the 3D pass
 * already has in hand -- `null` when [viewProjection] is singular (no ray can be unprojected
 * from it), in which case the caller simply skips this frame's sky rather than uploading
 * garbage.
 *
 * [sunDirection] is the scene light's own direction (the direction it shines FROM), so the sun
 * disc lands where the light comes from and the moon lands opposite it -- no second light
 * source, and the sky moves whenever the light does.
 */
fun skyboxUniformFloats(
    viewProjection: Mat4,
    cameraEye: Vec3f,
    sunDirection: Vec3f,
    horizonColor: Color,
    zenithColor: Color,
): FloatArray? {
    val inverse = viewProjection.inverse() ?: return null
    return UniformWriter(SkyboxUniformLayout)
        .put(SkyboxFields.InverseViewProjection, inverse)
        .put(SkyboxFields.CameraEye, cameraEye)
        .put(SkyboxFields.SunDirection, sunDirection)
        .put(SkyboxFields.HorizonColor, horizonColor)
        .put(SkyboxFields.ZenithColor, zenithColor)
        .put(SkyboxFields.SunColor, SUN_DISC_COLOR)
        .put(SkyboxFields.MoonColor, MOON_DISC_COLOR)
        .build()
}
