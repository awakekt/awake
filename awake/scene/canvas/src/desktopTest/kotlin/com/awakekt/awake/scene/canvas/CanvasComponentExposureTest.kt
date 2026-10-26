/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasAppearance
import com.awakekt.awake.ui.canvas.CanvasBarWidget
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasFollow
import com.awakekt.awake.ui.canvas.CanvasInput
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasRect
import com.awakekt.awake.ui.canvas.CanvasStepperWidget
import com.awakekt.awake.ui.canvas.CanvasSweepWidget
import com.awakekt.awake.ui.canvas.CanvasText
import com.awakekt.awake.ui.canvas.CanvasTextFieldWidget
import com.awakekt.awake.ui.canvas.CanvasTooltip
import com.awakekt.awake.ui.canvas.CanvasViewWidget
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every option a canvas component exposes is a field of its saved form, of the same name, or listed
 * here as set from code only, with why: an option added to a component and left out of the scene
 * file fails here until that is a decision. What the player does, in `CanvasPointerState`, has no
 * saved form at all. `CanvasLayout`, `CanvasCluster` and `CanvasBinding` are values saved whole.
 */
@OptIn(ExperimentalSerializationApi::class)
class CanvasComponentExposureTest {
    private class Exposure(val component: KClass<*>, val saved: KSerializer<*>, val codeOnly: Map<String, String> = emptyMap())

    private val exposures = listOf(
        Exposure(CanvasRect::class, SceneCanvasRect.serializer()),
        Exposure(CanvasText::class, SceneCanvasText.serializer()),
        Exposure(CanvasAppearance::class, SceneCanvasAppearance.serializer(), mapOf("previewState" to "an editor's preview of one state, never saved with the scene")),
        Exposure(CanvasFollow::class, SceneCanvasFollow.serializer(), mapOf("score" to "a follower's importance right now, which game code works out each frame")),
        Exposure(CanvasInput::class, SceneCanvasInput.serializer()),
        Exposure(CanvasTooltip::class, SceneCanvasTooltip.serializer()),
        Exposure(
            CanvasButtonWidget::class,
            SceneCanvasButton.serializer(),
            mapOf(
                "cooldown" to "how much of a cooldown is left right now, set by game code or read through a binding's cooldown",
                "charges" to "how many uses are left right now, set by game code or read through a binding's charges",
                "aimLock" to "a hook into the game's own targets, which only code can answer",
            ),
        ),
        Exposure(CanvasBarWidget::class, SceneCanvasBar.serializer()),
        Exposure(CanvasSweepWidget::class, SceneCanvasSweep.serializer()),
        Exposure(CanvasStepperWidget::class, SceneCanvasStepper.serializer(), mapOf("number" to "the number right now, saved as where its settings start it")),
        Exposure(CanvasJoystickWidget::class, SceneCanvasJoystick.serializer()),
        Exposure(CanvasLookAreaWidget::class, SceneCanvasLookArea.serializer()),
        Exposure(CanvasTextFieldWidget::class, SceneCanvasTextField.serializer()),
        Exposure(CanvasViewWidget::class, SceneCanvasView.serializer()),
    )

    /** The class's public property setters, skipping a synthetic bridge's `$`-named one. */
    private fun options(type: KClass<*>): Set<String> = type.java.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("set") && it.parameterCount == 1 && '$' !in it.name }
        .map { it.name.removePrefix("set").replaceFirstChar(Char::lowercase) }
        .toSet()

    @Test
    fun everyOptionIsSavedOrListedAsCodeOnly() {
        for (exposure in exposures) {
            val saved = exposure.saved.descriptor.elementNames.toSet()
            val name = exposure.component.simpleName

            assertEquals(emptySet(), options(exposure.component) - saved - exposure.codeOnly.keys, "$name options with no saved field")
            assertEquals(emptySet(), saved - options(exposure.component), "${exposure.saved.descriptor.serialName} fields that set no $name option")
            assertEquals(emptySet(), exposure.codeOnly.keys - options(exposure.component), "$name has none of these code-only options")
        }
    }

    @Test
    fun theCodeOnlyListsSayWhy() {
        for (exposure in exposures) assertEquals(true, exposure.codeOnly.values.all { it.isNotBlank() })
    }
}
