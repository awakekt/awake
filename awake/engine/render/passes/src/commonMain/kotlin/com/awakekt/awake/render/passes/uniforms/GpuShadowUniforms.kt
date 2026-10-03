/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter

/** Packs the same ABI from backend-owned source adapter data. */
@Suppress("LongParameterList")
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
): FloatArray = gpuLitShadowUniforms(
    transform = transform,
    extraUniformFloats = extraUniformFloats,
    vertexAnimation = vertexAnimation,
    timeSeconds = timeSeconds,
    mvp = mvp,
    lightPayload = lightPayload,
    cascades = cascades,
    cameraEye = cameraEye,
    fogColor = fogColor,
    fogDensity = fogDensity,
    cameraForward = Vec3f(0f, 0f, -1f),
)

/**
 * Packs the lit-shadow block with an explicit camera direction for cascade selection.
 *
 * Most of the block is the frame's -- lights, shadow cascades, camera, fog, debug view -- and is
 * the same for every draw. That part is packed once per frame and copied; only the draw's own
 * matrices, vertex animation and material are written per draw.
 */
@Suppress("LongParameterList")
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
    cameraForward: Vec3f,
    debugView: GpuDebugView = GpuDebugView.Off,
): FloatArray {
    val out = LitShadowFrameFields.shared(lightPayload, cascades, cameraEye, fogColor, fogDensity, cameraForward, debugView)
        .copyOf()
    mvp.data.copyInto(out, MVP_AT)
    transform.data.copyInto(out, MODEL_AT)
    out[ANIMATION_AT] = vertexAnimation.x
    out[ANIMATION_AT + 1] = vertexAnimation.y
    out[ANIMATION_AT + 2] = vertexAnimation.z
    out[ANIMATION_AT + VEC4_W] = timeSeconds
    pbrMaterialFloats(extraUniformFloats).copyInto(out, MATERIAL_AT, 0, UniformFields.Material.floats)
    return out
}

/** The whole lit-shadow block, field by field. The per-draw fields hold whatever the caller passes. */
@Suppress("LongParameterList")
internal fun packLitShadowBlock(
    transform: Mat4,
    extraUniformFloats: FloatArray,
    vertexAnimation: Vec3f,
    timeSeconds: Float,
    mvp: Mat4,
    lightPayload: FloatArray,
    cascades: ShadowCascadeUniforms,
    cameraEye: Vec3f,
    fogColor: Color,
    fogDensity: Float,
    cameraForward: Vec3f,
    debugView: GpuDebugView,
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
        .put(UniformFields.CameraForward, cameraForward, cascades.count.toFloat())
        .put(material, UniformFields.Material)
        .put(fogUniformFloats(fogColor, fogDensity), UniformFields.FogColor)
        .putDebugView(debugView, cameraForward)
        .build()
}

/**
 * The frame's share of the lit-shadow block, packed once and reused while its inputs hold.
 *
 * Keyed on the light block and cascade set by identity -- the scene compiler builds each once per
 * frame and every draw of that frame passes the same ones -- and on the camera, fog and debug view
 * by value. Render thread only, like the rest of draw preparation.
 */
private object LitShadowFrameFields {
    private var light: FloatArray? = null
    private var cascades: ShadowCascadeUniforms? = null
    private val eye = Vec3f(0f, 0f, 0f)
    private val forward = Vec3f(0f, 0f, 0f)
    private var fogColor: Color? = null
    private var fogDensity = 0f
    private var debugView: GpuDebugView? = null
    private var block = FloatArray(0)
    private val identity = Mat4()
    private val stillAnimation = Vec3f(0f, 0f, 0f)

    @Suppress("LongParameterList", "ComplexCondition")
    fun shared(
        lightPayload: FloatArray,
        cascades: ShadowCascadeUniforms,
        cameraEye: Vec3f,
        fogColor: Color,
        fogDensity: Float,
        cameraForward: Vec3f,
        debugView: GpuDebugView,
    ): FloatArray {
        if (light === lightPayload && this.cascades === cascades && eye == cameraEye && forward == cameraForward &&
            this.fogColor == fogColor && this.fogDensity == fogDensity && this.debugView == debugView
        ) {
            return block
        }
        block = packLitShadowBlock(
            identity, EMPTY_EXTRAS, stillAnimation, 0f, identity,
            lightPayload, cascades, cameraEye, fogColor, fogDensity, cameraForward, debugView,
        )
        light = lightPayload
        this.cascades = cascades
        eye.set(cameraEye)
        forward.set(cameraForward)
        this.fogColor = fogColor
        this.fogDensity = fogDensity
        this.debugView = debugView
        return block
    }
}

private val EMPTY_EXTRAS = FloatArray(0)
private const val VEC4_W = 3
private val MVP_AT = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.Mvp)
private val MODEL_AT = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.Model)
private val ANIMATION_AT = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.VertexAnimation)
private val MATERIAL_AT = MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.Material)
