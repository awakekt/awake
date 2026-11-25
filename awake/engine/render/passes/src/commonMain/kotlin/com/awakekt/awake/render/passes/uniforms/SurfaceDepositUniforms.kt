/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformWriter
import kotlin.math.PI

@Suppress("LongParameterList")
internal fun UniformWriter.putSurfaceDeposit(
    color: Color,
    amount: Float,
    slope: Float,
    softness: Float,
    roughness: Float,
    noiseScale: Float,
    secondaryColor: Color?,
    glint: Float,
    grain: Float,
    seed: Float,
): UniformWriter {
    val clamped = amount.coerceIn(0f, 1f)
    return if (clamped > 0f) {
        val secR = secondaryColor?.r ?: (color.r * AUTO_SECONDARY_R)
        val secG = secondaryColor?.g ?: (color.g * AUTO_SECONDARY_G)
        val secB = secondaryColor?.b ?: (color.b * AUTO_SECONDARY_B)
        put(UniformFields.SurfaceDepositColor, color.r, color.g, color.b, clamped)
            .put(
                UniformFields.SurfaceDepositParams,
                slope.coerceIn(0f, 1f),
                softness.coerceIn(MIN_DEPOSIT_SOFTNESS, 1f),
                roughness.coerceIn(0f, 1f),
                noiseScale.coerceAtLeast(MIN_DEPOSIT_NOISE_SCALE),
            )
            .put(UniformFields.SurfaceDepositDetail, secR, secG, secB, glint.coerceIn(0f, 1f))
            .put(
                UniformFields.SurfaceDepositSeed,
                depositSeedAxis(seed, SEED_SALT_X),
                depositSeedAxis(seed, SEED_SALT_Z),
                depositSeedAngle(seed),
                grain.coerceIn(0f, 1f),
            )
    } else {
        put(UniformFields.SurfaceDepositColor, 0f, 0f, 0f, 0f)
            .put(UniformFields.SurfaceDepositParams, 0f, 0f, 0f, 0f)
            .put(UniformFields.SurfaceDepositDetail, 0f, 0f, 0f, 0f)
            .put(UniformFields.SurfaceDepositSeed, 0f, 0f, 0f, 0f)
    }
}

private fun depositSeedHash01(seed: Float, salt: Int): Float {
    var h = seed.toRawBits() xor salt
    h = (h xor (h ushr SEED_HASH_SHIFT_HIGH)) * SEED_HASH_MUL_A
    h = (h xor (h ushr SEED_HASH_SHIFT_MID)) * SEED_HASH_MUL_B
    h = h xor (h ushr SEED_HASH_SHIFT_HIGH)
    return (h and SEED_MASK_UNIT) / SEED_DIV_UNIT
}

private fun depositSeedAxis(seed: Float, salt: Int): Float =
    if (!seed.isFinite() || seed == 0f) 0f else (depositSeedHash01(seed, salt) * 2f - 1f) * SEED_OFFSET_RANGE

private fun depositSeedAngle(seed: Float): Float =
    if (!seed.isFinite() || seed == 0f) 0f else depositSeedHash01(seed, SEED_SALT_ANGLE) * FULL_TURN_RAD

private const val MIN_DEPOSIT_SOFTNESS = 0.02f
private const val MIN_DEPOSIT_NOISE_SCALE = 0.05f
private const val AUTO_SECONDARY_R = 0.72f
private const val AUTO_SECONDARY_G = 0.77f
private const val AUTO_SECONDARY_B = 0.85f
private const val SEED_SALT_X = 0x27d4eb2d
private const val SEED_SALT_Z = 0x165667b1
private const val SEED_SALT_ANGLE = 0x9e3779b9.toInt()
private const val SEED_HASH_SHIFT_HIGH = 16
private const val SEED_HASH_SHIFT_MID = 13
private const val SEED_HASH_MUL_A = 0x45d9f3b
private const val SEED_HASH_MUL_B = 0x7feb352d
private const val SEED_MASK_UNIT = 0x00ffffff
private const val SEED_DIV_UNIT = 16777215f
private const val SEED_OFFSET_RANGE = 250f
private const val FULL_TURN_RAD = (PI * 2.0).toFloat()
