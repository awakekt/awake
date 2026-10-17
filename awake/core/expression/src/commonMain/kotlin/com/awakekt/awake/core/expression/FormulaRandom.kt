/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/**
 * Where a formula's `random()` draws from. The caller supplies it on [FormulaInputs.random], so a server
 * and its clients that share a seed draw the same numbers. A game can wrap its own generator.
 */
fun interface FormulaRandom {
    /** The next number: at least 0 and less than 1. */
    fun nextDouble(): Double

    /** Built-in sources. */
    companion object {
        /** A generator that gives the same sequence for [seed] on every target; see [SeededFormulaRandom]. */
        fun seeded(seed: Long): SeededFormulaRandom = SeededFormulaRandom(seed)
    }
}

/**
 * A SplitMix64 generator. It uses only 64-bit integer arithmetic, so a seed gives the same sequence on the
 * JVM, native and wasm.
 *
 * @property state The generator's whole state. Read it to save the generator and assign it to restore one.
 */
class SeededFormulaRandom(var state: Long) : FormulaRandom {
    /** The next 64 random bits. */
    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** The next number from the top 53 bits of [nextLong]: at least 0 and less than 1. */
    override fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * DOUBLE_UNIT

    private companion object {
        val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX_2 = 0x94D049BB133111EBuL.toLong()
        const val DOUBLE_UNIT = 1.0 / 9007199254740992.0
    }
}
