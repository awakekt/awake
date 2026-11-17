/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * A game-scale HUD's per-frame writes for the revision allocation probes (#1512): [ELEMENTS] bars,
 * buttons and labels, each frame moved, filled, relabelled and given charges, half of them with the
 * value they already hold, then every [CanvasElementBase.contentRevision] read, as a skipping host
 * polls it. `enabled` is only rewritten as it is: a button made usable again marks the time for its
 * ready pulse, an allocation of that feature, not of counting.
 */
internal class CanvasRevisionWorkload {
    private val labels = arrayOf("Ready", "Cooling")
    private val kinds = arrayOf(CanvasElementKind.Bar, CanvasElementKind.Button, CanvasElementKind.Text)

    val elements: Array<CanvasElement> = Array(ELEMENTS) { index ->
        CanvasElement().apply {
            kind = kinds[index % kinds.size]
            text = labels[0]
        }
    }

    /** Sums the revisions read so the reads cannot be dropped. */
    var seen = 0
        private set

    /** One frame of writes and reads; [frame] picks the values, so frames alternate between change and no change. */
    fun frame(frame: Int) {
        val moving = frame % 2 == 0
        for (index in elements.indices) {
            val element = elements[index]
            val still = index % 2 == 0
            element.offsetX = if (still || !moving) BASE_X else BASE_X + index
            element.offsetY = BASE_Y
            element.visible = true
            element.order = index
            element.value = if (still) FULL else (frame % FILL_STEPS) / FILL_STEPS.toFloat()
            element.text = labels[if (still) 0 else frame % labels.size]
            element.enabled = true
            element.charges = if (still) 0 else frame % MAX_CHARGES
            seen += element.contentRevision
        }
    }

    companion object {
        const val ELEMENTS = 1_000
        private const val BASE_X = 12f
        private const val BASE_Y = 40f
        private const val FULL = 1f
        private const val FILL_STEPS = 8
        private const val MAX_CHARGES = 3
    }
}
