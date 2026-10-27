/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Raster sample count and fragment-alpha coverage for a colour/depth pass.
 *
 * [count] is a request until [supportedBy] negotiates it with the target's colour and depth
 * formats. UI, image passes and independent depth passes keep their own single-sample state.
 *
 * @property count Samples per pixel: 1 disables multisampling, otherwise 2, 4 or 8.
 * @property alphaToCoverageEnabled Whether fragment alpha contributes to the sample coverage mask.
 */
data class MultisampleState(
    val count: Int = 1,
    val alphaToCoverageEnabled: Boolean = false,
) {
    init {
        require(count in VALID_COUNTS) { "Sample count must be 1, 2, 4 or 8; got $count." }
        require(count > 1 || !alphaToCoverageEnabled) { "Alpha to coverage requires multisampling." }
    }

    /**
     * Chooses the greatest supported count at or below the request, reporting a downgrade once.
     * A missing multisample capability always has the single-sample fallback. Coverage is disabled
     * when the negotiated count is 1.
     */
    fun supportedBy(supportedCounts: Set<Int>, report: (String) -> Unit = ::println): MultisampleState {
        val selected = VALID_COUNTS.last { it <= count && (it == 1 || it in supportedCounts) }
        if (selected != count) report("Raster sample count requested=$count selected=$selected (supported=${supportedCounts.sorted()}).")
        return copy(count = selected, alphaToCoverageEnabled = alphaToCoverageEnabled && selected > 1)
    }

    /** Valid raster counts and the default pass state. */
    companion object {
        private val VALID_COUNTS = listOf(1, 2, 4, 8)

        /** One sample with no alpha coverage, preserving the default rasterization path. */
        val Single = MultisampleState()
    }
}
