/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasButtonTrigger
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasStateStyle
import com.awakekt.awake.ui.canvas.CanvasStyle
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A `canvas_element`'s action `button` settings, `enabled`, and the fields a `bind` reads for them. */
class CanvasActionButtonSceneTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aButtonSavedBeforeItHadSettingsStillReads() {
        val element = json.decodeFromString(SceneCanvasElement.serializer(), """{ "kind": "Button", "action": "jump" }""")

        assertNull(element.button)
        assertTrue(element.enabled)
        assertEquals(emptyList(), element.validate("nodes[0]"))
    }

    @Test
    fun everyButtonSettingRoundTrips() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            action = "dash",
            enabled = false,
            button = CanvasButton(
                trigger = CanvasButtonTrigger.Down,
                longPressSeconds = 0.8f,
                longPressAction = "inspect",
                releaseAction = "stop",
                blockWhileCooling = false,
            ),
            bind = CanvasBinding("Player", cooldown = "dash.left", cooldownMax = "dash.seconds", charges = "dash.charges", enabled = "dash.ready"),
            style = CanvasStyle(disabled = CanvasStateStyle(background = "#303030", alpha = 0.5f)),
        )

        assertEquals(saved, saved.toComponent().toSceneComponent())
        assertEquals(saved, json.decodeFromString(SceneCanvasElement.serializer(), Json.encodeToString(SceneCanvasElement.serializer(), saved)))
    }

    @Test
    fun validationNamesWhatIsWrongWithTheSettingsAndWhereTheyDoNothing() {
        val issues = SceneCanvasElement(
            kind = CanvasElementKind.Panel,
            button = CanvasButton(longPressSeconds = 0f),
            bind = CanvasBinding("Player", charges = "rounds"),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.bind.charges \"rounds\" must be component.field",
                "canvas_element.button only sets up a Button",
                "canvas_element.button.longPressSeconds must be above 0",
            ),
            issues,
        )
    }
}
