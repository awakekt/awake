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
 * Every option of a trail is either something a `trail_renderer` can set or something only code can,
 * and this says which: a new option on [TrailRenderer], or a new scene field, fails here until it is
 * listed. `TrailMapping.kt` carries a listed option across; `TrailBindingTest` checks it.
 */
@OptIn(ExperimentalSerializationApi::class)
class TrailExposureTest {
    private val setFromSceneFile = mapOf("blend" to "blend", "tint" to "tint", "strip" to "capacity")

    private val codeOnly = mapOf(
        "texture" to "a scene names no texture yet; a loaded texture is set from code",
    )

    private val options: Set<String> = TrailRenderer::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()

    private val sceneFields = SceneTrailRenderer.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyOptionIsSetFromASceneFileOrListedAsCodeOnly() {
        val accountedFor = setFromSceneFile.keys + codeOnly.keys
        assertEquals(emptySet(), options - accountedFor, "TrailRenderer has options neither mapped nor listed as code-only")
        assertEquals(emptySet(), accountedFor - options, "TrailRenderer lists options it no longer has")
    }

    @Test
    fun everySceneFieldSetsSomething() {
        val used = setFromSceneFile.values.toSet()

        assertEquals(emptySet(), used - sceneFields, "mapped to scene fields that do not exist")
        assertEquals(emptySet(), sceneFields - used, "scene fields that set nothing")
    }
}
