/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** Uniform fields of the sun and moon pass. Each body has a direction and the screen-plane axes around it. */
object CelestialFields {
    /** Inverse view-projection matrix used to reconstruct view rays. */
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)

    /** World-space camera eye position. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** Direction toward the sun. */
    val SunDirection = UniformField("sunDirection", GpuDataShape.Vec4)

    /** The sun's right axis, perpendicular to [SunDirection]. */
    val SunRight = UniformField("sunRight", GpuDataShape.Vec4)

    /** The sun's up axis, perpendicular to [SunDirection] and [SunRight]. */
    val SunUp = UniformField("sunUp", GpuDataShape.Vec4)

    /** Direction toward the moon. */
    val MoonDirection = UniformField("moonDirection", GpuDataShape.Vec4)

    /** The moon's right axis. */
    val MoonRight = UniformField("moonRight", GpuDataShape.Vec4)

    /** The moon's up axis. */
    val MoonUp = UniformField("moonUp", GpuDataShape.Vec4)

    /** Sun colour in rgb. */
    val SunColor = UniformField("sunColor", GpuDataShape.Vec4)

    /** Moon colour in rgb. */
    val MoonColor = UniformField("moonColor", GpuDataShape.Vec4)

    /** The sun's visibility in x and the moon's in y, each 0 hidden to 1 shown. */
    val Visibility = UniformField("visibility", GpuDataShape.Vec4)

    /**
     * x: the cosine of the moon's phase angle; y: +1 while it waxes and -1 while it wanes; z: 1 when
     * the sun draws a texture; w: 1 when the moon does.
     */
    val Params = UniformField("params", GpuDataShape.Vec4)
}

/** Uniform layout of the sun and moon pass. */
val CelestialUniformLayout = UniformLayout(
    CelestialFields.InverseViewProjection,
    CelestialFields.CameraEye,
    CelestialFields.SunDirection,
    CelestialFields.SunRight,
    CelestialFields.SunUp,
    CelestialFields.MoonDirection,
    CelestialFields.MoonRight,
    CelestialFields.MoonUp,
    CelestialFields.SunColor,
    CelestialFields.MoonColor,
    CelestialFields.Visibility,
    CelestialFields.Params,
)
