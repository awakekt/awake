/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.trail.TrailBlend
import com.awakekt.awake.trail.TrailRenderer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** A `trail_renderer` becomes the live trail it describes, and saves back unchanged. */
class TrailBindingTest {
    private val ribbon = SceneTrailRenderer(
        capacity = 300,
        blend = TrailBlend.Alpha,
        tint = SceneColor(1f, 0.5f, 0.25f, 0.75f),
        depthWrite = true,
        doubleSided = false,
        texture = "textures/ribbon.png",
    )

    @Test
    fun everyFieldReachesTheLiveTrail() {
        val trail = ribbon.toTrailRenderer()

        assertEquals(300, trail.capacity)
        assertEquals(TrailBlend.Alpha, trail.blend)
        assertEquals(listOf(1f, 0.5f, 0.25f, 0.75f), trail.tint.let { listOf(it.r, it.g, it.b, it.a) })
        assertEquals(0, trail.visible, "a system reveals it")
        assertEquals(true, trail.depthWrite)
        assertEquals(false, trail.doubleSided)
    }

    @Test
    fun itSavesBackUnchanged() {
        val decoded = Json.decodeFromString<SceneTrailRenderer>(Json.encodeToString(ribbon))
        val world = World()
        val entity = world.create()
        val context = object : SceneResolutionContext {
            override val world = world
            override fun deferNodeLink(targetNodeName: String, onResolved: (Entity) -> Unit) = Unit
            override fun recordRequest(request: Any) = Unit
        }
        TrailBinding.attachTyped(world, entity, decoded, context)
        val trail = requireNotNull(world.get<TrailRenderer>(entity))

        assertEquals(ribbon, TrailBinding.export(world, entity, trail))
    }

    @Test
    fun olderDocumentsKeepDefaultRasterState() {
        val scene = Json.decodeFromString<SceneTrailRenderer>("""{"capacity":32}""")
        assertEquals(SceneTrailRenderer(capacity = 32), scene)
    }

    @Test
    fun invalidCapacityAndBlankTextureAreReported() {
        assertEquals(1, SceneTrailRenderer(capacity = 1).validate("nodes[0]").size)
        assertEquals(0, SceneTrailRenderer().validate("nodes[0]").size)
        assertEquals(1, SceneTrailRenderer(texture = " ").validate("nodes[0]").size)
    }
}
