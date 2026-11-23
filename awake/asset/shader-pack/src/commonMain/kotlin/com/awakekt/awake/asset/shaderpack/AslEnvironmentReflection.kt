/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z

private const val METALLIC_ENV_SCALE = 0.65f
private const val SKY_GLINT_STRENGTH = 0.55f
private const val CHROME_BAND_STRENGTH = 1.65f
private const val CHROME_RIM_STRENGTH = 0.75f

/**
 * Analytic environment reflection for PBR static and skinned surfaces: reflects view vector
 * [viewDir] across [normal], evaluates a roughness-softened sky-to-ground gradient plus a
 * directional sun glint modulated by Fresnel-Schlick, and when [reflectance] is positive adds a
 * high-contrast analytic sphere-map chrome sheen tinted by [albedo] plus a grazing rim gleam so
 * reflective metallic surfaces gleam brightly from any camera angle without lifting dark areas into
 * a flat grey floor.
 *
 * Zero when both [metallic] and [reflectance] are zero so matte dielectric materials stay unchanged.
 */
@Suppress("LongParameterList")
internal fun AslBlockBuilder.environmentReflection(
    normal: AslExpr,
    viewDir: AslExpr,
    lightDir: AslExpr,
    lightColor: AslExpr,
    albedo: AslExpr,
    f0: AslExpr,
    metallic: AslExpr,
    roughness: AslExpr,
    reflectance: AslExpr,
    occlusion: AslExpr,
): AslExpr {
    val smoothness = let("envSmoothness", 1f.lit - roughness)
    val reflStrength = let("envReflStrength", saturate(reflectance))
    val envWeight = let("envWeight", max(metallic * smoothness * METALLIC_ENV_SCALE.lit, reflStrength))
    val nDotV = let("envNDotV", clamp(dot(normal, viewDir), 0f.lit, 1f.lit))
    // Reflected direction R = 2 * (N·V) * N - V.
    val refl = let("envRefl", normalize(normal * (2f.lit * dot(normal, viewDir)) - viewDir))
    val upFactor = let("envUp", saturate(refl.y * 0.5f.lit + 0.5f.lit))
    val groundColor = vec3(0.18f.lit, 0.16f.lit, 0.14f.lit)
    val horizonColor = vec3(0.75f.lit, 0.82f.lit, 0.90f.lit)
    val zenithColor = vec3(0.32f.lit, 0.52f.lit, 0.82f.lit)
    val lower = mix(groundColor, horizonColor, saturate(upFactor * 2f.lit))
    val upper = mix(horizonColor, zenithColor, saturate((upFactor - 0.5f.lit) * 2f.lit))
    val hemi = let("envHemi", mix(lower, upper, saturate((refl.y + 0.1f.lit) * 5f.lit)))
    // Rougher surfaces blur toward a neutral hemisphere average; smoother surfaces keep sharp sky contrast + sun glint.
    val blurred = let("envBlurred", mix(vec3(0.45f.lit, 0.48f.lit, 0.52f.lit), hemi, smoothness))
    val sunAlign = let("envSunAlign", max(dot(refl, lightDir), 0f.lit))
    val sunGlint = let("envSunGlint", pow(sunAlign, mix(4f.lit, 48f.lit, smoothness)) * lightColor.xyz * (SKY_GLINT_STRENGTH.lit * smoothness))
    // Roughness-attenuated Fresnel-Schlick so grazing reflective edges gleam naturally.
    val fMax = max(vec3(smoothness), f0)
    val envFresnel = let("envFresnel", f0 + (fMax - f0) * pow(1f.lit - nDotV, 5f.lit))
    val baseRefl = let("envBaseRefl", (blurred + sunGlint) * envFresnel * occlusion * envWeight)

    // Analytic sphere-map chrome bands (zero when reflectance == 0): bright shimmering reflection
    // highlights across R with a grazing rim gleam, tinted slightly by the surface albedo.
    val bandA = let("envBandA", sin(refl.x * 3.6f.lit + refl.y * 4.8f.lit + refl.z * 2.2f.lit) * 0.5f.lit + 0.5f.lit)
    val bandB = let("envBandB", sin(refl.x * (-5.2f).lit + refl.y * 2.4f.lit + refl.z * 4.4f.lit) * 0.5f.lit + 0.5f.lit)
    val chromePeak = let("envChromePeak", pow(saturate(bandA * 0.65f.lit + bandB * 0.35f.lit), mix(1.5f.lit, 3.2f.lit, smoothness)))
    val sheenTint = let("envSheenTint", mix(vec3(1f.lit), albedo + vec3(0.45f.lit), 0.5f.lit))
    val rimGleam = let("envRimGleam", pow(1f.lit - nDotV, 2.5f.lit) * CHROME_RIM_STRENGTH.lit)
    val chromeGlow = let(
        "envChromeGlow",
        sheenTint * lightColor.xyz * (chromePeak * CHROME_BAND_STRENGTH.lit + rimGleam) * reflStrength * smoothness * occlusion,
    )
    return baseRefl + chromeGlow
}
