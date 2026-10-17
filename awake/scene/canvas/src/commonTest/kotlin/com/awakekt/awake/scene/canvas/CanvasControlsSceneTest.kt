/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.input.InputDevice
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasControlsBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasControlsBinding.toSceneComponent
import com.awakekt.awake.ui.canvas.CanvasControls
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasTouchVisibility
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A scene's `canvas_controls`: saved, checked, and what its canvas shows by. */
class CanvasControlsSceneTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun itRoundTripsAndReportsItsProblems() {
        val saved = SceneCanvasControls(touchVisibility = CanvasTouchVisibility.Auto, minTouchSize = 48f)

        assertEquals(saved, saved.toComponent().toSceneComponent())
        assertEquals(saved, json.decodeFromString(SceneCanvasControls.serializer(), Json.encodeToString(SceneCanvasControls.serializer(), saved)))
        assertEquals(
            listOf("canvas_controls.minTouchSize must not be negative"),
            SceneCanvasControls(minTouchSize = -1f).validate("nodes[0]").map { it.message },
        )
    }

    @Test
    fun aSceneCanvasShowsTouchControlsByItsScenesVisibility() {
        val world = World()
        world.add(world.create(), CanvasControls(touchVisibility = CanvasTouchVisibility.Auto))
        val stick = world.create().also { world.add(it, CanvasElement().apply { kind = CanvasElementKind.Joystick; touchOnly = true }) }
        val host = ComposeHost()
        fun drawnWith(device: InputDevice?) =
            host.frame(FrameInput(800, 600)) { SceneCanvas(world, showTouchControls = false, inputDevice = device) }
                .semantics.findTag("canvas-element-${stick.id}")

        assertNull(drawnWith(null), "until anything is used, as the host says")
        assertNotNull(drawnWith(InputDevice.Touch))
        assertNull(drawnWith(InputDevice.Controller))
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
}
