/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.trail.TrailRenderer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every option on [TrailRenderer] is exposed through `trail_renderer`. A new option or scene field
 * fails here until it is listed. `TrailBindingTest` checks the mapping in both directions.
 */
@OptIn(ExperimentalSerializationApi::class)
class TrailExposureTest {
    private val setFromSceneFile = mapOf(
        "blend" to "blend",
        "tint" to "tint",
        "strip" to "capacity",
        "depthWrite" to "depthWrite",
        "doubleSided" to "doubleSided",
        "texture" to "texture",
    )

    private val options: Set<String> = TrailRenderer::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()

    private val sceneFields = SceneTrailRenderer.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyOptionIsSetFromASceneFile() {
        val accountedFor = setFromSceneFile.keys
        assertEquals(emptySet(), options - accountedFor, "TrailRenderer has options that are not mapped")
        assertEquals(emptySet(), accountedFor - options, "TrailRenderer lists options it no longer has")
    }

    @Test
    fun everySceneFieldSetsSomething() {
        val used = setFromSceneFile.values.toSet()

        assertEquals(emptySet(), used - sceneFields, "mapped to scene fields that do not exist")
        assertEquals(emptySet(), sceneFields - used, "scene fields that set nothing")
    }
}
