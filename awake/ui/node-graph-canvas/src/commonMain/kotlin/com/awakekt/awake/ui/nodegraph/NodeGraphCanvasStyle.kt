/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.Sp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color

/**
 * Sizes and colours of the canvas. Sizes are at zoom 1; the canvas scales all of them with zoom.
 *
 * Neutral on purpose: a design system restyles the canvas by passing its own values, and nothing
 * here depends on one.
 */
class NodeGraphCanvasStyle(
    val nodeWidth: Dp = 180.dp,
    val headerHeight: Dp = 28.dp,
    val portRowHeight: Dp = 22.dp,
    val portRadius: Dp = 5.dp,
    val portHitRadius: Dp = 9.dp,
    val cornerRadius: Dp = 6.dp,
    val borderWidth: Dp = 1.dp,
    val wireWidth: Dp = 2.dp,
    val titleSize: Sp = 12.sp,
    val labelSize: Sp = 11.sp,
    /**
     * Below this zoom, node titles are not drawn: they would be a few pixels tall. Layout does not
     * change, so nothing moves when they appear.
     */
    val titleMinZoom: Float = 0.5f,
    /** The same, for port labels. */
    val labelMinZoom: Float = 0.6f,
    val background: Color = Color(0.11f, 0.11f, 0.13f),
    val nodeColor: Color = Color(0.17f, 0.17f, 0.2f),
    val headerColor: Color = Color(0.24f, 0.24f, 0.29f),
    val borderColor: Color = Color(0.32f, 0.32f, 0.38f),
    val selectedBorderColor: Color = Color(0.36f, 0.6f, 1f),
    val highlightColor: Color = Color(1f, 0.74f, 0.26f),
    val titleColor: Color = Color(0.92f, 0.92f, 0.94f),
    val labelColor: Color = Color(0.7f, 0.7f, 0.76f),
    val rejectedWireColor: Color = Color(0.95f, 0.36f, 0.36f),
    val selectionBoxColor: Color = Color(0.36f, 0.6f, 1f, 0.14f),
    val selectionBoxBorderColor: Color = Color(0.36f, 0.6f, 1f, 0.8f),
    /**
     * Colour of a port, and of wires leaving it, by port type. This is how a graph kind tells, say,
     * execution wires from data wires apart.
     */
    val portColor: (type: String) -> Color = { DefaultPortColor },
) {
    companion object {
        val DefaultPortColor: Color = Color(0.62f, 0.64f, 0.72f)
        val Default: NodeGraphCanvasStyle = NodeGraphCanvasStyle()
    }
}
