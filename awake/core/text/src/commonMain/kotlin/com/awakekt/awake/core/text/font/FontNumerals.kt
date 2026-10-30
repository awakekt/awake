/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Numeric figure spacing, matching CSS `font-variant-numeric`'s figure-spacing values (`proportional-nums`
 * and `tabular-nums`) and OpenType `GSUB`'s `pnum` and `tnum` features.
 *
 * A style that leaves `numerals` null draws the font's default (`cmap`) digit glyphs.
 */
enum class FontNumerals {
    /**
     * Digits take widths fitted to their shapes (a narrow `1` beside a wider `0`), via the font's
     * OpenType `pnum` feature.
     */
    Proportional,

    /**
     * Every digit advances by the same width so columns of numbers and counters keep their places,
     * via the font's OpenType `tnum` feature.
     */
    Tabular,
}
