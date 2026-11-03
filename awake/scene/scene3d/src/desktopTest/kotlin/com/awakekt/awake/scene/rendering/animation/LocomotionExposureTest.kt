/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.LocomotionConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/** A new capability option cannot silently be left out of the scene's authored data. */
@OptIn(ExperimentalSerializationApi::class)
class LocomotionExposureTest {
    @Test
    fun everyCapabilityOptionIsAuthoredByTheScene() {
        val options = LocomotionConfig::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()
        assertEquals(options, SceneLocomotionAnimation.serializer().descriptor.elementNames.toSet())
        // No code-only configuration: position, delta, ground contact and AnimationPlayer are
        // runtime update inputs rather than authored options. LocomotionMappingTest checks values.
    }
}
