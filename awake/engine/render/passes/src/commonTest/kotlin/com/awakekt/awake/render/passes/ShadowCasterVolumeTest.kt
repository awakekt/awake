/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The fixed shadow box sits at the origin, 12 units either side and reaching 15 units up toward a
 * light straight overhead, which makes every expectation below a matter of reading coordinates.
 */
class ShadowCasterVolumeTest {
    private val overhead = directionalShadowBox(Vec3f(0f, 1f, 0f), ClipSpace.WebGpu).viewProjection

    @Test
    fun aCasterInsideTheBoxOrAboveItTowardTheLightIsAdmitted() {
        val volume = ShadowCasterVolume().apply { addCascade(overhead) }

        assertTrue(volume.admits(box(0f, 0f, 0f)), "inside the box")
        assertTrue(volume.admits(box(0f, 500f, 0f)), "far above it, between the light and the box, it still casts")
        assertFalse(volume.admits(box(30f, 0f, 0f)), "beside the box, it cannot reach the map")
        assertFalse(volume.admits(box(0f, -40f, 0f)), "past the far plane, it is behind everything the map holds")
    }

    @Test
    fun boundsHoldEveryAdmittedCasterWithinTheContent() {
        val volume = ShadowCasterVolume().apply { addCascade(directionalShadowBox(Vec3f(1f, 1f, 0f), ClipSpace.WebGpu).viewProjection) }
        val content = Aabb(Vec3f(-1000f, -10f, -1000f), Vec3f(1000f, 60f, 1000f))
        val reach = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))

        assertTrue(volume.bounds(content, reach))
        // Sample the content: every admitted point's box must lie inside the reach.
        val admitted = samples().map { (x, y, z) -> box(x, y, z, half = 0.1f) }.filter(volume::admits)
        admitted.forEach { caster ->
            val inside = caster.min.x <= reach.max.x && caster.max.x >= reach.min.x &&
                caster.min.z <= reach.max.z && caster.max.z >= reach.min.z
            assertTrue(inside, "admitted at ${caster.min} but outside $reach")
        }
        assertTrue(admitted.isNotEmpty(), "a sample that admits nothing proves nothing")
        assertTrue(reach.max.x - reach.min.x < 2000f, "the reach is cut by the content's height, not the whole content")
    }

    @Test
    fun followsTheShadowPassGate() {
        val shadowed = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f), viewProjection = overhead)
        val volume = ShadowCasterVolume()

        assertTrue(volume.set(shadowed, EnvironmentUniforms.Default.copy(shadowsEnabled = false)).isEmpty, "shadows off, nothing casts")
        assertTrue(volume.set(null, EnvironmentUniforms.Default.copy(shadowsEnabled = true)).isEmpty)
        assertFalse(volume.set(shadowed, EnvironmentUniforms.Default.copy(shadowsEnabled = true)).isEmpty)
    }

    @Test
    fun aShadowedPointLightAdmitsWhatItsCubeHolds() {
        val light = SceneLight(
            direction = Vec3f(0f, 1f, 0f),
            color = Vec3f(0f, 0f, 0f),
            points = listOf(
                PointLight(position = Vec3f(50f, 0f, 0f), color = Vec3f(1f, 1f, 1f), range = 5f, shadowBaseLayer = MAX_CASCADE_LAYERS),
                PointLight(position = Vec3f(-50f, 0f, 0f), color = Vec3f(1f, 1f, 1f), range = 5f),
            ),
        )
        val volume = ShadowCasterVolume().set(light, EnvironmentUniforms.Default.copy(shadowsEnabled = true))

        assertTrue(volume.admits(box(53f, 0f, 0f)), "inside the shadowed light's range")
        assertFalse(volume.admits(box(-50f, 0f, 0f)), "a light with no shadow layer casts nothing")
        assertFalse(volume.admits(box(60f, 0f, 0f)), "beyond its range")
    }

    /** A grid of points over x and z -100..100 and y -10..60. */
    private fun samples(): List<Triple<Float, Float, Float>> =
        (-100..100 step 4).flatMap { x ->
            (-10..60 step 5).flatMap { y -> (-100..100 step 4).map { z -> Triple(x.toFloat(), y.toFloat(), z.toFloat()) } }
        }

    private fun box(x: Float, y: Float, z: Float, half: Float = 1f) =
        Aabb(Vec3f(x - half, y - half, z - half), Vec3f(x + half, y + half, z + half))

    private companion object {
        const val MAX_CASCADE_LAYERS = 4
    }
}
