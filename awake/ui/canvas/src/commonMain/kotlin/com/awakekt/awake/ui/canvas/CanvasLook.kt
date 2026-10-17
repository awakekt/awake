/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How a LookArea turns a swipe into a look and a pinch into a zoom. A LookArea with none behaves as
 * [Default] does.
 *
 * One thumb dragged across the area looks: [CanvasElement.consumeLook] reports how far, in dp times
 * [sensitivity], right and down being positive unless inverted. Two thumbs pinch:
 * [CanvasElement.consumeZoom] reports the factor they spread by, above 1 to zoom in.
 *
 * @property sensitivity How much a dp of swipe looks.
 * @property invertX Whether a swipe right looks left.
 * @property invertY Whether a swipe down looks up.
 * @property pinchZoom Whether two thumbs pinch to zoom; false ignores a second thumb.
 * @property zoomSensitivity How strongly a pinch zooms: the factor is raised to this power, so 2
 * zooms twice as far and 0.5 half.
 * @property turnsCamera Whether, in a played project, the look turns the scene's active camera rig
 * and the pinch zooms it, as a mouse drag and wheel do.
 * @property zoomAction An input axis action the pinch is pushed to, as `ln` of its factor along x,
 * so spreading is positive; empty pushes none.
 */
@Serializable
data class CanvasLook(
    @PropertyRange(min = 0.0) val sensitivity: Float = 1f,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val pinchZoom: Boolean = true,
    @PropertyRange(min = 0.0) val zoomSensitivity: Float = 1f,
    val turnsCamera: Boolean = true,
    val zoomAction: String = "",
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (sensitivity < 0f) add("sensitivity must not be negative")
        if (zoomSensitivity < 0f) add("zoomSensitivity must not be negative")
    }

    /** Defaults. */
    companion object {
        /** A LookArea's settings when it has none. */
        val Default = CanvasLook()
    }
}

/** A LookArea's live state: the thumbs on it, where each is in dp, and the look and zoom not yet read. */
internal class CanvasLookState {
    private val ids = LongArray(2)
    private val xs = FloatArray(2)
    private val ys = FloatArray(2)
    var count = 0
        private set

    private var lookX = 0f
    private var lookY = 0f
    private var zoom = 1f

    fun press(id: Long, x: Float, y: Float, settings: CanvasLook): Boolean {
        val limit = if (settings.pinchZoom) 2 else 1
        if (count >= limit) return false
        ids[count] = id
        xs[count] = x
        ys[count] = y
        count++
        return true
    }

    fun holds(id: Long): Boolean = indexOf(id) >= 0

    fun move(id: Long, x: Float, y: Float, settings: CanvasLook) {
        val index = indexOf(id)
        if (index < 0) return
        if (count == 1) {
            lookX += (x - xs[index]) * settings.sensitivity * if (settings.invertX) -1f else 1f
            lookY += (y - ys[index]) * settings.sensitivity * if (settings.invertY) -1f else 1f
            xs[index] = x
            ys[index] = y
            return
        }
        val before = spread()
        xs[index] = x
        ys[index] = y
        val after = spread()
        if (before > 0f && after > 0f) zoom *= (after / before).pow(settings.zoomSensitivity)
    }

    fun release(id: Long) {
        val index = indexOf(id)
        if (index < 0) return
        if (index == 0 && count == 2) {
            ids[0] = ids[1]
            xs[0] = xs[1]
            ys[0] = ys[1]
        }
        count--
    }

    fun consumeLook(): Vec2 = if (lookX == 0f && lookY == 0f) NO_LOOK else Vec2(lookX, lookY).also { lookX = 0f; lookY = 0f }

    fun consumeZoom(): Float = zoom.also { zoom = 1f }

    private fun indexOf(id: Long): Int = (0 until count).firstOrNull { ids[it] == id } ?: -1

    private fun spread(): Float {
        val dx = xs[1] - xs[0]
        val dy = ys[1] - ys[0]
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        val NO_LOOK = Vec2(0f, 0f)
    }
}

/** Feeds [element]'s look from up to two thumbs that land on it, in dp at [density]; any more pass through. */
internal fun Modifier.lookInput(element: CanvasElement, density: Float): Modifier = this then LookInputElement(element, density)

private class LookInputElement(private val element: CanvasElement, private val density: Float) : ModifierNodeElement<LookInputNode>() {
    override fun create(): LookInputNode = LookInputNode(element, density)

    override fun update(node: LookInputNode) {
        node.element = element
        node.density = density
    }

    override fun toString(): String = "lookInput()"
}

private class LookInputNode(var element: CanvasElement, var density: Float) :
    Modifier.Node(),
    PointerInputNode {
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        val state = element.lookState
        val settings = element.look ?: CanvasLook.Default
        val x = event.x / density
        val y = event.y / density
        when (event.type) {
            PointerEventType.Press -> if (event.isInBounds && state.press(event.pointerId, x, y, settings)) event.consume()
            PointerEventType.Move -> if (state.holds(event.pointerId)) {
                state.move(event.pointerId, x, y, settings)
                event.consume()
            }
            PointerEventType.Release -> if (state.holds(event.pointerId)) {
                state.release(event.pointerId)
                event.consume()
            }
            else -> Unit
        }
    }
}

/** A LookArea: its style, with up to two thumbs feeding its look and zoom, and its children over it. */
context(_: Composer)
internal fun LookAreaView(element: CanvasElement, styled: Modifier, tree: CanvasTree, children: List<CanvasItem>) {
    Box(styled.lookInput(element, LocalDensity.current)) { Children(element, children, tree) }
}
