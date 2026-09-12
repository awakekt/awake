/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

object SkyboxFields {
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec3)
    val SunDirection = UniformField("sunDirection", GpuDataShape.Vec3)
    val HorizonColor = UniformField("horizonColor", GpuDataShape.Vec4)
    val ZenithColor = UniformField("zenithColor", GpuDataShape.Vec4)
    val SunColor = UniformField("sunColor", GpuDataShape.Vec4)
    val MoonColor = UniformField("moonColor", GpuDataShape.Vec4)
}

val SkyboxUniformLayout = UniformLayout(
    SkyboxFields.InverseViewProjection,
    SkyboxFields.CameraEye,
    SkyboxFields.SunDirection,
    SkyboxFields.HorizonColor,
    SkyboxFields.ZenithColor,
    SkyboxFields.SunColor,
    SkyboxFields.MoonColor,
)

val SUN_DISC_COLOR = Color(r = 1f, g = 0.98f, b = 0.9f, a = 1f)
val MOON_DISC_COLOR = Color(r = 0.85f, g = 0.9f, b = 1f, a = 0.7f)

fun skyboxUniformFloats(
    inverseViewProjection: Mat4,
    cameraEye: Vec3f,
    sunDirection: Vec3f,
    horizonColor: Color,
    zenithColor: Color,
): FloatArray = UniformWriter(SkyboxUniformLayout)
    .put(SkyboxFields.InverseViewProjection, inverseViewProjection)
    .put(SkyboxFields.CameraEye, cameraEye)
    .put(SkyboxFields.SunDirection, sunDirection)
    .put(SkyboxFields.HorizonColor, horizonColor)
    .put(SkyboxFields.ZenithColor, zenithColor)
    .put(SkyboxFields.SunColor, SUN_DISC_COLOR)
    .put(SkyboxFields.MoonColor, MOON_DISC_COLOR)
    .build()
