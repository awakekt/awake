/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFunctionHandle
import com.awakekt.awake.asset.shaderdsl.AslLayoutHandles
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslType
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.cos
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.fract
import com.awakekt.awake.asset.shaderdsl.gt
import com.awakekt.awake.asset.shaderdsl.length
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
import com.awakekt.awake.asset.shaderdsl.select
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
import com.awakekt.awake.core.geometry.GpuDataShape

private const val DEPOSIT_ACTIVE_EPSILON = 0.001f
private const val MIN_SLOPE_SOFTNESS = 0.04f
private const val MIN_DRIFT_SOFTNESS = 0.06f
private const val MAX_SLOPE_HIGH = 0.995f
private const val INITIAL_SLOPE_PEAK = 0.88f
private const val COVERAGE_GAIN = 1.32f
private const val DEPOSIT_GAMMA = 2.2f
private const val CHARACTER_NOISE_MULT = 3.2f
private const val TERRAIN_NOISE_MULT = 0.55f
private const val BUMP_STRENGTH = 0.18f

/** 2D Simplex skew factor `(sqrt(3) - 1) / 2`. */
private const val SIMPLEX_K1 = 0.3660254f

/** 2D Simplex unskew factor `(3 - sqrt(3)) / 6`. */
private const val SIMPLEX_K2 = 0.21132487f

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

private class SimplexGrad(val value: AslExpr, val dx: AslExpr, val dy: AslExpr)

private class DepositSample(
    val coverage: AslExpr,
    val texTone: AslExpr,
    val softMod: AslExpr,
    val glint: AslExpr,
    val bumpedNormal: AslExpr,
    val fine: AslExpr,
    val grainStrength: AslExpr,
)

/**
 * Dave Hoskins 2D-to-2D gradient hash in `[-1, 1]^2`. Uses only `fract`, multiply, and dot —
 * zero trig, bit-consistent across Vulkan/SPIR-V and WebGPU/WGSL, and float32-crisp at large
 * open-world coordinates.
 */
@Suppress("MagicNumber")
private fun AslBlockBuilder.grad2(cell: AslExpr, tag: String): Pair<AslExpr, AslExpr> {
    val c = let("${tag}C", cell)
    val p3 = let("${tag}P3", fract(vec3(c.x * 0.1031f.lit, c.y * 0.1030f.lit, c.x * 0.0973f.lit)))
    val p3s = let("${tag}P3s", p3 + vec3(dot(p3, vec3(p3.y + 33.33f.lit, p3.z + 33.33f.lit, p3.x + 33.33f.lit))))
    val gx = let("${tag}Gx", fract((p3s.x + p3s.y) * p3s.z) * 2f.lit - 1f.lit)
    val gy = let("${tag}Gy", fract((p3s.x + p3s.z) * p3s.y) * 2f.lit - 1f.lit)
    return gx to gy
}

/**
 * Isotropic 2D Simplex noise (`0..1`) with exact analytical C3-smooth derivatives `(dV/dx, dV/dy)`.
 *
 * Uses an equilateral triangle simplex lattice with a `C4` radial kernel (`max(0.5 - r^2, 0)^4`),
 * eliminating both square-grid lattice seams and pixelated block artifacts while needing only
 * three gradient lookups per octave instead of four.
 */
@Suppress("LongMethod", "MagicNumber")
private fun AslBlockBuilder.simplexNoise2dGrad(pExpr: AslExpr, tag: String): SimplexGrad {
    val p = let("${tag}P", pExpr)
    val s = let("${tag}S", (p.x + p.y) * SIMPLEX_K1.lit)
    val cell = let("${tag}I", floor(vec2(p.x + s, p.y + s)))
    val t = let("${tag}T", (cell.x + cell.y) * SIMPLEX_K2.lit)
    val x0 = let("${tag}X0", p.x - (cell.x - t))
    val y0 = let("${tag}Y0", p.y - (cell.y - t))
    val i1x = let("${tag}I1x", select(0f.lit, 1f.lit, x0 gt y0))
    val i1y = let("${tag}I1y", 1f.lit - i1x)
    val x1 = let("${tag}X1", x0 - i1x + SIMPLEX_K2.lit)
    val y1 = let("${tag}Y1", y0 - i1y + SIMPLEX_K2.lit)
    val x2 = let("${tag}X2", x0 - 1f.lit + (2f * SIMPLEX_K2).lit)
    val y2 = let("${tag}Y2", y0 - 1f.lit + (2f * SIMPLEX_K2).lit)

    val (g0x, g0y) = grad2(cell, "${tag}0")
    val (g1x, g1y) = grad2(cell + vec2(i1x, i1y), "${tag}1")
    val (g2x, g2y) = grad2(cell + vec2(1f.lit, 1f.lit), "${tag}2")

    val m0 = let("${tag}M0", max(0.5f.lit - (x0 * x0 + y0 * y0), 0f.lit))
    val m1 = let("${tag}M1", max(0.5f.lit - (x1 * x1 + y1 * y1), 0f.lit))
    val m2 = let("${tag}M2", max(0.5f.lit - (x2 * x2 + y2 * y2), 0f.lit))
    val m02 = let("${tag}M02", m0 * m0)
    val m12 = let("${tag}M12", m1 * m1)
    val m22 = let("${tag}M22", m2 * m2)
    val m04 = let("${tag}M04", m02 * m02)
    val m14 = let("${tag}M14", m12 * m12)
    val m24 = let("${tag}M24", m22 * m22)

    val d0 = let("${tag}D0", g0x * x0 + g0y * y0)
    val d1 = let("${tag}D1", g1x * x1 + g1y * y1)
    val d2 = let("${tag}D2", g2x * x2 + g2y * y2)

    val raw = let("${tag}Raw", 55f.lit * (m04 * d0 + m14 * d1 + m24 * d2))
    val value = let("${tag}V", saturate(raw * 0.5f.lit + 0.5f.lit))
    val dx = let(
        "${tag}Dx",
        27.5f.lit * (
            m02 * m0 * (m0 * g0x - 8f.lit * d0 * x0) +
                m12 * m1 * (m1 * g1x - 8f.lit * d1 * x1) +
                m22 * m2 * (m2 * g2x - 8f.lit * d2 * x2)
            ),
    )
    val dy = let(
        "${tag}Dy",
        27.5f.lit * (
            m02 * m0 * (m0 * g0y - 8f.lit * d0 * y0) +
                m12 * m1 * (m1 * g1y - 8f.lit * d1 * y1) +
                m22 * m2 * (m2 * g2y - 8f.lit * d2 * y2)
            ),
    )
    return SimplexGrad(value, dx, dy)
}

/**
 * Declares a single shared `simplexNoise2dGrad(p: vec2<f32>) -> vec3<f32>` helper on this shader
 * returning `(value, dV/dx, dV/dy)`. Calling this helper instead of inlining 3-octave Simplex noise
 * at every deposit site cuts SPIR-V size and mobile register pressure substantially.
 */
internal fun AslShaderBuilder.declareSurfaceDepositNoise(): AslFunctionHandle =
    fn("simplexNoise2dGrad", returns = AslType.Data(GpuDataShape.Vec3)) {
        val p by param(GpuDataShape.Vec2)
        val g = simplexNoise2dGrad(p, "sn")
        returnValue(vec3(g.value, g.dx, g.dy))
    }

private fun AslBlockBuilder.sampleSimplexGrad(
    pExpr: AslExpr,
    tag: String,
    simplexFn: AslFunctionHandle?,
): SimplexGrad = if (simplexFn != null) {
    val s = let("${tag}S", simplexFn(pExpr))
    SimplexGrad(s.x, s.y, s.z)
} else {
    simplexNoise2dGrad(pExpr, tag)
}

/**
 * Evaluates 3-octave domain-warped isotropic 2D Simplex deposit coverage, silky dual-tone texture
 * height, analytical C3-smooth micro-bump normal, and optional smooth crystalline glint.
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
    simplexFn: AslFunctionHandle? = null,
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

    // 3-octave domain-warped 2D Simplex noise (equilateral triangles, zero boxes, zero plaid):
    // 1. Flowing wind-sculpted macro drifts
    val macro = sampleSimplexGrad(
        vec2(rx * 0.42f.lit + ry * 0.25f.lit, rz * 0.68f.lit - ry * 0.21f.lit),
        "${prefix}Mac",
        simplexFn,
    )
    // 2. Domain-warped soft billowy pillows/mounds
    val pillow = sampleSimplexGrad(
        vec2(
            rx * 1.35f.lit + (macro.value - 0.5f.lit) * 1.05f.lit + ry * 0.52f.lit + 17.3f.lit,
            rz * 1.35f.lit - (macro.value - 0.5f.lit) * 0.85f.lit - ry * 0.44f.lit + 9.1f.lit,
        ),
        "${prefix}Plw",
        simplexFn,
    )
    // 3. Soft fine powdery crust / dry dust texture (smooth C4 simplex, zero pixel boxes)
    val fine = sampleSimplexGrad(
        vec2(
            rx * 4.2f.lit + (pillow.value - 0.5f.lit) * 0.65f.lit + ry * 1.4f.lit + 41.7f.lit,
            rz * 4.2f.lit + (macro.value - 0.5f.lit) * 0.65f.lit - ry * 1.1f.lit + 23.9f.lit,
        ),
        "${prefix}Fin",
        simplexFn,
    )

    val driftNoise = let("${prefix}Noise", saturate(macro.value * 0.60f.lit + pillow.value * 0.28f.lit + fine.value * 0.12f.lit))
    val softness = let("${prefix}Soft", max(deposit.params.y, MIN_SLOPE_SOFTNESS.lit))
    val rimJitter = let("${prefix}Rim", (pillow.value - 0.5f.lit) * softness * 0.65f.lit + (fine.value - 0.5f.lit) * softness * 0.22f.lit)
    val slopeLow = let("${prefix}SlopeLow", mix(INITIAL_SLOPE_PEAK.lit, deposit.params.x, effectiveAmount) + rimJitter)
    val slopeHigh = let("${prefix}SlopeHigh", min(slopeLow + softness, MAX_SLOPE_HIGH.lit))
    val slopeT = let("${prefix}SlopeT", clamp((worldNormal.y - slopeLow) / max(slopeHigh - slopeLow, MIN_SLOPE_SOFTNESS.lit), 0f.lit, 1f.lit))
    val slopeMask = let("${prefix}SlopeMask", slopeT * slopeT * (3f.lit - 2f.lit * slopeT))

    val driftEdge = let("${prefix}DriftEdge", 1f.lit - effectiveAmount)
    val driftSoft = let("${prefix}DriftSoft", max(softness * 0.85f.lit, MIN_DRIFT_SOFTNESS.lit))
    val driftT = let(
        "${prefix}DriftT",
        clamp((driftNoise - (driftEdge - driftSoft)) / max(driftSoft * 2f.lit, MIN_DRIFT_SOFTNESS.lit), 0f.lit, 1f.lit),
    )
    val driftMask = let("${prefix}DriftMask", driftT * driftT * (3f.lit - 2f.lit * driftT))
    val coverage = let("${prefix}Coverage", slopeMask * driftMask * saturate(effectiveAmount * COVERAGE_GAIN.lit))

    val rawTone = let(
        "${prefix}RawTone",
        saturate(macro.value * 0.32f.lit + pillow.value * 0.44f.lit + fine.value * 0.24f.lit),
    )
    val toneT = let("${prefix}ToneT", clamp((rawTone - 0.18f.lit) / 0.66f.lit, 0f.lit, 1f.lit))
    val texTone = let("${prefix}Tone", toneT * toneT * (3f.lit - 2f.lit * toneT))
    val softMod = let(
        "${prefix}Mod",
        1f.lit + (fine.value - 0.5f.lit) * 0.20f.lit * grainStrength,
    )

    // Smooth C3 analytical micro-bump normal (pillowy mounds + subtle fine powder).
    val gx = let("${prefix}Gx", pillow.dx * 0.58f.lit + fine.dx * 0.42f.lit)
    val gz = let("${prefix}Gz", pillow.dy * 0.58f.lit + fine.dy * 0.42f.lit)
    val bumpAmp = let("${prefix}Bump", grainStrength * coverage * BUMP_STRENGTH.lit)
    val bumpedNormal = let(
        "${prefix}Norm",
        normalize(worldNormal + vec3(-gx * bumpAmp, 0f.lit, -gz * bumpAmp)),
    )

    // Smooth organic pinpoint snow-crystal shimmer (0 for matte volcanic ash/dust, zero square blocks).
    val viewDir = let("${prefix}View", normalize(cameraPosition.xyz - sp))
    val glintWave = let(
        "${prefix}GlnW",
        saturate(fine.value * 0.55f.lit + pillow.value * 0.45f.lit + 0.14f.lit * sin(rx * 11f.lit + rz * 7f.lit + viewDir.x * 5f.lit) * cos(rx * 8f.lit - rz * 13f.lit + viewDir.z * 5f.lit)),
    )
    val glintGate = let("${prefix}GlnGate", saturate((glintWave - 0.76f.lit) / 0.24f.lit))
    val glint = let(
        "${prefix}Glint",
        glintGate * glintGate * glintGate * saturate(deposit.detail.w) * coverage * saturate(bumpedNormal.y),
    )

    return DepositSample(coverage, texTone, softMod, glint, bumpedNormal, fine.value, grainStrength)
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
 * Applies smooth, textured dynamic upward-facing surface deposit to linear PBR [albedo],
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
    simplexFn: AslFunctionHandle? = null,
) {
    val effectiveAmount = let("depositAmount", effectiveSurfaceDepositAmount(deposit.color, drawFade))
    iff(effectiveAmount gt DEPOSIT_ACTIVE_EPSILON.lit) {
        val freqMult = if (characterScale) CHARACTER_NOISE_MULT else 1f
        val sample = evaluateSurfaceDeposit(
            worldNormal,
            samplePosition,
            cameraPosition,
            deposit,
            drawFade.z,
            effectiveAmount,
            freqMult,
            "litDep",
            simplexFn,
        )
        val primaryLin = let("litDepPri", pow(max(deposit.color.rgb, vec3(0f.lit)), vec3(DEPOSIT_GAMMA.lit)))
        val secondaryLin = let("litDepSec", pow(max(deposit.detail.rgb, vec3(0f.lit)), vec3(DEPOSIT_GAMMA.lit)))
        val baseLuma = let("litDepLuma", dot(albedo, vec3(0.299f.lit, 0.587f.lit, 0.114f.lit)))
        val reliefCarry = let("litDepRelief", mix(0.88f.lit + 0.22f.lit * saturate(baseLuma * 1.4f.lit), 1f.lit, sample.coverage * sample.coverage))
        val depositLinear = let(
            "litDepColor",
            mix(secondaryLin, primaryLin, sample.texTone) * sample.softMod * reliefCarry + vec3(sample.glint * 0.35f.lit),
        )
        val texturedRoughness = let(
            "litDepRough",
            clamp(deposit.params.z + (0.5f.lit - sample.fine) * 0.12f.lit * sample.grainStrength, 0.08f.lit, 1f.lit),
        )
        assign(albedo, mix(albedo, depositLinear, sample.coverage))
        assign(roughness, mix(roughness, texturedRoughness, sample.coverage))
        assign(metallic, mix(metallic, 0f.lit, sample.coverage))
        assign(worldNormal, sample.bumpedNormal)
    }
}

/**
 * Applies smooth, textured dynamic upward-facing surface deposit to a display-referred [unlitRgb]
 * variable (e.g. unlit skinned characters or unlit props) with soft directional mound shading.
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
    simplexFn: AslFunctionHandle? = null,
) {
    val effectiveAmount = let("unlitDepositAmount", effectiveSurfaceDepositAmount(deposit.color, drawFade))
    iff(effectiveAmount gt DEPOSIT_ACTIVE_EPSILON.lit) {
        val freqMult = if (characterScale) CHARACTER_NOISE_MULT else 1f
        val sample = evaluateSurfaceDeposit(
            worldNormal,
            samplePosition,
            cameraPosition,
            deposit,
            drawFade.z,
            effectiveAmount,
            freqMult,
            "unlitDep",
            simplexFn,
        )
        val keyDir = let("unlitDepKey", normalize(vec3(0.32f.lit, 0.88f.lit, 0.34f.lit)))
        val bumpLight = let("unlitDepBumpL", 0.84f.lit + 0.20f.lit * saturate(dot(sample.bumpedNormal, keyDir)))
        val baseLuma = let("unlitDepLuma", dot(unlitRgb, vec3(0.299f.lit, 0.587f.lit, 0.114f.lit)))
        val reliefCarry = let("unlitDepRelief", mix(0.88f.lit + 0.18f.lit * saturate(baseLuma), 1f.lit, sample.coverage * sample.coverage))
        val depositRgb = let(
            "unlitDepCol",
            saturate(mix(deposit.detail.rgb, deposit.color.rgb, sample.texTone) * sample.softMod * bumpLight * reliefCarry + vec3(sample.glint * 0.32f.lit)),
        )
        assign(unlitRgb, mix(unlitRgb, depositRgb, sample.coverage))
    }
}

/**
 * Applies smooth, textured dynamic upward-facing surface deposit to display-referred terrain
 * [albedo] and gently perturbs mutable [worldNormal] before sun and shadow lighting.
 *
 * When a terrain pixel already closely matches the deposit colour (for example fresh snow falling
 * on an already-snowy alpine hillside), both recoloring and bump strength soften smoothly so
 * existing painted snow stays fluffy instead of roughening into noise.
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
        // Surfaces already matching the deposit colour (e.g. snow-on-snow terrain) keep their smooth painted look.
        val colorDist = let("terDepDist", length(albedo - deposit.color.rgb))
        val contrastT = let("terDepContrast", clamp((colorDist - 0.10f.lit) / 0.32f.lit, 0f.lit, 1f.lit))
        val effectiveCov = let("terDepEffCov", sample.coverage * contrastT)
        val baseLuma = let("terDepLuma", dot(albedo, vec3(0.299f.lit, 0.587f.lit, 0.114f.lit)))
        val reliefCarry = let("terDepRelief", mix(0.88f.lit + 0.20f.lit * saturate(baseLuma), 1f.lit, effectiveCov * effectiveCov))
        val depositRgb = let(
            "terDepCol",
            saturate(mix(deposit.detail.rgb, deposit.color.rgb, sample.texTone) * sample.softMod * reliefCarry + vec3(sample.glint * 0.30f.lit)),
        )
        assign(out, mix(out, depositRgb, effectiveCov))
        assign(worldNormal, normalize(mix(worldNormal, sample.bumpedNormal, contrastT)))
    }
    return out
}
