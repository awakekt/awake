/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.gestures

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * State receiving multi-touch transformation gestures (pan, zoom, rotation).
 */
fun interface TransformableState {
    fun transform(panX: Float, panY: Float, zoom: Float, rotationDegrees: Float)
}

/**
 * Detects multi-touch pan, pinch-to-zoom, and rotation gestures.
 */
fun Modifier.transformable(
    state: TransformableState,
    enabled: Boolean = true,
): Modifier = this then TransformableElement(state, enabled)

private class TransformableElement(
    private val state: TransformableState,
    private val enabled: Boolean,
) : ModifierNodeElement<TransformableNode>() {
    override fun create(): TransformableNode = TransformableNode(state, enabled)

    override fun update(node: TransformableNode) {
        node.state = state
        node.enabled = enabled
    }

    override fun toString(): String = "transformable(enabled=$enabled)"
}

private class TransformableNode(
    var state: TransformableState,
    var enabled: Boolean,
) : Modifier.Node(),
    PointerInputNode {

    private val pointerPositions = LinkedHashMap<Long, FloatArray>()

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (!enabled || pass != PointerEventPass.Main) return

        val id = event.pointerId
        val px = event.x.toFloat()
        val py = event.y.toFloat()

        when (event.type) {
            PointerEventType.Press -> {
                pointerPositions[id] = floatArrayOf(px, py)
                event.consume()
            }
            PointerEventType.Move -> {
                if (event.isCaptureHolder || pointerPositions.containsKey(id)) {
                    val prev = pointerPositions[id]
                    if (prev == null) {
                        pointerPositions[id] = floatArrayOf(px, py)
                        if (event.dx != 0 || event.dy != 0) {
                            state.transform(event.dx.toFloat(), event.dy.toFloat(), 1f, 0f)
                        }
                    } else {
                        if (pointerPositions.size <= 1) {
                            state.transform(event.dx.toFloat(), event.dy.toFloat(), 1f, 0f)
                        } else {
                            val keys = pointerPositions.keys.toList()
                            val p1Id = keys[0]
                            val p2Id = keys[1]
                            val p1Prev = pointerPositions[p1Id]!!
                            val p2Prev = pointerPositions[p2Id]!!
                            val p1Cur = if (id == p1Id) floatArrayOf(px, py) else p1Prev
                            val p2Cur = if (id == p2Id) floatArrayOf(px, py) else p2Prev

                            val prevDist = distance(p1Prev[0], p1Prev[1], p2Prev[0], p2Prev[1])
                            val curDist = distance(p1Cur[0], p1Cur[1], p2Cur[0], p2Cur[1])
                            val zoom = if (prevDist > 0f) curDist / prevDist else 1f

                            val prevAngle = angleDegrees(p1Prev[0], p1Prev[1], p2Prev[0], p2Prev[1])
                            val curAngle = angleDegrees(p1Cur[0], p1Cur[1], p2Cur[0], p2Cur[1])
                            val rotation = curAngle - prevAngle

                            val prevCentroidX = (p1Prev[0] + p2Prev[0]) / 2f
                            val prevCentroidY = (p1Prev[1] + p2Prev[1]) / 2f
                            val curCentroidX = (p1Cur[0] + p2Cur[0]) / 2f
                            val curCentroidY = (p1Cur[1] + p2Cur[1]) / 2f
                            val panX = curCentroidX - prevCentroidX
                            val panY = curCentroidY - prevCentroidY

                            state.transform(panX, panY, zoom, rotation)
                        }
                        prev[0] = px
                        prev[1] = py
                    }
                    event.consume()
                }
            }
            PointerEventType.Release, PointerEventType.Exit -> {
                pointerPositions.remove(id)
            }
            else -> Unit
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun angleDegrees(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val rad = atan2(y2 - y1, x2 - x1)
        return (rad * 180f / kotlin.math.PI.toFloat())
    }

    override fun toString(): String = "transformable(enabled=$enabled)"
}
