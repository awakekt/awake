/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.renderer

import io.github.awakelab.awake.core.geometry.GpuDataShape

/**
 * `skybox.wgsl`'s uniform block as named fields, following [UniformFields]' pattern.
 *
 * Named rather than declared inline inside [SkyboxUniformLayout] because [UniformWriter] matches
 * fields by identity -- a writer can only name a field it can reach. Declaration order here is
 * not load-bearing; the layout's is.
 */
object SkyboxFields {
    val InverseViewProjection = UniformField("inverseViewProjection", GpuDataShape.Mat4)
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)
    val SunDirection = UniformField("sunDirection", GpuDataShape.Vec4)
    val HorizonColor = UniformField("horizonColor", GpuDataShape.Vec4)
    val ZenithColor = UniformField("zenithColor", GpuDataShape.Vec4)
    val SunColor = UniformField("sunColor", GpuDataShape.Vec4)
    val MoonColor = UniformField("moonColor", GpuDataShape.Vec4)
}
