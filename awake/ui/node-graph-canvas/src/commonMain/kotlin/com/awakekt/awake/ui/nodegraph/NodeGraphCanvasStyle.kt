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
 *
 * @property nodeWidth Default width of each node card.
 * @property headerHeight Height of the node header bar.
 * @property portRowHeight Height of each port row in the node.
 * @property portRadius Radius of the visible port connection circle.
 * @property portHitRadius Radius of the interactive touch/click hit target for ports.
 * @property cornerRadius Corner radius of node cards.
 * @property borderWidth Width of the border around node cards.
 * @property wireWidth Stroke width of connection wires.
 * @property titleSize Text size for node header titles.
 * @property labelSize Text size for port labels.
 * @property titleMinZoom Below this zoom, node titles are not drawn: they would be a few pixels tall.
 * @property labelMinZoom Below this zoom, port labels are not drawn.
 * @property background Background color of the canvas area.
 * @property nodeColor Base background color of node cards.
 * @property headerColor Background color of node header bars.
 * @property borderColor Border color of unselected node cards.
 * @property selectedBorderColor Border color of selected node cards.
 * @property highlightColor Color used to highlight active or executing nodes.
 * @property titleColor Text color for node titles.
 * @property labelColor Text color for port labels.
 * @property rejectedWireColor Color of connection wires when a connection is rejected.
 * @property selectionBoxColor Fill color of the rectangular selection box.
 * @property selectionBoxBorderColor Border color of the rectangular selection box.
 * @property portColor Function mapping a port type to its distinct color.
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
    val titleMinZoom: Float = 0.5f,
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
    val portColor: (type: String) -> Color = { DefaultPortColor },
) {
    /**
     * Default visual styling constants and factory instances for node graph canvases.
     */
    companion object {
        /** Default color assigned to untyped or unrecognized ports. */
        val DefaultPortColor: Color = Color(0.62f, 0.64f, 0.72f)

        /** Default style configuration for node graph canvases. */
        val Default: NodeGraphCanvasStyle = NodeGraphCanvasStyle()
    }
}
