/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * `log`, `pow` and `round` built only from IEEE 754 operations every target rounds the same way: `+`, `-`,
 * `*`, `/`, `sqrt`, `floor` and bit reinterpretation. Platform `ln` and `pow` come from different libraries
 * on the JVM, native and wasm and may differ in the last bit; these do not.
 *
 * `log` and `pow` carry their intermediates in two doubles (a value and its rounding error), so a result
 * is within about half a unit in the last place, and an exact answer such as `100 ^ 1.5` or `log(1000, 10)`
 * comes out exact. No multiply is fused with an add: Kotlin emits none on any target.
 */
@Suppress("TooManyFunctions") // The three it offers, and the exact-arithmetic steps they share.
internal object DeterministicMath {
    private const val LN2_HI = 6.93147180369123816490e-01
    private const val LN2_LO = 1.90821492927058770002e-10
    private const val INV_LN2 = 1.44269504088896338700e+00
    private const val SQRT2 = 1.4142135623730951
    private const val TWO_54 = 1.8014398509481984E16
    private const val MIN_NORMAL = 2.2250738585072014E-308
    private const val SPLITTER = 134217729.0
    private const val SPLIT_LIMIT = 1.0E290
    private const val EXP_OVERFLOW = 709.782712893384
    private const val EXP_UNDERFLOW = -745.1332191019412
    private const val TWO_52 = 4503599627370496.0
    private const val TWO_53 = 9007199254740992.0
    private const val MAX_EXACT_INT_POWER = 64.0
    private const val MANTISSA_MASK = 0x000FFFFFFFFFFFFFL
    private const val EXPONENT_ONE = 0x3FF0000000000000L
    private const val EXPONENT_BIAS = 1023
    private const val ATANH_TERMS = 11
    private const val EXP_TERMS = 15

    /** 1/3, 1/5, 1/7, ...: the series of atanh(s)/s - 1 in s². */
    private val atanhCoefficients = DoubleArray(ATANH_TERMS) { 1.0 / (2 * it + 3) }

    /** 1/n! for n in 0..[EXP_TERMS]; every factorial up to 18! is exact in a double. */
    private val inverseFactorials = DoubleArray(EXP_TERMS + 1).also {
        var factorial = 1.0
        for (n in it.indices) {
            if (n > 0) factorial *= n
            it[n] = 1.0 / factorial
        }
    }

    /** The nearest whole number, halves away from zero. */
    fun round(x: Double): Double {
        val a = abs(x)
        if (a >= TWO_52 || a.isNaN()) return x
        val whole = floor(a)
        val rounded = if (a - whole >= 0.5) whole + 1.0 else whole
        return if (x < 0.0) -rounded else rounded
    }

    /** The natural logarithm: NaN below 0, negative infinity at 0. */
    fun log(x: Double): Double = when {
        x.isNaN() || x < 0.0 -> Double.NaN
        x == 0.0 -> Double.NEGATIVE_INFINITY
        x == Double.POSITIVE_INFINITY -> x
        else -> logParts(x) { hi, lo -> hi + lo }
    }

    /** The logarithm of [x] to [base]: NaN when either is below 0 or [base] is 1. */
    fun log(x: Double, base: Double): Double = when {
        !(x >= 0.0) || !(base > 0.0) || base == 1.0 -> Double.NaN
        x == 0.0 || x.isInfinite() || base.isInfinite() -> log(x) / log(base)
        else -> logParts(x) { aHi, aLo ->
            logParts(base) { bHi, bLo ->
                val q = aHi / bHi
                twoProduct(q, bHi) { p, pError ->
                    q + (((aHi - p) - pError) + aLo - q * bLo) / bHi
                }
            }
        }
    }

    /** [x] to the power [y]. NaN for a negative [x] and a fractional [y]; infinity for 0 to a negative power. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount") // IEEE special cases first, as in every pow.
    fun pow(x: Double, y: Double): Double {
        if (y == 0.0) return 1.0
        if (x.isNaN() || y.isNaN()) return Double.NaN
        val integral = abs(y) < TWO_53 && floor(y) == y
        if (x == 0.0) return if (y > 0.0) 0.0 else Double.POSITIVE_INFINITY
        if (x < 0.0 && !integral) return Double.NaN
        val negate = x < 0.0 && abs(y) % 2.0 == 1.0
        val ax = abs(x)
        val result = when {
            integral && abs(y) <= MAX_EXACT_INT_POWER && floor(ax) == ax -> {
                val power = powWhole(ax, abs(y).toInt())
                if (y < 0.0) 1.0 / power else power
            }
            y == 0.5 -> sqrt(ax)
            ax == Double.POSITIVE_INFINITY -> if (y > 0.0) ax else 0.0
            else -> logParts(ax) { lHi, lLo ->
                val rough = y * lHi
                when {
                    rough > EXP_OVERFLOW + 1.0 -> Double.POSITIVE_INFINITY
                    rough < EXP_UNDERFLOW - 1.0 -> 0.0
                    abs(y) > SPLIT_LIMIT -> expParts(rough, y * lLo)
                    else -> twoProduct(y, lHi) { pHi, pError -> expParts(pHi, pError + y * lLo) }
                }
            }
        }
        return if (negate) -result else result
    }

    /** [base] to the whole power [exponent] by squaring; exact while every product fits 53 bits. */
    private fun powWhole(base: Double, exponent: Int): Double {
        var result = 1.0
        var square = base
        var n = exponent
        while (n > 0) {
            if (n and 1 == 1) result *= square
            n = n shr 1
            if (n > 0) square *= square
        }
        return result
    }

    /** ln([x]) for a finite positive [x], as an unevaluated sum of two doubles handed to [use]. */
    private inline fun <R> logParts(x: Double, use: (hi: Double, lo: Double) -> R): R {
        var v = x
        var k = 0
        if (v < MIN_NORMAL) {
            v *= TWO_54
            k = -54
        }
        val bits = v.toRawBits()
        k += ((bits ushr 52) and 0x7FF).toInt() - EXPONENT_BIAS
        var m = Double.fromBits((bits and MANTISSA_MASK) or EXPONENT_ONE)
        if (m > SQRT2) {
            m *= 0.5
            k += 1
        }
        // ln(m) = 2 atanh(s) with s = f / (2 + f), f = m - 1, |s| < 0.172.
        val f = m - 1.0
        val dHi = 2.0 + f
        val dLo = f - (dHi - 2.0)
        val sHi = f / dHi
        val sLo = twoProduct(sHi, dHi) { p, pError -> (((f - p) - pError) - sHi * dLo) / dHi }
        val z = sHi * sHi
        var series = atanhCoefficients[ATANH_TERMS - 1]
        for (i in ATANH_TERMS - 2 downTo 0) series = series * z + atanhCoefficients[i]
        val mLo = 2.0 * sLo + 2.0 * sHi * z * series
        val kd = k.toDouble()
        return twoSum(kd * LN2_HI, 2.0 * sHi) { hi, hiError ->
            fastTwoSum(hi, hiError + mLo + kd * LN2_LO, use)
        }
    }

    /** e to the power [hi] + [lo], for |[lo]| far below |[hi]|. */
    private fun expParts(hi: Double, lo: Double): Double = when {
        hi > EXP_OVERFLOW -> Double.POSITIVE_INFINITY
        hi < EXP_UNDERFLOW -> 0.0
        else -> {
            // e^x = 2^k e^r, |r| <= ln(2) / 2.
            val k = floor(hi * INV_LN2 + 0.5)
            twoSum(hi - k * LN2_HI, lo - k * LN2_LO) { rHi, rLo ->
                var series = inverseFactorials[EXP_TERMS]
                for (n in EXP_TERMS - 1 downTo 2) series = series * rHi + inverseFactorials[n]
                twoSum(rHi, rHi * rHi * series) { tHi, tError ->
                    val tLo = tError + rLo * (1.0 + rHi)
                    fastTwoSum(1.0, tHi) { eHi, eError -> scaleByPowerOfTwo(eHi + (eError + tLo), k.toInt()) }
                }
            }
        }
    }

    private fun scaleByPowerOfTwo(v: Double, k: Int): Double = when {
        k > EXPONENT_BIAS -> v * powerOfTwo(EXPONENT_BIAS) * powerOfTwo(k - EXPONENT_BIAS)
        k < 1 - EXPONENT_BIAS -> v * powerOfTwo(k + 54) * powerOfTwo(-54)
        else -> v * powerOfTwo(k)
    }

    private fun powerOfTwo(k: Int): Double = Double.fromBits((k + EXPONENT_BIAS).toLong() shl 52)

    /** a + b exactly, as the rounded sum and its error. */
    private inline fun <R> twoSum(a: Double, b: Double, use: (sum: Double, error: Double) -> R): R {
        val s = a + b
        val bb = s - a
        return use(s, (a - (s - bb)) + (b - bb))
    }

    /** a + b exactly for |a| >= |b|, as the rounded sum and its error. */
    private inline fun <R> fastTwoSum(a: Double, b: Double, use: (sum: Double, error: Double) -> R): R {
        val s = a + b
        return use(s, b - (s - a))
    }

    /** a * b exactly, as the rounded product and its error, by Dekker's split; no fused multiply-add. */
    private inline fun <R> twoProduct(a: Double, b: Double, use: (product: Double, error: Double) -> R): R {
        val p = a * b
        val ta = SPLITTER * a
        val aHi = ta - (ta - a)
        val aLo = a - aHi
        val tb = SPLITTER * b
        val bHi = tb - (tb - b)
        val bLo = b - bHi
        return use(p, ((aHi * bHi - p) + aHi * bLo + aLo * bHi) + aLo * bLo)
    }
}
