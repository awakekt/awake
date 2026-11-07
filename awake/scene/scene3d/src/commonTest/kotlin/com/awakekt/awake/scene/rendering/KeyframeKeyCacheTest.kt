/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneVec3Key
import com.awakekt.awake.scene.rendering.animation.keyAfter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Starting each track's key search where the last frame ended reads the same value as a scan from key 0 (#1428). */
class KeyframeKeyCacheTest {
    @Test
    fun aHintedSearchFindsTheSameKeyAsAScanFromTheStart() {
        val times = floatArrayOf(0f, 0.5f, 0.5f, 1f, 2.5f, 3f)
        for (hint in 0..times.size + 2) {
            var t = -0.25f
            while (t <= 3.25f) {
                assertEquals(scan(times, t), keyAfter(times.size, t, hint) { times[it] }, "time $t from hint $hint")
                t += 0.125f
            }
        }
    }

    @Test
    fun aLoopingNodeMovesAsItDidWhenEveryFrameScannedFromKeyZero() {
        val keys = List(KEYS) { index -> SceneVec3Key(index * DURATION / (KEYS - 1), SceneVec3(index.toFloat(), (index * index).toFloat(), -index.toFloat())) }
        val world = World()
        val transform = Transform()
        val entity = world.create()
        world.add(entity, transform)
        val animation = KeyframeAnimation(SceneKeyframeAnimation(duration = DURATION, position = keys))
        world.add(entity, animation)
        val system = KeyframeAnimationSystem(hasRenderer = false)

        var time = 0f
        // Uneven steps, some longer than a key and one longer than the whole loop.
        val steps = floatArrayOf(0.016f, 0.033f, 0.25f, 0.7f, 0.001f, DURATION * 1.3f, 0.016f)
        repeat(FRAMES) { frame ->
            val delta = steps[frame % steps.size]
            system.update(world, delta)
            time = (time + delta).mod(DURATION)
            val expected = expected(keys, time)
            assertEquals(expected.x, transform.position.x, TOLERANCE, "x, frame $frame")
            assertEquals(expected.y, transform.position.y, TOLERANCE, "y, frame $frame")
            assertEquals(expected.z, transform.position.z, TOLERANCE, "z, frame $frame")
        }
    }

    private fun scan(times: FloatArray, time: Float): Int {
        var index = 0
        while (index < times.size && times[index] <= time) index++
        return index
    }

    /** The track at [time], interpolated from a scan that starts at key 0 every time. */
    private fun expected(keys: List<SceneVec3Key>, time: Float): SceneVec3 {
        val next = scan(FloatArray(keys.size) { keys[it].time }, time)
        val from = keys[(next - 1).coerceAtLeast(0)]
        val to = keys[next.coerceAtMost(keys.size - 1)]
        val f = if (to.time > from.time) ((time - from.time) / (to.time - from.time)).coerceIn(0f, 1f) else 0f
        return SceneVec3(
            from.value.x + (to.value.x - from.value.x) * f,
            from.value.y + (to.value.y - from.value.y) * f,
            from.value.z + (to.value.z - from.value.z) * f,
        )
    }

    private companion object {
        const val KEYS = 24
        const val DURATION = 2f
        const val FRAMES = 400
        const val TOLERANCE = 1e-3f
    }
}
