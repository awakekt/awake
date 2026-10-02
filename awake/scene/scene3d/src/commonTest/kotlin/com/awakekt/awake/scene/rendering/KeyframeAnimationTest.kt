/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneFloatKey
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneVec3Key
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyframeAnimationTest {
    private val rise = SceneKeyframeAnimation(
        duration = 2f,
        position = listOf(SceneVec3Key(0.5f, SceneVec3(0f, 1f, 0f)), SceneVec3Key(1.5f, SceneVec3(0f, 3f, 0f))),
        alpha = listOf(SceneFloatKey(0f, 0f), SceneFloatKey(1f, 1f)),
    )

    @Test
    fun aTrackHoldsItsEndsInterpolatesBetweenAndLoops() {
        val node = Node(rise)

        assertEquals(1f, node.after(0.25f).position.y, "before the first key it holds that key")
        assertEquals(2f, node.after(0.75f).position.y, "halfway between the keys")
        assertEquals(3f, node.after(0.75f).position.y, "after the last key it holds that one")
        assertEquals(1f, node.after(0.5f).position.y, "the loop starts over at 2 seconds")
    }

    @Test
    fun alphaSetsTheMaterialAndEmptyTracksLeaveTheTransformAsAuthored() {
        val node = Node(rise, Transform(rotation = Vec3f(0.1f, 0.2f, 0.3f), scale = Vec3f(4f, 4f, 4f)))

        node.after(0.5f)

        assertEquals(0.5f, node.material.baseColorFactor.a)
        assertEquals(Color(1f, 0f, 0f, 0.5f), node.material.baseColorFactor, "only the alpha changes")
        assertEquals(Vec3f(0.1f, 0.2f, 0.3f), node.transform.rotation)
        assertEquals(Vec3f(4f, 4f, 4f), node.transform.scale)
    }

    @Test
    fun keysOutOfOrderOrOutsideTheLoopAreReported() {
        val issues = SceneKeyframeAnimation(
            duration = 1f,
            scale = listOf(SceneVec3Key(0.5f, SceneVec3()), SceneVec3Key(0.2f, SceneVec3())),
            alpha = listOf(SceneFloatKey(2f, 1f)),
        ).validate("effect").map { it.message }

        assertEquals(
            listOf(
                "keyframe_animation.scale keys must be in time order within 0..duration",
                "keyframe_animation.alpha keys must be in time order within 0..duration",
            ),
            issues,
        )
        assertEquals(
            listOf("keyframe_animation.duration must be greater than 0"),
            SceneKeyframeAnimation(duration = 0f).validate("effect").map { it.message },
        )
    }

    @Test
    fun keysReadFromJson() {
        val decoded = Json.decodeFromString(
            SceneKeyframeAnimation.serializer(),
            """{"duration": 2, "position": [{"time": 0.5, "value": {"y": 1}}, {"time": 1.5, "value": {"y": 3}}],
               "alpha": [{"time": 0, "value": 0}, {"time": 1, "value": 1}]}""",
        )

        assertEquals(rise, decoded)
    }

    private class Node(tracks: SceneKeyframeAnimation, val transform: Transform = Transform()) {
        private val world = World()
        private val system = KeyframeAnimationSystem()
        val material = PbrMaterial(baseColorFactor = Color(1f, 0f, 0f, 1f))

        init {
            val entity = world.create()
            world.add(entity, transform)
            world.add(entity, material)
            world.add(entity, KeyframeAnimation(tracks))
        }

        fun after(seconds: Float): Transform {
            system.update(world, seconds)
            return transform
        }
    }
}
