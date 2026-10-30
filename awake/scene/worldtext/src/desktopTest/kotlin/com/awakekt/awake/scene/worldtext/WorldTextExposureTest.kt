/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every option of world text is either something a `world_text` can set or something only code can,
 * and this says which: a new option in [WorldTextStyle] or [WorldText], or a new scene field, fails here
 * until it is listed. `WorldTextMapping.kt` carries a listed option across; `WorldTextBindingTest` checks it.
 */
@OptIn(ExperimentalSerializationApi::class)
class WorldTextExposureTest {
    private val setFromSceneFile: Map<KClass<*>, Map<String, String>> = mapOf(
        WorldTextStyle::class to mapOf(
            "size" to "size",
            "weight" to "weight",
            "color" to "color",
            "align" to "align",
            "lineSpacing" to "lineSpacing",
        ),
        WorldText::class to mapOf("text" to "text", "style" to "size", "billboard" to "billboard"),
    )

    private val codeOnly: Map<KClass<*>, Map<String, String>> = mapOf(
        WorldTextStyle::class to mapOf("font" to "a scene draws the built-in face; a loaded font is set from code"),
        WorldText::class to mapOf("laidOut" to "the cached layout, not an option", "revision" to "a counter, not an option"),
    )

    private fun options(type: KClass<*>): Set<String> = type.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()

    private val sceneFields = SceneWorldText.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyOptionIsSetFromASceneFileOrListedAsCodeOnly() {
        for (type in setFromSceneFile.keys) {
            val accountedFor = setFromSceneFile.getValue(type).keys + codeOnly[type].orEmpty().keys
            assertEquals(emptySet(), options(type) - accountedFor, "${type.simpleName} has options neither mapped nor listed as code-only")
            assertEquals(emptySet(), accountedFor - options(type), "${type.simpleName} lists options it no longer has")
        }
    }

    @Test
    fun everySceneFieldSetsSomething() {
        val used = setFromSceneFile.values.flatMap { it.values }.toSet()

        assertEquals(emptySet(), used - sceneFields, "mapped to scene fields that do not exist")
        assertEquals(emptySet(), sceneFields - used, "scene fields that set nothing")
    }
}
