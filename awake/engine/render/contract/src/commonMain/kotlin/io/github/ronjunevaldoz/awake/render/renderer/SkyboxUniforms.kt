// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.renderer

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math.Mat4
import io.github.ronjunevaldoz.awake.core.math.Vec3f
import io.github.ronjunevaldoz.awake.core.math.inverse
import io.github.ronjunevaldoz.awake.core.geometry.GpuDataShape

/** `skybox.wgsl`'s uniform block, field for field: inverseViewProjection (mat4x4), cameraEye/
 * sunDirection/horizonColor/zenithColor/sunColor/moonColor (each a `vec4f`). Same "one number,
 * not N hand-copied literals" reason as [UniformLayout]. */
val SkyboxUniformLayout = UniformLayout(
    UniformField("inverseViewProjection", GpuDataShape.Mat4),
    UniformField("cameraEye", GpuDataShape.Vec4),
    UniformField("sunDirection", GpuDataShape.Vec4),
    UniformField("horizonColor", GpuDataShape.Vec4),
    UniformField("zenithColor", GpuDataShape.Vec4),
    UniformField("sunColor", GpuDataShape.Vec4),
    UniformField("moonColor", GpuDataShape.Vec4),
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
    return inverse.data +
            floatArrayOf(cameraEye.x, cameraEye.y, cameraEye.z, 0f) +
            floatArrayOf(sunDirection.x, sunDirection.y, sunDirection.z, 0f) +
            horizonColor.toFloatArray() + zenithColor.toFloatArray() +
            SUN_DISC_COLOR.toFloatArray() + MOON_DISC_COLOR.toFloatArray()
}
