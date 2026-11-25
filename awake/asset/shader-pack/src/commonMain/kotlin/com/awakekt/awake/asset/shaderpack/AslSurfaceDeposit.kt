/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslLayoutHandles
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.min
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.pow
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.saturate
import com.awakekt.awake.asset.shaderdsl.sin
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z

private const val DEPOSIT_ACTIVE_EPSILON = 0.001f
private const val MIN_SLOPE_SOFTNESS = 0.04f
private const val MIN_DRIFT_SOFTNESS = 0.05f
private const val MAX_SLOPE_HIGH = 0.995f
private const val INITIAL_SLOPE_PEAK = 0.86f
private const val COVERAGE_GAIN = 1.35f
private const val DEPOSIT_GAMMA = 2.2f
private const val CHARACTER_NOISE_MULT = 3.2f
private const val TERRAIN_NOISE_MULT = 0.55f
private const val BUMP_STRENGTH = 0.38f

/**
 * Aggregated frame uniform handles for dynamic surface deposit shading.
 *
 * Keeps call sites concise while ensuring every field derives from `MaterialUniformLayouts.Frame`.
 */
internal class SurfaceDepositUniforms(
    val color: AslExpr,
    val params: AslExpr,
    val detail: AslExpr,
    val seed: AslExpr,
)

internal fun surfaceDepositUniforms(frame: AslLayoutHandles): SurfaceDepositUniforms = SurfaceDepositUniforms(
    color = frame.value("surfaceDepositColor"),
    params = frame.value("surfaceDepositParams"),
    detail = frame.value("surfaceDepositDetail"),
    seed = frame.value("surfaceDepositSeed"),
)

private class NoiseGrad(val value: AslExpr, val dx: AslExpr, val dy: AslExpr)

private class DepositSample(
    val coverage: AslExpr,
    val texTone: AslExpr,
    val gritMod: AslExpr,
    val glint: AslExpr,
    val bumpedNormal: AslExpr,
    val grain: AslExpr,
    val grainStrength: AslExpr,
)

/**
 * Dave Hoskins 2D-to-1D scalar hash (`0..1`). Uses only `fract`, multiply, and dot — zero trig,
 * bit-consistent across Vulkan/SPIR-V and WebGPU/WGSL, and float32-crisp at large open-world coordinates.
 */
@Suppress("MagicNumber")
private fun AslBlockBuilder.hash12(cell: AslExpr, tag: String): AslExpr {
    val p3 = let("${tag}P3", fract(vec3(cell.x, cell.y, cell.x) * 0.1031f.lit))
    val p3s = let("${tag}P3s", p3 + vec3(dot(p3, vec3(p3.y + 33.33f.lit, p3.z + 33.33f.lit, p3.x + 33.33f.lit))))
    return let("${tag}H", fract((p3s.x + p3s.y) * p3s.z))
}

/** Smooth Hermite bilinear value noise (`0..1`) over integer lattice cells. */
@Suppress("MagicNumber")
private fun AslBlockBuilder.valueNoise2d(p: AslExpr, tag: String): AslExpr {
    val cell = let("${tag}I", floor(p))
    val f = let("${tag}F", p - cell)
    val u = let("${tag}U", f * f * (vec2(3f.lit, 3f.lit) - f * 2f.lit))
    val h00 = hash12(cell, "${tag}00")
    val h10 = hash12(cell + vec2(1f.lit, 0f.lit), "${tag}10")
    val h01 = hash12(cell + vec2(0f.lit, 1f.lit), "${tag}01")
    val h11 = hash12(cell + vec2(1f.lit, 1f.lit), "${tag}11")
    return let("${tag}V", mix(mix(h00, h10, u.x), mix(h01, h11, u.x), u.y))
}

/**
 * Smooth Hermite bilinear value noise PLUS its exact analytical gradient `(dV/dx, dV/dy)` from the
 * same four corner hashes — zero extra hash calls for 3D micro-bump normal perturbation.
 */
@Suppress("MagicNumber")
private fun AslBlockBuilder.valueNoise2dGrad(p: AslExpr, tag: String): NoiseGrad {
    val cell = let("${tag}I", floor(p))
    val f = let("${tag}F", p - cell)
    val u = let("${tag}U", f * f * (vec2(3f.lit, 3f.lit) - f * 2f.lit))
    val du = let("${tag}Du", f * (vec2(1f.lit, 1f.lit) - f) * 6f.lit)
    val h00 = hash12(cell, "${tag}00")
    val h10 = hash12(cell + vec2(1f.lit, 0f.lit), "${tag}10")
    val h01 = hash12(cell + vec2(0f.lit, 1f.lit), "${tag}01")
    val h11 = hash12(cell + vec2(1f.lit, 1f.lit), "${tag}11")
    val k1 = let("${tag}K1", h10 - h00)
    val k2 = let("${tag}K2", h01 - h00)
    val k3 = let("${tag}K3", h00 - h10 - h01 + h11)
    val value = let("${tag}V", h00 + k1 * u.x + k2 * u.y + k3 * u.x * u.y)
    val dx = let("${tag}Dx", du.x * (k1 + k3 * u.y))
    val dy = let("${tag}Dy", du.y * (k2 + k3 * u.x))
    return NoiseGrad(value, dx, dy)
}

/**
 * Evaluates 4-scale non-repeating hash-fBm deposit coverage, dual-tone texture height, micro-grit,
 * optional view-reactive sparkle glint, and analytical micro-bump normal.
 *
 * Call ONLY inside `iff(effectiveAmount gt 0.001f.lit)` so inactive frames pay zero fragment ALU.
 */
@Suppress("LongParameterList", "LongMethod", "MagicNumber")
private fun AslBlockBuilder.evaluateSurfaceDeposit(
    worldNormal: AslExpr,
    samplePosition: AslExpr,
    cameraPosition: AslExpr,
    deposit: SurfaceDepositUniforms,
    drawSeed: AslExpr,
    effectiveAmount: AslExpr,
    frequencyMultiplier: Float,
    prefix: String,
): DepositSample {
    val sp = let("${prefix}Pos", samplePosition)
    val freq = let("${prefix}Freq", max(deposit.params.w, 0.05f.lit) * frequencyMultiplier.lit)
    val grainStrength = let("${prefix}GrainStr", saturate(deposit.seed.w))
    // Rotate XZ by the per-storm wind angle and shift by storm + per-entity seeds.
    val rotCos = let("${prefix}Cos", cos(deposit.seed.z))
    val rotSin = let("${prefix}Sin", sin(deposit.seed.z))
    val offX = let("${prefix}OffX", deposit.seed.x + drawSeed * 17.31f.lit)
    val offZ = let("${prefix}OffZ", deposit.seed.y + drawSeed * 31.17f.lit)
    val rx = let("${prefix}Rx", (sp.x * rotCos - sp.z * rotSin + offX) * freq)
    val rz = let("${prefix}Rz", (sp.x * rotSin + sp.z * rotCos + offZ) * freq)
    val ry = let("${prefix}Ry", sp.y * freq)

    // 4-scale non-repeating procedural texture:
    // 1. Anisotropic wind-stretched macro drifts (breaks polka-dot tiling)
    val macro = valueNoise2d(vec2(rx * 0.62f.lit + ry * 0.37f.lit, rz * 1.28f.lit - ry * 0.29f.lit), "${prefix}Mac")
    // 2. Rotated medium clumps + analytical slope for natural mound relief
    val clump = valueNoise2dGrad(
        vec2(rx * 2.15f.lit - rz * 1.45f.lit + ry * 0.73f.lit + 19.4f.lit, rx * 1.45f.lit + rz * 2.15f.lit - ry * 0.61f.lit + 7.8f.lit),
        "${prefix}Clm",
    )
    // 3. Crisp granular crust/cinder texture + analytical micro-bump gradient
    val grain = valueNoise2dGrad(
        vec2(rx * 7.8f.lit + rz * 3.1f.lit + ry * 2.4f.lit + 43.1f.lit, -rx * 3.1f.lit + rz * 7.8f.lit - ry * 1.9f.lit + 29.7f.lit),
        "${prefix}Grn",
    )
    // 4. Fine high-frequency powder / volcanic cinder speckle
    val speckle = hash12(floor(vec2(rx * 22f.lit + ry * 7f.lit, rz * 22f.lit - ry * 5f.lit)), "${prefix}Spk")

    val driftNoise = let("${prefix}Noise", saturate(macro * 0.56f.lit + clump.value * 0.30f.lit + grain.value * 0.14f.lit))
    val softness = let("${prefix}Soft", max(deposit.params.y, MIN_SLOPE_SOFTNESS.lit))
    val rimJitter = let("${prefix}Rim", (clump.value - 0.5f.lit) * softness * 0.85f.lit + (grain.value - 0.5f.lit) * softness * 0.35f.lit)
    val slopeLow = let("${prefix}SlopeLow", mix(INITIAL_SLOPE_PEAK.lit, deposit.params.x, effectiveAmount) + rimJitter)
    val slopeHigh = let("${prefix}SlopeHigh", min(slopeLow + softness, MAX_SLOPE_HIGH.lit))
    val slopeT = let("${prefix}SlopeT", clamp((worldNormal.y - slopeLow) / max(slopeHigh - slopeLow, MIN_SLOPE_SOFTNESS.lit), 0f.lit, 1f.lit))
    val slopeMask = let("${prefix}SlopeMask", slopeT * slopeT * (3f.lit - 2f.lit * slopeT))

    val driftEdge = let("${prefix}DriftEdge", 1f.lit - effectiveAmount)
    val driftSoft = let("${prefix}DriftSoft", max(softness * 0.72f.lit, MIN_DRIFT_SOFTNESS.lit))
    val driftT = let(
        "${prefix}DriftT",
        clamp((driftNoise - (driftEdge - driftSoft)) / max(driftSoft * 2f.lit, MIN_DRIFT_SOFTNESS.lit), 0f.lit, 1f.lit),
    )
    val driftMask = let("${prefix}DriftMask", driftT * driftT * (3f.lit - 2f.lit * driftT))
    val coverage = let("${prefix}Coverage", slopeMask * driftMask * saturate(effectiveAmount * COVERAGE_GAIN.lit))

    val rawTone = let(
        "${prefix}RawTone",
        saturate(macro * 0.24f.lit + clump.value * 0.34f.lit + grain.value * 0.28f.lit + speckle * 0.14f.lit),
    )
    val toneT = let("${prefix}ToneT", clamp((rawTone - 0.18f.lit) / 0.68f.lit, 0f.lit, 1f.lit))
    val texTone = let("${prefix}Tone", toneT * toneT * (3f.lit - 2f.lit * toneT))
    val gritMod = let(
        "${prefix}Grit",
        1f.lit + ((grain.value - 0.5f.lit) * 0.32f.lit + (speckle - 0.5f.lit) * 0.22f.lit) * grainStrength,
    )

    // Analytical micro-bump normal perturbation so sunlit ridges and shadowed hollows read in 3D.
    val gx = let("${prefix}Gx", clump.dx * 0.42f.lit + grain.dx * 0.58f.lit)
    val gz = let("${prefix}Gz", clump.dy * 0.42f.lit + grain.dy * 0.58f.lit)
    val bumpAmp = let("${prefix}Bump", grainStrength * coverage * BUMP_STRENGTH.lit)
    val bumpedNormal = let(
        "${prefix}Norm",
        normalize(worldNormal + vec3(-gx * bumpAmp, 0f.lit, -gz * bumpAmp)),
    )

    // Optional view-reactive pinpoint crystalline glint (0 for matte volcanic ash / dust).
    val viewDir = let("${prefix}View", normalize(cameraPosition.xyz - sp))
    val glintHash = hash12(
        floor(vec2(rx * 34f.lit + ry * 14f.lit + viewDir.x * 3.5f.lit, rz * 34f.lit - ry * 11f.lit + viewDir.z * 3.5f.lit)),
        "${prefix}Gln",
    )
    val glintGate = let("${prefix}GlnGate", saturate((glintHash - 0.80f.lit) / 0.20f.lit))
    val glint = let(
        "${prefix}Glint",
        glintGate * glintGate * glintGate * saturate(deposit.detail.w) * coverage * saturate(bumpedNormal.y),
    )

    return DepositSample(coverage, texTone, gritMod, glint, bumpedNormal, grain.value, grainStrength)
}

/**
 * Modulates the frame's deposit amount by this draw's `drawFade.y` (`1 - surfaceDepositScale`),
 * clamped to `[0, 1]`. Default `drawFade.y == 0` yields `1.0x`; `drawFade.y == 1` yields `0`.
 */
internal fun AslBlockBuilder.effectiveSurfaceDepositAmount(depositColor: AslExpr, drawFade: AslExpr): AslExpr {
    val drawScale = clamp(1f.lit - drawFade.y, 0f.lit, 2f.lit)
    return saturate(depositColor.w * drawScale)
}

/**
 * Applies textured, micro-bumped dynamic upward-facing surface deposit to linear PBR [albedo],
 * [metallic], [roughness], and mutable [worldNormal] before lighting.
 * Guarded by `iff(effectiveAmount > 0.001)` for zero idle cost.
 */
@Suppress("LongParameterList", "MagicNumber")
internal fun AslBlockBuilder.applyLitSurfaceDeposit(
    albedo: AslExpr,
    metallic: AslExpr,
    roughness: AslExpr,
    worldNormal: AslExpr,
    samplePosition: AslExpr,
    cameraPosition: AslExpr,
    deposit: SurfaceDepositUniforms,
    drawFade: AslExpr,
    characterScale: Boolean = false,
) {
    val effectiveAmount = let("depositAmount", effectiveSurfaceDepositAmount(deposit.color, drawFade))
    iff(effectiveAmount gt DEPOSIT_ACTIVE_EPSILON.lit) {
        val freqMult = if (characterScale) CHARACTER_NOISE_MULT else 1f
        val sample = evaluateSurfaceDeposit(
            worldNormal, samplePosition, cameraPosition, deposit, drawFade.z, effectiveAmount, freqMult, "litDep",
        )
        val primaryLin = let("litDepPri", pow(max(deposit.color.rgb, vec3(0f.lit)), vec3(DEPOSIT_GAMMA.lit)))
        val secondaryLin = let("litDepSec", pow(max(deposit.detail.rgb, vec3(0f.lit)), vec3(DEPOSIT_GAMMA.lit)))
        val baseLuma = let("litDepLuma", dot(albedo, vec3(0.299f.lit, 0.587f.lit, 0.114f.lit)))
        val reliefCarry = let("litDepRelief", mix(0.84f.lit + 0.30f.lit * saturate(baseLuma * 1.4f.lit), 1f.lit, sample.coverage * sample.coverage))
        val depositLinear = let(
            "litDepColor",
            mix(secondaryLin, primaryLin, sample.texTone) * sample.gritMod * reliefCarry + vec3(sample.glint * 0.45f.lit),
        )
        val texturedRoughness = let(
            "litDepRough",
            clamp(deposit.params.z + (0.5f.lit - sample.grain) * 0.18f.lit * sample.grainStrength, 0.08f.lit, 1f.lit),
        )
        assign(albedo, mix(albedo, depositLinear, sample.coverage))
        assign(roughness, mix(roughness, texturedRoughness, sample.coverage))
        assign(metallic, mix(metallic, 0f.lit, sample.coverage))
        assign(worldNormal, sample.bumpedNormal)
    }
}

/**
 * Applies textured, micro-bumped dynamic upward-facing surface deposit to a display-referred
 * [unlitRgb] variable (e.g. unlit skinned characters or unlit props) with directional micro-bump
 * sculptural relief so deposits read with crisp 3D texture.
 * Guarded by `iff(effectiveAmount > 0.001)` for zero idle cost.
 */
@Suppress("LongParameterList", "MagicNumber")
internal fun AslBlockBuilder.applyUnlitSurfaceDeposit(
    unlitRgb: AslExpr,
    worldNormal: AslExpr,
    samplePosition: AslExpr,
    cameraPosition: AslExpr,
    deposit: SurfaceDepositUniforms,
    drawFade: AslExpr,
    characterScale: Boolean = false,
) {
    val effectiveAmount = let("unlitDepositAmount", effectiveSurfaceDepositAmount(deposit.color, drawFade))
    iff(effectiveAmount gt DEPOSIT_ACTIVE_EPSILON.lit) {
        val freqMult = if (characterScale) CHARACTER_NOISE_MULT else 1f
        val sample = evaluateSurfaceDeposit(
            worldNormal, samplePosition, cameraPosition, deposit, drawFade.z, effectiveAmount, freqMult, "unlitDep",
        )
        val keyDir = let("unlitDepKey", normalize(vec3(0.32f.lit, 0.88f.lit, 0.34f.lit)))
        val bumpLight = let("unlitDepBumpL", 0.78f.lit + 0.28f.lit * saturate(dot(sample.bumpedNormal, keyDir)))
        val baseLuma = let("unlitDepLuma", dot(unlitRgb, vec3(0.299f.lit, 0.587f.lit, 0.114f.lit)))
        val reliefCarry = let("unlitDepRelief", mix(0.86f.lit + 0.24f.lit * saturate(baseLuma), 1f.lit, sample.coverage * sample.coverage))
        val depositRgb = let(
            "unlitDepCol",
            saturate(mix(deposit.detail.rgb, deposit.color.rgb, sample.texTone) * sample.gritMod * bumpLight * reliefCarry + vec3(sample.glint * 0.40f.lit)),
        )
        assign(unlitRgb, mix(unlitRgb, depositRgb, sample.coverage))
    }
}

/**
 * Applies textured, micro-bumped dynamic upward-facing surface deposit to display-referred terrain
 * [albedo] and perturbs mutable [worldNormal] before sun and shadow lighting.
 * Guarded by `iff(depositColor.w > 0.001)` for zero idle cost.
 */
@Suppress("MagicNumber")
fun AslBlockBuilder.applyTerrainSurfaceDeposit(
    albedo: AslExpr,
    worldNormal: AslExpr,
    surfacePosition: AslExpr,
    terrain: TerrainClipmapOutputs,
): AslExpr {
    val deposit = SurfaceDepositUniforms(
        color = terrain.surfaceDepositColor,
        params = terrain.surfaceDepositParams,
        detail = terrain.surfaceDepositDetail,
        seed = terrain.surfaceDepositSeed,
    )
    val out = variable("terrainDepositAlbedo", albedo)
    iff(deposit.color.w gt DEPOSIT_ACTIVE_EPSILON.lit) {
        val sample = evaluateSurfaceDeposit(
            worldNormal = worldNormal,
            samplePosition = surfacePosition,
            cameraPosition = terrain.cascades.cameraPosition,
            deposit = deposit,
            drawSeed = 0f.lit,
            effectiveAmount = deposit.color.w,
            frequencyMultiplier = TERRAIN_NOISE_MULT,
            prefix = "terDep",
        )
        val baseLuma = let("terDepLuma", dot(albedo, vec3(0.299f.lit, 0.587f.lit, 0.114f.lit)))
        val reliefCarry = let("terDepRelief", mix(0.84f.lit + 0.28f.lit * saturate(baseLuma), 1f.lit, sample.coverage * sample.coverage))
        val depositRgb = let(
            "terDepCol",
            saturate(mix(deposit.detail.rgb, deposit.color.rgb, sample.texTone) * sample.gritMod * reliefCarry + vec3(sample.glint * 0.38f.lit)),
        )
        assign(out, mix(out, depositRgb, sample.coverage))
        assign(worldNormal, sample.bumpedNormal)
    }
    return out
}
