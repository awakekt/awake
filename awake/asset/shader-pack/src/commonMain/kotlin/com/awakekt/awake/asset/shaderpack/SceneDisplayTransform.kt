/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFunctionHandle
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.b
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.g
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.lt
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.r
import com.awakekt.awake.asset.shaderdsl.select
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * What [sceneDisplayTransform] declared: how a lit scene shader turns its linear radiance into the
 * colour the target stores. Every lit shader writes through [display], so exposure and tone
 * mapping mean the same thing on every surface and on both backends.
 */
class SceneDisplayTransform internal constructor(
    private val toneMap: AslFunctionHandle,
    private val encode: AslFunctionHandle,
    private val decode: AslFunctionHandle?,
) {
    /** [radiance] times [exposure], tone mapped, then encoded for the UNORM target. */
    fun display(radiance: AslExpr, exposure: AslExpr): AslExpr = encode(toneMap(radiance * exposure))

    /**
     * [display] for a shader that lights display-referred colour, such as vertex colours, textures
     * or a lightmap multiplied as stored: [shaded] is decoded to linear first. At exposure 1 a
     * colour below the tone curve's shoulder comes back nearly as it went in. Needs the transform
     * declared with `decodesDisplayReferred = true`.
     */
    fun displayReferred(shaded: AslExpr, exposure: AslExpr): AslExpr {
        val decode = checkNotNull(decode) {
            "Declare sceneDisplayTransform(decodesDisplayReferred = true) to light display-referred colour."
        }
        return display(decode(shaded), exposure)
    }

    /** [linear] encoded with no exposure or tone mapping, for data views such as albedo. */
    fun encoded(linear: AslExpr): AslExpr = encode(linear)
}

/**
 * Declares the scene's display transform: exposure, Khronos PBR Neutral, then gamma 2.2.
 *
 * PBR Neutral leaves colours below 0.76 almost as authored and compresses only highlights, scaling
 * all three channels by one factor before easing toward white, so a lit colour keeps its hue. Its
 * toe removes the 4% specular floor a dielectric reflects under image-based light. With flat
 * ambient and no image-based light, ambient-only surfaces come out darker than that design
 * assumes. Reference: https://github.com/KhronosGroup/ToneMapping/tree/main/PBR_Neutral
 */
fun AslShaderBuilder.sceneDisplayTransform(decodesDisplayReferred: Boolean = false): SceneDisplayTransform {
    // The reference's `0.8 - 0.04`: where compression starts, after the toe's offset.
    val startCompression = const("TONE_MAP_START_COMPRESSION", 0.76f)
    val desaturation = const("TONE_MAP_DESATURATION", 0.15f)
    // Gamma 2.2, not exact sRGB -- matches the rest of the pipeline's colour (im)precision.
    val invGamma = const("INV_GAMMA", 1f / 2.2f)
    val toneMap = fn("toneMapPbrNeutral", returns = AslType.Data(GpuDataShape.Vec3)) {
        val color by param(GpuDataShape.Vec3)
        val low = let("low", min(color.r, min(color.g, color.b)))
        val offset = let("offset", select(0.04f.lit, low - 6.25f.lit * low * low, low lt 0.08f.lit))
        val shifted = let("shifted", color - vec3(offset))
        val peak = let("peak", max(shifted.r, max(shifted.g, shifted.b)))
        iff(peak lt startCompression) { returnValue(shifted) }
        val shoulder = 1f.lit - startCompression
        val newPeak = let("newPeak", 1f.lit - shoulder * shoulder / (peak + shoulder - startCompression))
        val fade = let("fade", 1f.lit - 1f.lit / (desaturation * (peak - newPeak) + 1f.lit))
        returnValue(mix(shifted * (newPeak / peak), vec3(newPeak), fade))
    }
    val encode = fn("linearToSrgb", returns = AslType.Data(GpuDataShape.Vec3)) {
        val linear by param(GpuDataShape.Vec3)
        returnValue(pow(max(linear, vec3(0f.lit)), vec3(invGamma)))
    }
    // Declared only on request: ASL rejects a function a shader never calls.
    val decode = if (decodesDisplayReferred) {
        val gamma = const("GAMMA", 2.2f)
        fn("displayToLinear", returns = AslType.Data(GpuDataShape.Vec3)) {
            val encoded by param(GpuDataShape.Vec3)
            returnValue(pow(max(encoded, vec3(0f.lit)), vec3(gamma)))
        }
    } else {
        null
    }
    return SceneDisplayTransform(toneMap, encode, decode)
}
