/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * `skybox.wgsl`'s uniform block as named fields, following [UniformFields]' pattern.
 *
 * Named rather than declared inline inside [SkyboxUniformLayout] because [UniformWriter] matches
 * fields by identity -- a writer can only name a field it can reach. Declaration order here is
 * not load-bearing; the layout's is.
 */
object SkyboxFields {
    /** Inverse view-projection matrix used to reconstruct world-space ray directions for skybox dome rendering. */
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)

    /** Camera eye position in world space. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** World-space direction towards the primary sun light source. */
    val SunDirection = UniformField("sunDirection", GpuDataShape.Vec4)

    /** Clear sky color at the horizon. */
    val HorizonColor = UniformField("horizonColor", GpuDataShape.Vec4)

    /** Clear sky color at the zenith. */
    val ZenithColor = UniformField("zenithColor", GpuDataShape.Vec4)

    /** Sunlight disc and halo tint color. */
    val SunColor = UniformField("sunColor", GpuDataShape.Vec4)

    /** Moon disc and nighttime ambient tint color. */
    val MoonColor = UniformField("moonColor", GpuDataShape.Vec4)
}
