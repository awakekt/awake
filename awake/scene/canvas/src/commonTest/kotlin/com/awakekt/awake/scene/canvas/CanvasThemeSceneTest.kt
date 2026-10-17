/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.canvas.CanvasControlsBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasControlsBinding.toSceneComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.ui.canvas.CanvasButtonSkin
import com.awakekt.awake.ui.canvas.CanvasControlAdjust
import com.awakekt.awake.ui.canvas.CanvasControlSkin
import com.awakekt.awake.ui.canvas.CanvasControlState
import com.awakekt.awake.ui.canvas.CanvasControlTheme
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFrames
import com.awakekt.awake.ui.canvas.CanvasImage
import com.awakekt.awake.ui.canvas.CanvasJoystickSkin
import com.awakekt.awake.ui.canvas.CanvasPlayerLayout
import com.awakekt.awake.ui.canvas.CanvasSlot
import com.awakekt.awake.ui.canvas.CanvasSlotOverride
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasThemePreset
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** A scene's control theme, an element's own skin, and a player layout: read, saved and checked. */
class CanvasThemeSceneTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val knob = CanvasSlot(
        style = CanvasStyle(background = "@accent", cornerRadius = 8f),
        size = 48f,
        circle = true,
        frames = CanvasFrames(listOf(CanvasImage("ui/knob-1.png"), CanvasImage("ui/knob-2.png")), fps = 8f, loop = false),
        custom = "",
        states = mapOf(CanvasControlState.Pressed to CanvasSlotOverride(style = CanvasStyle(background = "#FFFFFF"), scale = 1.2f, alpha = 0.9f)),
        transitionSeconds = 0.2f,
    )

    @Test
    fun aThemeAndAPlayerLayoutRoundTrip() {
        val saved = SceneCanvasControls(
            theme = CanvasControlTheme(
                preset = CanvasThemePreset.Glass,
                colors = mapOf("accent" to "#FF8800"),
                images = mapOf("pad" to CanvasImage("ui/pad.png")),
                skin = CanvasControlSkin(joystick = CanvasJoystickSkin(knob = knob)),
            ),
            playerLayout = CanvasPlayerLayout(scale = 1.25f, opacity = 0.8f, controls = mapOf("move" to CanvasControlAdjust(offsetX = 12f, offsetY = -6f, scale = 1.1f))),
        )

        assertEquals(saved, saved.toComponent().toSceneComponent())
        assertEquals(saved, json.decodeFromString(SceneCanvasControls.serializer(), Json.encodeToString(SceneCanvasControls.serializer(), saved)))
        assertEquals(emptyList(), saved.validate("nodes[0]"))
    }

    @Test
    fun anElementsOwnSkinRoundTripsAndMayNameTokens() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            controlId = "skill-1",
            skin = CanvasControlSkin(button = CanvasButtonSkin(background = knob, label = CanvasSlot(style = CanvasStyle(textColor = "@label")))),
        )

        assertEquals(saved, saved.toComponent().toSceneComponent())
        assertEquals(saved, json.decodeFromString(SceneCanvasElement.serializer(), Json.encodeToString(SceneCanvasElement.serializer(), saved)))
        assertEquals(emptyList(), saved.validate("nodes[0]"), "a token is no colour problem in a skin")
    }

    @Test
    fun validationNamesWhatIsWrongWithAThemeAndAPlayerLayout() {
        val issues = SceneCanvasControls(
            theme = CanvasControlTheme(colors = mapOf("accent" to "orange"), skin = CanvasControlSkin(joystick = CanvasJoystickSkin(knob = CanvasSlot(size = -1f)))),
            playerLayout = CanvasPlayerLayout(opacity = 2f),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_controls.theme.colors.accent \"orange\" must be #RRGGBB or #RRGGBBAA",
                "canvas_controls.theme.skin.joystick.knob.size must not be negative",
                "canvas_controls.playerLayout.opacity must be between 0 and 1",
            ),
            issues,
        )
    }
}
