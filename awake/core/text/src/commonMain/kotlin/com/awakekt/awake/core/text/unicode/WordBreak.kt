/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

/**
 * How soft line breaking treats runs of letters without spaces, matching CSS `word-break`.
 */
enum class WordBreak {
    /**
     * Default browser rules (`word-break: normal`): Latin and numeric words break at spaces and
     * hyphens, while CJK ideographs, kana and Hangul syllables may also break between characters
     * subject to kinsoku punctuation rules.
     */
    Normal,

    /**
     * Keeps contiguous letter runs whole (`word-break: keep-all`), so Korean words (as well as
     * unspaced CJK runs) break only at spaces or allowed punctuation boundaries unless a line has
     * no break opportunity at all and falls back to grapheme-cluster wrapping.
     */
    KeepAll,
}
