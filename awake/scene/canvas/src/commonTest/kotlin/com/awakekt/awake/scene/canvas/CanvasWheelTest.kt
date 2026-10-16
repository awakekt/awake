/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An element opts into the wheel with `wheel = true`: the frame then keeps it from gameplay
 * ([com.awakekt.awake.compose.ui.platform.InputOwnership.isScrollConsumed]) and the element
 * collects it through `consumeWheel()`. An element that leaves `wheel` false sees nothing change.
 */
class CanvasWheelTest {

    private val world = World()
    private val host = ComposeHost()

    private fun panel(parent: Entity? = null, configure: CanvasElement.() -> Unit): Entity = world.create().also {
        if (parent != null) world.add(it, Transform(parent = parent))
        world.add(it, CanvasElement().apply { kind = CanvasElementKind.Panel }.apply(configure))
    }

    private fun CanvasElement.at(x: Float, y: Float, w: Float = 100f, h: Float = 100f) {
        anchor = CanvasAnchor.TopLeft
        offsetX = x
        offsetY = y
        width = w
        height = h
    }

    private fun frame(x: Int = 0, y: Int = 0, scroll: Float = 0f): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, scrollDeltaY = scroll)) { SceneCanvas(world) }

    /** Places the tree, then scrolls at the middle of [entity]'s placed box. */
    private fun wheelAt(entity: Entity, delta: Float): FrameOutput {
        val box = assertNotNull(frame().semantics.find("canvas-element-${entity.id}"), "$entity is not on screen")
        return frame(box.x + box.width / 2, box.y + box.height / 2, scroll = delta)
    }

    @Test
    fun aWheelOverAWheelElementAccumulatesIntoConsumeWheelThenZero() {
        val target = panel { at(10f, 10f); wheel = true }

        val output = wheelAt(target, -3f)

        assertTrue(output.ownership.isScrollConsumed, "the element must take the frame's wheel")
        val element = world.get<CanvasElement>(target)!!
        assertEquals(-3f, element.consumeWheel())
        assertEquals(0f, element.consumeWheel(), "consumeWheel reads once, then zero")
    }

    @Test
    fun aWheelOverAnElementWithoutWheelConsumesNothing() {
        val target = panel { at(10f, 10f) }

        val output = wheelAt(target, -3f)

        assertFalse(output.ownership.isScrollConsumed)
        assertEquals(0f, world.get<CanvasElement>(target)!!.consumeWheel())
    }

    @Test
    fun theInnermostOfTwoNestedWheelElementsTakesIt() {
        val outer = panel { at(0f, 0f, 400f, 400f); wheel = true }
        val inner = panel(outer) { at(10f, 10f, 100f, 100f); wheel = true }

        val output = wheelAt(inner, -5f)

        assertTrue(output.ownership.isScrollConsumed)
        assertEquals(-5f, world.get<CanvasElement>(inner)!!.consumeWheel(), "the inner element took it")
        assertEquals(0f, world.get<CanvasElement>(outer)!!.consumeWheel(), "the outer element did not also take it")
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
