/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.shadowCascadeUniforms
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowFadeUniformsTest {

    private val camera = Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 200f)
    private val light = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))

    @Test
    fun theFadeRidesInTheFractionalPartOfTheCascadeCount() {
        val faded = shadowCascadeUniforms(light, camera, 1f, ClipSpace.WebGpu, shadowDistance = 50f, shadowFade = 0.25f)

        assertEquals(0.25f, faded.shadowFade)
        assertEquals(faded.count.toFloat(), floor(faded.countAndFade), "the whole part is still the count")
        assertEquals(0.25f, faded.countAndFade - faded.count, 1e-6f)
    }

    @Test
    fun noFadeKeepsTheCountAloneAndAFullFadeNeverCarriesIntoIt() {
        val hard = shadowCascadeUniforms(light, camera, 1f, ClipSpace.WebGpu, shadowDistance = 50f)
        val full = shadowCascadeUniforms(light, camera, 1f, ClipSpace.WebGpu, shadowDistance = 50f, shadowFade = 5f)

        assertEquals(hard.count.toFloat(), hard.countAndFade)
        assertEquals(full.count, floor(full.countAndFade).toInt(), "a fade past 1 is capped below it")
    }
}
