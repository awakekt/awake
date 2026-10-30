/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

internal const val LB_NONE = 0
internal const val LB_SP = 1
internal const val LB_GL = 2
internal const val LB_OP_NARROW = 3
internal const val LB_OP_EA = 4
internal const val LB_QU = 5
internal const val LB_CL = 6
internal const val LB_CP = 7
internal const val LB_EX = 8
internal const val LB_IS = 9
internal const val LB_SY = 10
internal const val LB_NS = 11
internal const val LB_CJ_PROLONGED = 12
internal const val LB_HY = 13
internal const val LB_BA = 14
internal const val LB_IN = 15
internal const val LB_B2 = 16
internal const val LB_NU = 17
internal const val LB_AL = 18
internal const val LB_ID = 19
internal const val LB_HANGUL = 20

@Suppress("MagicNumber")
internal fun isHardBreakCodePoint(cp: Int): Boolean = cp == 0x0A || cp == 0x0D

internal fun isWordUnit(cls: Int): Boolean =
    cls == LB_ID || cls == LB_HANGUL || cls == LB_CJ_PROLONGED || cls == LB_AL || cls == LB_NU

@Suppress("CyclomaticComplexMethod", "MagicNumber", "ReturnCount")
internal fun lineBreakClassOf(cp: Int): Int {
    if (cp < 0x80) return asciiLineBreakClass(cp)
    return when (cp) {
        0x00A0, 0x202F, 0x2060, 0xFEFF -> LB_GL
        0x00AB, 0x00BB, 0x2018, 0x2019, 0x201C, 0x201D, 0x2039, 0x203A -> LB_QU
        0x2010, 0x3000, 0x30A0 -> LB_BA
        0x2014 -> LB_B2
        0x2025, 0x2026 -> LB_IN
        0x203C, in 0x2047..0x2049, 0xFE56, 0xFE57, 0xFF01, 0xFF1F -> LB_EX
        0x30FC, 0xFF70 -> LB_CJ_PROLONGED
        else -> when {
            isEastAsianOpen(cp) -> LB_OP_EA
            isEastAsianClose(cp) -> LB_CL
            isNonStarter(cp) -> LB_NS
            isHangulCodePoint(cp) -> LB_HANGUL
            cp in 0xFF10..0xFF19 -> LB_NU
            cp in 0xFF21..0xFF3A || cp in 0xFF41..0xFF5A || cp in 0xFF66..0xFF9D -> LB_AL
            isIdeographOrKanaOrPictograph(cp) -> LB_ID
            else -> LB_AL
        }
    }
}

@Suppress("CyclomaticComplexMethod", "MagicNumber")
private fun asciiLineBreakClass(cp: Int): Int = when (cp) {
    0x20 -> LB_SP
    0x21, 0x3F -> LB_EX
    0x22, 0x27 -> LB_QU
    0x28, 0x5B, 0x7B -> LB_OP_NARROW
    0x29, 0x5D, 0x7D -> LB_CP
    0x2C, 0x2E, 0x3A, 0x3B -> LB_IS
    0x2D -> LB_HY
    0x2F -> LB_SY
    in 0x30..0x39 -> LB_NU
    else -> LB_AL
}

@Suppress("CyclomaticComplexMethod", "MagicNumber")
private fun isEastAsianOpen(cp: Int): Boolean = when (cp) {
    0x3008, 0x300A, 0x300C, 0x300E, 0x3010, 0x3014, 0x3016, 0x3018, 0x301A, 0x301D,
    0xFE59, 0xFE5B, 0xFE5D, 0xFF08, 0xFF3B, 0xFF5B, 0xFF5F, 0xFF62,
    -> true
    else -> false
}

@Suppress("CyclomaticComplexMethod", "MagicNumber")
private fun isEastAsianClose(cp: Int): Boolean = when (cp) {
    0x3001, 0x3002, 0x3009, 0x300B, 0x300D, 0x300F, 0x3011, 0x3015, 0x3017, 0x3019, 0x301B,
    0x301E, 0x301F, 0xFE50, 0xFE51, 0xFE52, 0xFE5A, 0xFE5C, 0xFE5E,
    0xFF09, 0xFF0C, 0xFF0E, 0xFF3D, 0xFF5D, 0xFF60, 0xFF61, 0xFF63, 0xFF64,
    -> true
    else -> false
}

@Suppress("CyclomaticComplexMethod", "MagicNumber")
private fun isNonStarter(cp: Int): Boolean = when (cp) {
    0x3003, 0x3005, 0x301C, in 0x309B..0x309E, 0x30FB, 0x30FD, 0x30FE,
    0xFE54, 0xFE55, 0xFF1A, 0xFF1B, 0xFF5E, 0xFF65, 0xFF9E, 0xFF9F,
    -> true
    else -> false
}

@Suppress("MagicNumber")
internal fun isHangulCodePoint(cp: Int): Boolean =
    cp in 0xAC00..0xD7A3 || cp in 0x1100..0x11FF || cp in 0x3130..0x318F ||
        cp in 0xA960..0xA97F || cp in 0xD7B0..0xD7FF

@Suppress("CyclomaticComplexMethod", "MagicNumber")
internal fun isIdeographOrKanaOrPictograph(cp: Int): Boolean =
    cp in 0x4E00..0x9FFF ||
        cp in 0x3040..0x30FF ||
        cp in 0x3400..0x4DBF ||
        cp in 0xF900..0xFAFF ||
        cp in 0x2E80..0x2FFF ||
        cp in 0x3006..0x3007 ||
        cp in 0x3100..0x312F ||
        cp in 0x31A0..0x31FF ||
        cp in 0x3200..0x33FF ||
        cp in 0xA000..0xA4CF ||
        cp in 0xFFE0..0xFFE6 ||
        cp in 0x1B000..0x1B16F ||
        cp in 0x1F000..0x1FAFF ||
        cp in 0x20000..0x2FA1F ||
        cp in 0x30000..0x323AF ||
        (propertiesOf(cp) and EXTENDED_PICTOGRAPHIC != 0)
