/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

/**
 * Unicode code points in UTF-16 text, which common Kotlin has no functions for.
 *
 * A code point past U+FFFF, such as an emoji or a CJK Extension B ideograph, is a surrogate pair:
 * two `Char`s. A surrogate without its partner is read as itself, one `Char`, so malformed text
 * still steps forward one `Char` at a time rather than failing.
 */
object CodePoints {
    /** The first code point past the Basic Multilingual Plane, the first that needs two `Char`s. */
    const val SUPPLEMENTARY_MIN: Int = 0x10000

    /** The code point at [index]: the surrogate pair starting there decoded, or the `Char` itself. */
    fun at(text: CharSequence, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) {
                return SUPPLEMENTARY_MIN + ((high.code - HIGH_SURROGATE_MIN) shl SURROGATE_BITS) + (low.code - LOW_SURROGATE_MIN)
            }
        }
        return high.code
    }

    /** How many `Char`s [codePoint] takes: 2 past U+FFFF, else 1. */
    fun charCount(codePoint: Int): Int = if (codePoint >= SUPPLEMENTARY_MIN) 2 else 1

    /** Calls [action] with each code point of [text] and the `Char` index it starts at. */
    inline fun forEach(text: CharSequence, action: (index: Int, codePoint: Int) -> Unit) {
        var index = 0
        while (index < text.length) {
            val codePoint = at(text, index)
            action(index, codePoint)
            index += charCount(codePoint)
        }
    }

    /** [codePoint] as a string of one `Char`, or of its surrogate pair past U+FFFF. */
    fun asString(codePoint: Int): String {
        require(codePoint in 0..MAX) { "U+${codePoint.toString(HEX)} is not a code point" }
        if (codePoint < SUPPLEMENTARY_MIN) return codePoint.toChar().toString()
        val offset = codePoint - SUPPLEMENTARY_MIN
        return charArrayOf(
            (HIGH_SURROGATE_MIN + (offset shr SURROGATE_BITS)).toChar(),
            (LOW_SURROGATE_MIN + (offset and SURROGATE_MASK)).toChar(),
        ).concatToString()
    }

    private const val MAX = 0x10FFFF
    private const val HEX = 16
    private const val HIGH_SURROGATE_MIN = 0xD800
    private const val LOW_SURROGATE_MIN = 0xDC00
    private const val SURROGATE_BITS = 10
    private const val SURROGATE_MASK = 0x3FF
}
