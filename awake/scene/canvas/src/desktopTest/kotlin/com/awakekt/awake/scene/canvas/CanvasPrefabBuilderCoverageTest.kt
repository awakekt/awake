/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every `canvas_element` field can be set from [canvasPrefab]: a field added to [SceneCanvasElement]
 * fails here until [CanvasElementScope] has a setting of the same name, so a prefab built in code
 * never falls behind what an editor can save.
 */
@OptIn(ExperimentalSerializationApi::class)
class CanvasPrefabBuilderCoverageTest {
    /** Fields the builder sets another way, with how. */
    private val setOtherwise = mapOf(
        "kind" to "chosen by the function that adds the element, such as text() or bar()",
    )

    private fun settings(): Set<String> = CanvasElementScope::class.java.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("set") && it.parameterCount == 1 && '$' !in it.name }
        .map { it.name.removePrefix("set").replaceFirstChar(Char::lowercase).substringBefore('-') }
        .toSet()

    @Test
    fun everySceneFileFieldHasABuilderSetting() {
        val fields = SceneCanvasElement.serializer().descriptor.elementNames.toSet()

        assertEquals(emptySet(), fields - settings() - setOtherwise.keys, "canvas_element fields the prefab builder cannot set")
        assertEquals(emptySet(), setOtherwise.keys - fields, "lists fields canvas_element no longer has")
    }
}
