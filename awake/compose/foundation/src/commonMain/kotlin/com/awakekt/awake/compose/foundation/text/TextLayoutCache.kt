/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.layout.AlignmentLine
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * One text node's last measurement and glyph run, reused while nothing that shapes the text has
 * changed.
 *
 * `Text` builds a new [TextMeasurePolicy] every frame, so the policy cannot remember anything
 * itself. This lives on the node's retained paint modifier node instead, which outlives the frame,
 * and is handed to each new policy before it measures. A hit returns exactly what the same
 * arithmetic produced last frame. A miss recomputes and replaces it.
 */
internal class TextLayoutCache {
    private var text: String? = null
    private var style: TextStyle? = null
    private var font: UiFont? = null
    private var density = Float.NaN
    private var fontScale = Float.NaN
    private var wrapWidthPx = Float.NaN

    var width = 0
        private set
    var height = 0
        private set
    var alignmentLines: Map<AlignmentLine, Int> = emptyMap()
        private set

    private var run: TextRun? = null
    private var runDensity = Float.NaN
    private var runFontScale = Float.NaN
    private var runWrapWidthPx = Float.NaN

    /** Whether the stored measurement is what [policy] would measure now. */
    fun hasMeasurement(policy: TextMeasurePolicy, density: Float, fontScale: Float, wrapWidthPx: Float): Boolean =
        shapes(policy) && density == this.density && fontScale == this.fontScale && wrapWidthPx == this.wrapWidthPx

    fun storeMeasurement(width: Int, height: Int, alignmentLines: Map<AlignmentLine, Int>) {
        this.width = width
        this.height = height
        this.alignmentLines = alignmentLines
    }

    /** Keys the measurement just stored. A changed shape also retires the cached run. */
    fun keyMeasurement(policy: TextMeasurePolicy, density: Float, fontScale: Float, wrapWidthPx: Float) {
        if (!shapes(policy)) run = null
        text = policy.text
        style = policy.style
        font = policy.font
        this.density = density
        this.fontScale = fontScale
        this.wrapWidthPx = wrapWidthPx
    }

    /** The run [policy] paints with, rebuilt only when its inputs changed. */
    fun runFor(policy: TextMeasurePolicy, density: Float, fontScale: Float, wrapWidthPx: Float): TextRun {
        val cached = run
        val reusable = cached != null && shapes(policy) && density == runDensity &&
            fontScale == runFontScale && wrapWidthPx == runWrapWidthPx
        if (reusable) return cached
        return policy.buildRun(density, fontScale).also {
            run = it
            runDensity = density
            runFontScale = fontScale
            runWrapWidthPx = wrapWidthPx
        }
    }

    /** Same characters, style and font as the stored measurement. */
    private fun shapes(policy: TextMeasurePolicy): Boolean =
        policy.font === font && policy.style == style && policy.text == text
}
