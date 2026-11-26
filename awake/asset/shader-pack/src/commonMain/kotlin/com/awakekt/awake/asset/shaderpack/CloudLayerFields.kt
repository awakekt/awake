/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/**
 * Fragment quality tier for [CloudLayerShader], switched uniformly per draw with zero pipeline rebuilds.
 *
 * @property code Numeric code packed into `lightDirection.w` for uniform shader branching.
 */
enum class CloudLayerQuality(val code: Int) {
    /** Low-spec mobile tier: single drifting 3-wave domain-warped analytic layer with horizon fade (0 texture fetches, 0 hash/floor ops). */
    Fast(0),

    /** Standard tier: two parallax-drifting layers blended in one pass with sunlit tops and cool undersides. */
    Standard(1),
}

/**
 * Live per-frame appearance and animation parameters for [cloudLayerContentFeature].
 *
 * Replaces stacked multi-mesh cloud sheets with a single alpha-blended pass, halving transparent overdraw
 * while adding smooth horizon fading and automatic day/night colour adaptation.
 */
class CloudLayerLook(
    /** Whether the cloud layer draws this frame. */
    var enabled: Boolean = true,
    /** Active shading tier ([CloudLayerQuality.Fast] or [CloudLayerQuality.Standard]). */
    var quality: CloudLayerQuality = CloudLayerQuality.Standard,
    /** Elapsed animation clock in seconds driving cloud drift. */
    var timeSeconds: Float = 0f,
    /** Bright sunlit cloud crest colour (linear space). */
    var cloudColor: Color = Color(0.97f, 0.98f, 1.0f, 1f),
    /** Cool shadowed cloud underside colour (linear space). */
    var shadowColor: Color = Color(0.22f, 0.31f, 0.50f, 1f),
    /** World-space cloud noise spatial frequency (inverse cloud feature size in metres). */
    var scale: Float = 0.008f,
    /** Horizontal drift speed along world X (noise units per second). */
    var driftSpeedX: Float = 0.015f,
    /** Horizontal drift speed along world Z (noise units per second). */
    var driftSpeedZ: Float = 0.011f,
    /** Fraction of the layer filled with cloud puffs (0..1). */
    var coverage: Float = 0.56f,
    /** Transition width controlling cloud edge fluffiness. */
    var softness: Float = 0.30f,
    /** Base cloud layer opacity (0..1). */
    var opacity: Float = 0.72f,
    /** Distance from the camera eye (metres) where horizon alpha fade begins. */
    var fadeStart: Float = 350f,
    /** Distance from the camera eye (metres) where clouds fade to zero alpha; `<= fadeStart` disables fade. */
    var fadeEnd: Float = 1600f,
    /** Optional directional light colour override; null reads the frame's scene light colour. */
    var lightColorOverride: Color? = null,
    /** Optional ambient floor override; null reads the frame's scene light ambient. */
    var ambientOverride: Float? = null,
    /** World-space translation offset added to the uploaded mesh's vertex positions. */
    val origin: Vec3f = Vec3f(0f, 0f, 0f),
)

/** Uniform fields of [CloudLayerShader]. */
object CloudLayerFields {
    /** Camera view-projection matrix. */
    val ViewProjection = UniformField("viewProjection", GpuDataShape.Mat4)

    /** `xyz` = world-space camera eye, `w` = animation time in seconds. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** `xyz` = normalized direction toward the sun, `w` = [CloudLayerQuality.code]. */
    val LightDirection = UniformField("lightDirection", GpuDataShape.Vec4)

    /** `rgb` = directional light colour, `a` = ambient floor. */
    val LightColor = UniformField("lightColor", GpuDataShape.Vec4)

    /** `rgb` = sky horizon colour, `a` = exposure. */
    val HorizonColor = UniformField("horizonColor", GpuDataShape.Vec4)

    /** `rgb` = fog colour, `a` = exponential fog density. */
    val FogColor = UniformField("fogColor", GpuDataShape.Vec4)

    /** `x` = linear fog start, `y` = linear fog end, `z` = fadeStart, `w` = fadeEnd. */
    val FogAndFade = UniformField("fogAndFade", GpuDataShape.Vec4)

    /** `rgb` = sunlit cloud colour, `a` = cloud spatial frequency ([CloudLayerLook.scale]). */
    val CloudColor = UniformField("cloudColor", GpuDataShape.Vec4)

    /** `rgb` = cloud underside/shadow colour, `a` = base opacity (0..1). */
    val ShadowColor = UniformField("shadowColor", GpuDataShape.Vec4)

    /** `x` = driftSpeedX, `y` = driftSpeedZ, `z` = coverage (0..1), `w` = softness. */
    val CloudParams = UniformField("cloudParams", GpuDataShape.Vec4)

    /** `xyz` = world-space origin offset, `w` = unused. */
    val Origin = UniformField("origin", GpuDataShape.Vec4)
}

/** Uniform layout of [CloudLayerShader]. */
val CloudLayerUniformLayout = UniformLayout(
    CloudLayerFields.ViewProjection,
    CloudLayerFields.CameraEye,
    CloudLayerFields.LightDirection,
    CloudLayerFields.LightColor,
    CloudLayerFields.HorizonColor,
    CloudLayerFields.FogColor,
    CloudLayerFields.FogAndFade,
    CloudLayerFields.CloudColor,
    CloudLayerFields.ShadowColor,
    CloudLayerFields.CloudParams,
    CloudLayerFields.Origin,
)
