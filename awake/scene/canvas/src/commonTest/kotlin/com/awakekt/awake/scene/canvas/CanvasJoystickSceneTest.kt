/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.ui.canvas.CanvasCurve
import com.awakekt.awake.ui.canvas.CanvasCurvePoint
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasJoystick
import com.awakekt.awake.ui.canvas.CanvasJoystickMode
import com.awakekt.awake.ui.canvas.CanvasStickDirections
import com.awakekt.awake.ui.canvas.CanvasStickResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A `canvas_element`'s `joystick` settings, read, saved and checked. */
class CanvasJoystickSceneTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aJoystickSavedBeforeItHadSettingsStillReads() {
        val element = json.decodeFromString(SceneCanvasElement.serializer(), """{ "kind": "Joystick", "action": "move" }""")

        assertNull(element.joystick)
        assertEquals(emptyList(), element.validate("nodes[0]"))
    }

    @Test
    fun everyJoystickSettingRoundTrips() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Joystick,
            joystick = CanvasJoystick(
                mode = CanvasJoystickMode.Dynamic,
                radius = 70f,
                restX = 0.25f,
                restY = 0.75f,
                deadZone = 0.15f,
                curve = CanvasCurve(listOf(CanvasCurvePoint(0f, 0f), CanvasCurvePoint(1f, 1f))),
                directions = CanvasStickDirections.Eight,
                snapBack = false,
                sprintThreshold = 0.85f,
                sprintAction = "run",
                idleAlpha = 0.3f,
                idleDelay = 2f,
                fadeSeconds = 0.5f,
            ),
        )

        assertEquals(saved, saved.toComponent().toSceneComponent())
        assertEquals(saved, json.decodeFromString(SceneCanvasElement.serializer(), Json.encodeToString(SceneCanvasElement.serializer(), saved)))
    }

    @Test
    fun validationNamesWhatIsWrongWithTheSettingsAndWhereTheyDoNothing() {
        val issues = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            joystick = CanvasJoystick(deadZone = 1f, response = CanvasStickResponse.Cubic, curve = CanvasCurve()),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.joystick only sets up a Joystick",
                "canvas_element.joystick.deadZone must be at least 0 and below 1",
                "canvas_element.joystick.curve and response both shape the deflection; keep one",
            ),
            issues,
        )
    }
}
