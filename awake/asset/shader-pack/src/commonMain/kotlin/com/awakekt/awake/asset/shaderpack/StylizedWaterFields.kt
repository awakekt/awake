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
 * Fragment quality tier for [StylizedWaterShader], switched uniformly per draw with zero pipeline rebuilds.
 *
 * @property code Numeric code packed into `lightDirection.w` for uniform shader branching.
 */
enum class StylizedWaterQuality(val code: Int) {
    /** Low-spec mobile tier: 2-wave ripple blend + cheap Fresnel horizon sheen + fog (0 texture fetches). */
    Fast(0),

    /** Standard tier: 4-octave analytic wave normals, Fresnel sky reflection, sun specular sparkle, and wave crests. */
    Standard(1),
}

/**
 * Live per-frame appearance and animation parameters for [stylizedWaterContentFeature].
 *
 * Mutate fields in place (for example advancing [timeSeconds] or toggling [quality] when graphics tier changes)
 * without re-attaching the content feature or allocating per frame.
 */
class StylizedWaterLook(
    /** Whether the water surface draws this frame. */
    var enabled: Boolean = true,
    /** Active shading tier ([StylizedWaterQuality.Fast] or [StylizedWaterQuality.Standard]). */
    var quality: StylizedWaterQuality = StylizedWaterQuality.Standard,
    /** Elapsed animation clock in seconds driving wave phase drift. */
    var timeSeconds: Float = 0f,
    /** Shallow / sunlit water tint blended across wave crests. */
    var shallowColor: Color = Color(0.14f, 0.56f, 0.70f, 1f),
    /** Deep / trough water tint blended across wave valleys. */
    var deepColor: Color = Color(0.05f, 0.24f, 0.44f, 1f),
    /** Highlight colour blended onto crisp wave crests. */
    var foamColor: Color = Color(0.92f, 0.97f, 1.0f, 1f),
    /** World-space wave spatial frequency (radians per metre). */
    var waveScale: Float = 0.22f,
    /** Multiplier on [timeSeconds] controlling ripple drift speed. */
    var waveSpeed: Float = 1.0f,
    /** Analytic surface normal perturbation strength in [StylizedWaterQuality.Standard]. */
    var waveStrength: Float = 0.26f,
    /** Base surface opacity (0..1), modulated upward at grazing Fresnel angles. */
    var opacity: Float = 0.78f,
    /** Specular sun-glint intensity on ripples facing the sun in [StylizedWaterQuality.Standard]. */
    var sunGlintStrength: Float = 0.90f,
    /** Blend strength of [foamColor] on high wave crests (0..1). */
    var crestHighlightStrength: Float = 0.24f,
    /** Optional directional light colour override; null reads the frame's scene light colour. */
    var lightColorOverride: Color? = null,
    /** Optional ambient floor override; null reads the frame's scene light ambient. */
    var ambientOverride: Float? = null,
    /** World-space translation offset added to the uploaded mesh's vertex positions. */
    val origin: Vec3f = Vec3f(0f, 0f, 0f),
)

/** Uniform fields of [StylizedWaterShader]. */
object StylizedWaterFields {
    /** Camera view-projection matrix. */
    val ViewProjection = UniformField("viewProjection", GpuDataShape.Mat4)

    /** `xyz` = world-space camera eye, `w` = animation time in seconds. */
    val CameraEye = UniformField("cameraEye", GpuDataShape.Vec4)

    /** `xyz` = normalized direction toward the sun, `w` = [StylizedWaterQuality.code]. */
    val LightDirection = UniformField("lightDirection", GpuDataShape.Vec4)

    /** `rgb` = directional light colour, `a` = ambient floor. */
    val LightColor = UniformField("lightColor", GpuDataShape.Vec4)

    /** `rgb` = sky horizon colour, `a` = exposure. */
    val HorizonColor = UniformField("horizonColor", GpuDataShape.Vec4)

    /** `rgb` = sky zenith colour, `a` = base surface opacity (0..1). */
    val ZenithColor = UniformField("zenithColor", GpuDataShape.Vec4)

    /** `rgb` = fog colour, `a` = exponential fog density. */
    val FogColor = UniformField("fogColor", GpuDataShape.Vec4)

    /** `x` = linear fog start, `y` = linear fog end, `z` = origin.y, `w` = unused. */
    val FogRange = UniformField("fogRange", GpuDataShape.Vec4)

    /** `rgb` = shallow tint colour, `a` = world-space wave spatial frequency. */
    val ShallowColor = UniformField("shallowColor", GpuDataShape.Vec4)

    /** `rgb` = deep tint colour, `a` = wave drift speed multiplier. */
    val DeepColor = UniformField("deepColor", GpuDataShape.Vec4)

    /** `rgb` = wave crest highlight / foam colour, `a` = analytic normal perturbation strength. */
    val FoamColor = UniformField("foamColor", GpuDataShape.Vec4)

    /** `x` = sun specular glint strength, `y` = crest highlight strength, `z` = origin.x, `w` = origin.z. */
    val HighlightParams = UniformField("highlightParams", GpuDataShape.Vec4)
}

/** Uniform layout of [StylizedWaterShader]. */
val StylizedWaterUniformLayout = UniformLayout(
    StylizedWaterFields.ViewProjection,
    StylizedWaterFields.CameraEye,
    StylizedWaterFields.LightDirection,
    StylizedWaterFields.LightColor,
    StylizedWaterFields.HorizonColor,
    StylizedWaterFields.ZenithColor,
    StylizedWaterFields.FogColor,
    StylizedWaterFields.FogRange,
    StylizedWaterFields.ShallowColor,
    StylizedWaterFields.DeepColor,
    StylizedWaterFields.FoamColor,
    StylizedWaterFields.HighlightParams,
)
