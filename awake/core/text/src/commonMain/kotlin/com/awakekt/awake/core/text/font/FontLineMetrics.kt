/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * A font's line box, in em: what [UiFont.lineAscentEm], [UiFont.lineDescentEm] and [UiFont.lineGapEm]
 * report. See [OpenTypeFont.lineMetrics] for where a font file's come from.
 */
internal class FontLineMetrics(val ascentEm: Float, val descentEm: Float, val gapEm: Float)

/**
 * The built-in Roboto's line box. Every weight's `OS/2` table gives `usWinAscent` 1946 and
 * `usWinDescent` 512 over 2048 units per em, without USE_TYPO_METRICS, and `hhea` has no line gap.
 *
 * Written out because the subset outlines the built-in atlas keeps have no `OS/2` table. The
 * `OpenTypeUiFontTest` checks it against each Roboto file.
 */
internal val RobotoLineMetrics = FontLineMetrics(ascentEm = 1946f / 2048f, descentEm = 512f / 2048f, gapEm = 0f)
