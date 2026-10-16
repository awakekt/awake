/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

/**
 * What a text length counts, for a limit such as a text field's maximum length.
 *
 * [Graphemes] is what a person counts as characters, and the default. A server that stores
 * text in a fixed number of code points or UTF-8 bytes needs its own unit, or a line the field
 * accepted is refused or cut when it arrives. Whatever the unit, text is only ever cut between
 * clusters, never inside one: [fit] keeps a whole cluster or leaves it out.
 */
enum class TextLengthUnit {
    /** User-perceived characters: a ZWJ family emoji, a flag or `e` with a combining accent is one. */
    Graphemes,

    /** Unicode scalar values. A surrogate pair is one; a lone surrogate is one too. */
    CodePoints,

    /** Bytes of the text's UTF-8 encoding, where a lone surrogate takes the 3 bytes of U+FFFD. */
    Utf8Bytes,
    ;

    /** The length of [text] in this unit. */
    fun count(text: CharSequence): Int = lengthIn(this, text, 0, text.length)

    /**
     * The end of the longest prefix of [text] no longer than [max] in this unit that ends between
     * clusters, as a `Char` index: `text.length` when all of it fits, `0` when not even its first
     * cluster does.
     */
    fun fit(text: CharSequence, max: Int): Int {
        var used = 0
        var start = 0
        while (start < text.length) {
            val end = GraphemeClusters.nextBoundary(text, start)
            used += lengthIn(this, text, start, end)
            if (used > max) return start
            start = end
        }
        return text.length
    }
}

/** The length of `text[start until end]` in [unit], outside the enum, whose entries would shadow the objects. */
private fun lengthIn(unit: TextLengthUnit, text: CharSequence, start: Int, end: Int): Int = when (unit) {
    TextLengthUnit.Graphemes -> {
        var clusters = 0
        var index = start
        while (index < end) {
            index = GraphemeClusters.nextBoundary(text, index)
            clusters++
        }
        clusters
    }
    TextLengthUnit.CodePoints -> {
        var codePoints = 0
        var index = start
        while (index < end) {
            index += CodePoints.charCount(CodePoints.at(text, index))
            codePoints++
        }
        codePoints
    }
    TextLengthUnit.Utf8Bytes -> {
        var bytes = 0
        var index = start
        while (index < end) {
            val codePoint = CodePoints.at(text, index)
            bytes += utf8Length(codePoint)
            index += CodePoints.charCount(codePoint)
        }
        bytes
    }
}

private fun utf8Length(codePoint: Int): Int = when {
    codePoint < 0x80 -> 1
    codePoint < 0x800 -> 2
    codePoint < CodePoints.SUPPLEMENTARY_MIN -> 3
    else -> 4
}
