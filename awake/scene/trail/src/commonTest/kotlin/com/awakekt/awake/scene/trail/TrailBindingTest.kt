/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.trail.TrailBlend
import kotlin.test.Test
import kotlin.test.assertEquals

/** A `trail_renderer` becomes the live trail it describes, and saves back unchanged. */
class TrailBindingTest {
    private val ribbon = SceneTrailRenderer(capacity = 300, blend = TrailBlend.Alpha, tint = SceneColor(1f, 0.5f, 0.25f, 0.75f))

    @Test
    fun everyFieldReachesTheLiveTrail() {
        val trail = ribbon.toTrailRenderer()

        assertEquals(300, trail.capacity)
        assertEquals(TrailBlend.Alpha, trail.blend)
        assertEquals(listOf(1f, 0.5f, 0.25f, 0.75f), trail.tint.let { listOf(it.r, it.g, it.b, it.a) })
        assertEquals(0, trail.visible, "a system reveals it")
    }

    @Test
    fun itSavesBackUnchanged() {
        assertEquals(ribbon, ribbon.toTrailRenderer().toScene())
    }

    @Test
    fun aCapacityThatDrawsNothingIsReported() {
        assertEquals(1, SceneTrailRenderer(capacity = 1).validate("nodes[0]").size)
        assertEquals(0, SceneTrailRenderer().validate("nodes[0]").size)
    }
}
