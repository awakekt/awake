/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.LocomotionConfig
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationBinding
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.toLocomotionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class LocomotionMappingTest {
    @Test
    fun allAuthoredSettingsReachTheCapabilityAndSaveBackUnchanged() {
        val scene = SceneLocomotionAnimation(
            idle = "Rest", walk = "Walk", run = "Run", jump = "Rise", takeOff = "Lift", fall = "Fall", land = "Land",
            walkAbove = 0.25f, runAbove = 5f, airborneAbove = 3f, crossFade = 0.5f,
            walkReferenceSpeed = 2f, runReferenceSpeed = 7f,
        )
        assertEquals(
            LocomotionConfig("Rest", "Walk", "Run", "Rise", "Lift", "Fall", "Land", 0.25f, 5f, 3f, 0.5f, 2f, 7f),
            scene.toLocomotionConfig(),
        )
        val world = World()
        val entity = world.create()
        val live = LocomotionAnimation(scene)
        assertEquals(scene.toLocomotionConfig(), live.runtime.config)
        assertEquals(scene, LocomotionAnimationBinding.export(world, entity, live))
        val json = SceneSerializers.createJson()
        assertEquals(scene, json.decodeFromString(SceneLocomotionAnimation.serializer(), json.encodeToString(SceneLocomotionAnimation.serializer(), scene)))
    }

    @Test
    fun sceneAndCapabilityDefaultsMatch() {
        assertEquals(LocomotionConfig(), SceneLocomotionAnimation().toLocomotionConfig())
    }
}
