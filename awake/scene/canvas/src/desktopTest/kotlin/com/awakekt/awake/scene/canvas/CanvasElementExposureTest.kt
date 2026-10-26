/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every option a [CanvasElement] exposes is either something a scene file can set or something
 * that can only be set from code, and this says which.
 *
 * A [CanvasElement] option used to be added to the capability and left out of the scene file until
 * someone noticed, because nothing connected the two. This is the connection: a new public setter on
 * [CanvasElement], or a new field on [SceneCanvasElement], fails here until it is listed, so leaving
 * one out is a decision written down rather than a slip. `CanvasElementBinding.kt` is where a listed
 * option is actually carried across.
 */
@OptIn(ExperimentalSerializationApi::class)
class CanvasElementExposureTest {
    /** Every [CanvasElement] option a scene file sets, named the same on both sides. */
    private val setFromSceneFile: Map<String, String> = listOf(
        "kind", "anchor", "offsetX", "offsetY", "width", "height", "text", "textKey", "textArgs", "fontSize", "color",
        "background", "value", "order", "visible", "action", "doubleAction", "touchOnly", "safeArea", "style",
        "textAlign", "follow", "followOffset", "followBounds", "followOutset", "followRules", "followArrow", "layout", "grow",
        "bind", "wheel", "draggable", "dropTarget", "camera", "links", "mask", "maxLength", "maxLengthUnit", "placeholder", "padding", "tooltip",
        "font", "pressable", "joystick", "button", "enabled", "look", "cluster", "skin", "controlId",
        "bar", "sweep", "stepper", "elementId",
    ).associateWith { it }

    /** Options that cannot be written as data, with why. Each is set from code through the live element. */
    private val codeOnly: Map<String, String> = mapOf(
        "focused" to "a request on the keyboard's current owner, not a saved value",
        "followScore" to "a follower's importance right now, which game code works out each frame",
        "cooldown" to "how much of a cooldown is left right now, set by game code or read through bind.cooldown",
        "charges" to "how many uses are left right now, set by game code or read through bind.charges",
        "aimLock" to "a hook into the game's own targets, which only code can answer",
        "previewState" to "an editor's preview of one state, never saved with the scene",
        "number" to "a Stepper's number right now, saved as its stepper.value, set by the player, game code or bind.value",
    )

    /**
     * [CanvasElement]'s own options: its public property setters, its own and those of the bases its
     * flat properties are split across, skipping a synthetic bridge's `$`-named one and [CanvasElement.set],
     * which sets a whole component.
     */
    private fun options(): Set<String> = generateSequence<Class<*>>(CanvasElement::class.java) { it.superclass }
        .takeWhile { it != Any::class.java }
        .flatMap { it.declaredMethods.asSequence() }
        .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("set") && it.name != "set" && '$' !in it.name }
        .map { it.name.removePrefix("set").replaceFirstChar(Char::lowercase) }
        .toSet()

    private val sceneFileFields: Set<String> = SceneCanvasElement.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyOptionIsEitherSetFromASceneFileOrListedAsCodeOnly() {
        val accountedFor = setFromSceneFile.keys + codeOnly.keys
        val actual = options()

        assertEquals(emptySet(), actual - accountedFor, "CanvasElement has options no scene file field is mapped to and that are not listed as code-only")
        assertEquals(emptySet(), accountedFor - actual, "lists options CanvasElement no longer has")
    }

    @Test
    fun everyMappedOptionNamesARealSceneFileField() {
        val targets = setFromSceneFile.values.toSet()

        assertEquals(emptySet(), targets - sceneFileFields, "mapped to scene file fields that do not exist")
    }

    @Test
    fun everySceneFileFieldSetsSomething() {
        val used = setFromSceneFile.values.toSet()

        assertEquals(emptySet(), sceneFileFields - used, "scene file fields that set no CanvasElement option")
    }

    @Test
    fun theCodeOnlyListSaysWhyForEachEntry() {
        assertTrue(codeOnly.values.all { it.isNotBlank() })
    }
}
