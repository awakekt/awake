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
        "bind", "wheel", "draggable", "dropTarget", "camera", "links", "mask", "maxLength", "maxLengthUnit",
        "font", "pressable",
    ).associateWith { it }

    /** Options that cannot be written as data, with why. Each is set from code through the live element. */
    private val codeOnly: Map<String, String> = mapOf(
        "focused" to "a request on the keyboard's current owner, not a saved value",
        "followScore" to "a follower's importance right now, which game code works out each frame",
    )

    /** [CanvasElement]'s own options: its public setters, skipping a synthetic bridge's `$`-named one. */
    private fun options(): Set<String> = CanvasElement::class.java.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("set") && '$' !in it.name }
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
