/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/** Packs the same ABI from backend-owned source adapter data. */
fun gpuLitShadowUniforms(
    transform: Mat4,
    extraUniformFloats: FloatArray,
    vertexAnimation: Vec3f,
    timeSeconds: Float,
    mvp: Mat4,
    lightPayload: FloatArray,
    cascades: ShadowCascadeUniforms,
    cameraEye: Vec3f,
    fogColor: Color = Color.Black,
    fogDensity: Float = 0f,
): FloatArray {
    val lightLayout = MaterialUniformLayouts.SceneLight
    val light = lightPayload.copyOf(lightLayout.total)
    val material = pbrMaterialFloats(extraUniformFloats)
    return UniformWriter(MaterialUniformLayouts.LitShadow)
        .put(mvp.data, UniformFields.Mvp)
        .put(
            light,
            lightLayout.offsetOf(UniformFields.LightDirection),
            UniformFields.LightDirection,
            UniformFields.LightColor,
        )
        .put(
            light,
            lightLayout.offsetOf(UniformFields.PointLightPositions),
            UniformFields.PointLightPositions,
        )
        .put(
            light,
            lightLayout.offsetOf(UniformFields.PointLightColors),
            UniformFields.PointLightColors,
        )
        .put(cascades.matrixFloats(), UniformFields.CascadeViewProjections)
        .put(cascades.depthScaleFloats(), UniformFields.CascadeDepthScales)
        .put(transform.data, UniformFields.Model)
        .put(UniformFields.VertexAnimation, vertexAnimation, timeSeconds)
        .put(cameraPositionFloats(cameraEye), UniformFields.CameraPosition)
        .put(material, UniformFields.Material)
        .put(fogUniformFloats(fogColor, fogDensity), UniformFields.FogColor)
        .build()
}
