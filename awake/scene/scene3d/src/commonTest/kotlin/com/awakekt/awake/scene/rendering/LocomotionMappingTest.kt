/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.FreeLocomotionConfig
import com.awakekt.awake.core.animation.LocomotionConfig
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneInstantiationAdapter
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneNodeHandle
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationBinding
import com.awakekt.awake.scene.rendering.animation.SceneFreeLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.toFreeLocomotionConfig
import com.awakekt.awake.scene.rendering.animation.toLocomotionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocomotionMappingTest {
    @Test
    fun allAuthoredSettingsReachTheCapabilityAndSaveBackUnchanged() {
        val sceneFree = SceneFreeLocomotionAnimation(
            idle = "Hover", move = "Glide", fast = "Boost", ascend = "Ascend", descend = "Descend",
            moveAbove = 0.2f, fastAbove = 6f, verticalAbove = 0.75f, crossFade = 0.3f,
            moveReferenceSpeed = 3f, fastReferenceSpeed = 9f,
        )
        val scene = SceneLocomotionAnimation(
            idle = "Rest", walk = "Walk", run = "Run", jump = "Rise", takeOff = "Lift", fall = "Fall", land = "Land",
            walkAbove = 0.25f, runAbove = 5f, airborneAbove = 3f, crossFade = 0.5f,
            walkReferenceSpeed = 2f, runReferenceSpeed = 7f,
            free = sceneFree,
        )
        assertEquals(
            LocomotionConfig(
                "Rest", "Walk", "Run", "Rise", "Lift", "Fall", "Land", 0.25f, 5f, 3f, 0.5f, 2f, 7f,
                FreeLocomotionConfig("Hover", "Glide", "Boost", "Ascend", "Descend", 0.2f, 6f, 0.75f, 0.3f, 3f, 9f),
            ),
            scene.toLocomotionConfig(),
        )
        val world = World()
        val entity = world.create()
        val live = LocomotionAnimation(scene)
        assertEquals(scene.toLocomotionConfig(), live.runtime.config)
        assertEquals(scene, LocomotionAnimationBinding.export(world, entity, live))
        val json = SceneSerializers.createJson()
        assertEquals(scene, json.decodeFromString(SceneLocomotionAnimation.serializer(), json.encodeToString(SceneLocomotionAnimation.serializer(), scene)))
        val legacyDecoded = json.decodeFromString(SceneLocomotionAnimation.serializer(), """{"idle":"Rest","walk":"Walk"}""")
        assertNull(legacyDecoded.free)
    }

    @Test
    fun invalidSceneConfigurationIsRejectedBeforeAnyNodeOrComponentIsAttached() {
        val invalid = listOf(
            SceneLocomotionAnimation(walkAbove = -1f), SceneLocomotionAnimation(runAbove = 0f),
            SceneLocomotionAnimation(airborneAbove = 0f), SceneLocomotionAnimation(crossFade = -1f),
            SceneLocomotionAnimation(walkReferenceSpeed = 0f), SceneLocomotionAnimation(runReferenceSpeed = 0f),
            SceneLocomotionAnimation(walkAbove = Float.NaN), SceneLocomotionAnimation(runAbove = Float.POSITIVE_INFINITY),
            SceneLocomotionAnimation(airborneAbove = Float.NaN), SceneLocomotionAnimation(crossFade = Float.NaN),
            SceneLocomotionAnimation(walkReferenceSpeed = Float.POSITIVE_INFINITY), SceneLocomotionAnimation(runReferenceSpeed = Float.NaN),
        )
        val invalidFree = listOf(
            SceneFreeLocomotionAnimation(moveAbove = -1f),
            SceneFreeLocomotionAnimation(moveAbove = 2f, fastAbove = 1f),
            SceneFreeLocomotionAnimation(verticalAbove = 0f),
            SceneFreeLocomotionAnimation(verticalAbove = -0.5f),
            SceneFreeLocomotionAnimation(crossFade = -0.1f),
            SceneFreeLocomotionAnimation(moveReferenceSpeed = 0f),
            SceneFreeLocomotionAnimation(fastReferenceSpeed = 0f),
            SceneFreeLocomotionAnimation(moveAbove = Float.NaN),
            SceneFreeLocomotionAnimation(fastAbove = Float.POSITIVE_INFINITY),
            SceneFreeLocomotionAnimation(verticalAbove = Float.NaN),
            SceneFreeLocomotionAnimation(crossFade = Float.NEGATIVE_INFINITY),
            SceneFreeLocomotionAnimation(moveReferenceSpeed = Float.POSITIVE_INFINITY),
            SceneFreeLocomotionAnimation(fastReferenceSpeed = Float.NaN),
        ).map { SceneLocomotionAnimation(free = it) }
        val adapter = object : SceneInstantiationAdapter<Unit, Unit> {
            override fun createNode(node: SceneNode, parent: Unit?) = error("invalid settings must fail before node creation")
            override fun attachName(node: Unit, name: String) = Unit
            override fun attachTransform(node: Unit, transform: SceneTransform, parent: Unit?) = Unit
            override fun attachComponent(node: Unit, component: SceneComponent) = error("invalid settings must fail before attach")
            override fun complete(roots: List<SceneNodeHandle<Unit>>) = Unit
        }
        for (settings in invalid) {
            val document = SceneDocument(name = "invalid locomotion", nodes = listOf(SceneNode(name = "Actor", components = listOf(settings))))
            val failure = assertFailsWith<SceneValidationException> { SceneLoader.instantiate(document, adapter) }
            assertTrue(failure.issues.isNotEmpty())
            assertFailsWith<IllegalArgumentException> { settings.toLocomotionConfig() }
        }
        for (settings in invalidFree) {
            val document = SceneDocument(name = "invalid free locomotion", nodes = listOf(SceneNode(name = "Actor", components = listOf(settings))))
            val failure = assertFailsWith<SceneValidationException> { SceneLoader.instantiate(document, adapter) }
            assertTrue(failure.issues.isNotEmpty())
            assertTrue(failure.issues.all { ".free" in it.path }, "issues must point to .free: ${failure.issues}")
            assertFailsWith<IllegalArgumentException> { settings.toLocomotionConfig() }
        }
    }

    @Test
    fun sceneAndCapabilityDefaultsMatch() {
        assertEquals(LocomotionConfig(), SceneLocomotionAnimation().toLocomotionConfig())
        assertEquals(FreeLocomotionConfig(), SceneFreeLocomotionAnimation().toFreeLocomotionConfig())
    }
}
