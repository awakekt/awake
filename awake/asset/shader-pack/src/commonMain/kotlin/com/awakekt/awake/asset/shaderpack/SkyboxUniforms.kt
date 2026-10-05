/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/** Uniform field declarations for procedural atmospheric skybox rendering. */
object SkyboxFields {
    /** Inverse view-projection matrix reconstructing view ray directions. */
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)

    /** World-space camera eye position. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec3)

    /** Normalized direction vector toward the sun celestial disc. */
    val SunDirection = UniformField("sunDirection", GpuDataShape.Vec3)

    /** Horizon ambient color gradient. */
    val HorizonColor = UniformField("horizonColor", GpuDataShape.Vec4)

    /** Zenith (top of sky dome) atmospheric color gradient. */
    val ZenithColor = UniformField("zenithColor", GpuDataShape.Vec4)

    /** Sun disc color and intensity. */
    val SunColor = UniformField("sunColor", GpuDataShape.Vec4)

    /** Moon disc color and intensity. */
    val MoonColor = UniformField("moonColor", GpuDataShape.Vec4)
}

/** Uniform layout specification for procedural skybox rendering. */
val SkyboxUniformLayout = UniformLayout(
    SkyboxFields.InverseViewProjection,
    SkyboxFields.CameraEye,
    SkyboxFields.SunDirection,
    SkyboxFields.HorizonColor,
    SkyboxFields.ZenithColor,
    SkyboxFields.SunColor,
    SkyboxFields.MoonColor,
)

/** Default emissive color of the sun celestial body disc. */
val SUN_DISC_COLOR = Color(r = 1f, g = 0.98f, b = 0.9f, a = 1f)

/** Default emissive color of the moon celestial body disc. */
val MOON_DISC_COLOR = Color(r = 0.85f, g = 0.9f, b = 1f, a = 0.7f)

/**
 * Packs procedural skybox uniforms into a float array conforming to [SkyboxUniformLayout].
 *
 * @param inverseViewProjection Inverse view-projection matrix reconstructing world view rays.
 * @param cameraEye World-space camera position.
 * @param sunDirection Direction vector pointing toward the sun.
 * @param horizonColor Ambient horizon color.
 * @param zenithColor Zenith color at the top of the sky dome.
 * @return Formatted uniform buffer float array.
 */
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
