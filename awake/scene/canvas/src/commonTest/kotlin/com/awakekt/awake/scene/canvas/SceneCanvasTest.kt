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
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneCanvasTest {

    private val world = World()

    private fun element(configure: CanvasElement.() -> Unit): Entity =
        world.create().also { world.add(it, CanvasElement().apply(configure)) }

    private fun frame(
        host: ComposeHost = ComposeHost(),
        input: FrameInput = FrameInput(800, 600),
        touch: Boolean = false,
    ): FrameOutput = host.frame(input) { SceneCanvas(world, showTouchControls = touch) }

    private fun FrameOutput.node(entity: Entity): SemanticsNode? = semantics.find("canvas-element-${entity.id}")

    @Test
    fun anchorsPlaceElementsInwardFromEachScreenEdge() {
        val topLeft = element { anchor = CanvasAnchor.TopLeft; offsetX = 16f; offsetY = 8f; width = 200f; height = 40f }
        val bottomRight = element { anchor = CanvasAnchor.BottomRight; offsetX = 16f; offsetY = 8f; width = 200f; height = 40f }
        val center = element { anchor = CanvasAnchor.Center; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f }

        val out = frame()

        assertEquals(16 to 8, assertNotNull(out.node(topLeft)).let { it.x to it.y })
        assertEquals(800 - 16 - 200 to 600 - 8 - 40, assertNotNull(out.node(bottomRight)).let { it.x to it.y })
        assertEquals(350 to 275, assertNotNull(out.node(center)).let { it.x to it.y })
    }

    @Test
    fun hiddenElementsAreNotDrawn() {
        val hidden = element { kind = CanvasElementKind.Panel; visible = false }

        assertNull(frame().node(hidden))
    }

    @Test
    fun tappingAButtonIsReportedOnce() {
        lateinit var button: CanvasElement
        element { kind = CanvasElementKind.Button; text = "Jump"; offsetX = 0f; offsetY = 0f; width = 100f; height = 40f; button = this }
        val host = ComposeHost()
        frame(host)
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerDown = true, pointerPressed = true))
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerReleased = true))

        assertTrue(button.consumePress())
        assertFalse(button.consumePress())
    }

    @Test
    fun aButtonIsHeldWhileThePointerIsDown() {
        lateinit var button: CanvasElement
        element { kind = CanvasElementKind.Button; offsetX = 0f; offsetY = 0f; width = 100f; height = 40f; button = this }
        val host = ComposeHost()
        frame(host)

        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerDown = true, pointerPressed = true))
        val heldOnPress = button.isHeld
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerDown = true))
        val heldAfter = button.isHeld
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerReleased = true))

        assertTrue(heldOnPress && heldAfter, "a button must stay held for as long as it is pressed")
        assertFalse(button.isHeld, "releasing must let go")
    }

    @Test
    fun draggingAJoystickDeflectsItUpToItsEdgeAndReleasingCentresIt() {
        lateinit var stick: CanvasElement
        element {
            kind = CanvasElementKind.Joystick; anchor = CanvasAnchor.TopLeft
            offsetX = 0f; offsetY = 0f; width = 100f; height = 100f
            stick = this
        }
        val host = ComposeHost()
        frame(host)

        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 50, pointerDown = true, pointerPressed = true))
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 25, pointerDown = true))
        val halfUp = stick.stickY
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = -200, pointerDown = true))
        val pinned = stick.stickY
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = -200, pointerReleased = true))

        assertEquals(-0.5f, halfUp, 0.01f, "25 of a 50 radius upward is half deflection")
        assertEquals(-1f, pinned, 0.01f, "the knob stops at the pad's edge")
        assertEquals(0f, stick.stickY, "releasing centres the stick")
    }

    @Test
    fun touchOnlyElementsAppearOnlyWhereTouchControlsAreShown() {
        val pad = element { kind = CanvasElementKind.Joystick; touchOnly = true }

        assertNull(frame().node(pad))
        assertNotNull(frame(touch = true).node(pad))
    }

    @Test
    fun savedFormRoundTripsEveryField() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Bar,
            anchor = CanvasAnchor.BottomCenter,
            offsetX = 4f,
            offsetY = 24f,
            width = 300f,
            height = 12f,
            text = "HP",
            fontSize = 14f,
            color = "#E5484D",
            background = "#00000080",
            action = "jump",
            touchOnly = true,
            value = 0.4f,
            order = 3,
            visible = false,
        )

        assertEquals(saved, saved.toComponent().toSceneComponent())
    }

    @Test
    fun validationRejectsBadValuesAndColours() {
        val issues = SceneCanvasElement(value = 2f, color = "red", fontSize = 0f).validate("nodes[0]")

        assertEquals(3, issues.size)
        assertTrue(SceneCanvasElement().validate("nodes[0]").isEmpty())
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
