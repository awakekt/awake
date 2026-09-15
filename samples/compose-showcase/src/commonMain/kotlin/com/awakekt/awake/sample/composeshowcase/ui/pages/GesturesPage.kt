/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.gestures.draggable
import com.awakekt.awake.compose.foundation.gestures.transformable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

private val GestureSurface = Color.fromHex(0xF8FAFC)
private val GestureBorder = Color.fromHex(0xCBD5E1)
private val GestureBlue = Color.fromHex(0x2563EB)
private val GesturePurple = Color.fromHex(0x7C3AED)
private val GestureText = Color.fromHex(0x334155)

private class DragDemoState {
    var x: Int = 24
    var y: Int = 24
    var lastGesture: String = "Press the card and drag it"
}

private class TransformDemoState {
    var panX: Float = 0f
    var panY: Float = 0f
    var zoom: Float = 1f
    var rotation: Float = 0f
}

internal val GesturesPage = ShowcasePage(
    id = "gestures",
    title = "Gestures",
    category = ShowcaseCategory.Gestures,
    description = "Frame-driven pointer gestures with capture, deltas, and multi-touch transforms.",
    notes = listOf(
        "draggable reports movement deltas, so the card can be repositioned without storing pointer origins.",
        "Captured moves continue after the pointer leaves the card; release commits the gesture.",
        "transformable combines one-pointer pan with two-pointer pan, zoom, and rotation.",
    ),
    demo = {
        val drag = remember { DragDemoState() }
        val transform = remember { TransformDemoState() }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DemoSectionLabel("Draggable card")
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .height(190.dp)
                    .background(GestureSurface)
                    .border(1.dp, GestureBorder),
            ) {
                Box(
                    modifier = Modifier
                        .offset(drag.x.dp, drag.y.dp)
                        .width(150.dp)
                        .height(72.dp)
                        .background(GestureBlue)
                        .draggable(
                            hitMarginPx = 8,
                            onDrag = { dx, dy ->
                                drag.x = (drag.x + dx).coerceIn(0, 370)
                                drag.y = (drag.y + dy).coerceIn(0, 118)
                                drag.lastGesture = "Dragging: dx=$dx, dy=$dy"
                            },
                            onDragStopped = { drag.lastGesture = "Released at (${drag.x}, ${drag.y})" },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Drag me", style = TextStyle.Default.copy(color = Color.White))
                }
            }
            Text(drag.lastGesture, style = TextStyle.Default.copy(color = GestureText))

            DemoSectionLabel("Pan, zoom, and rotate")
            Box(
                modifier = Modifier
                    .width(520.dp)
                    .height(190.dp)
                    .background(GestureSurface)
                    .border(1.dp, GestureBorder)
                    .transformable(
                        state = com.awakekt.awake.compose.foundation.gestures.TransformableState { panX, panY, zoom, rotation ->
                            transform.panX += panX
                            transform.panY += panY
                            transform.zoom = (transform.zoom * zoom).coerceIn(0.55f, 2.2f)
                            transform.rotation += rotation
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .offset(185.dp, 58.dp)
                        .width(150.dp)
                        .height(72.dp)
                        .graphicsLayer(
                            translationX = transform.panX,
                            translationY = transform.panY,
                            scaleX = transform.zoom,
                            scaleY = transform.zoom,
                            rotationDegrees = transform.rotation,
                        )
                        .background(GesturePurple),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Transform", style = TextStyle.Default.copy(color = Color.White))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
                Text("pan=(%.0f, %.0f)".format(transform.panX, transform.panY), style = TextStyle.Default.copy(color = GestureText))
                Text("zoom=%.2f".format(transform.zoom), style = TextStyle.Default.copy(color = GestureText))
                Text("rotation=%.0f°".format(transform.rotation), style = TextStyle.Default.copy(color = GestureText))
            }
        }
    },
)
