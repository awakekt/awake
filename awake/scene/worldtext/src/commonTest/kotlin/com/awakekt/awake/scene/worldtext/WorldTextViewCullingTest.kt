/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.worldtext.WorldText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A view draws only the world texts in its frustum, and a text out of one view keeps its quads for
 * the next (#1621).
 *
 * Every label in the world was drawn again for each view: a canvas portrait far above a town drew
 * every name plate in the transparent pass, where it sees none of them.
 */
class WorldTextViewCullingTest {
    private class CountingRenderer : NoopRenderer() {
        var created = 0
        var destroyed = 0

        override fun createMesh(geometry: MeshGeometry): Mesh {
            created++
            return object : Mesh {
                override val format = geometry.format
                override val sizeBytes: Long = 0
                override fun destroy() {
                    destroyed++
                }
            }
        }
    }

    @Test
    fun aViewDrawsOnlyTheTextsInItsFrustumAndKeepsTheRest() {
        val renderer = CountingRenderer()
        val feature = WorldTextRenderFeature(renderer)
        val world = World()
        repeat(LABELS) { index ->
            val label = world.create()
            world.add(label, Transform(position = Vec3f((index % ROW - ROW / 2) * 3f, 2f, (index / ROW) * -3f)))
            world.add(label, WorldText("Name $index", billboard = true))
        }
        val subject = world.create()
        world.add(subject, Transform(position = Vec3f(0f, HIGH, 0f)))
        world.add(subject, WorldText("Portrait", billboard = true))
        TransformSystem().update(world, 1f / 60f)
        val main = Camera(Lens(eye = Vec3f(0f, 20f, 30f), center = Vec3f(0f, 0f, -10f), fovYRadians = 1.2f, near = 0.1f, far = 200f))
        val portrait = Camera(
            Lens(eye = Vec3f(0f, HIGH, 5f), center = Vec3f(0f, HIGH, 0f), fovYRadians = 0.6f, near = 0.1f, far = 20f).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = 1.5f
            },
            isPrimary = false,
        )
        fun drawn(camera: Camera) = feature.collect(world, RenderFeatureContext3D(camera, 0f, 1f)).draws.size

        val mainDraws = drawn(main)
        val portraitDraws = drawn(portrait)
        val uploaded = renderer.created
        repeat(FRAMES) {
            drawn(main)
            drawn(portrait)
        }

        assertEquals(1, portraitDraws, "the portrait draws only the label beside it, not the town's")
        assertEquals(LABELS, mainDraws, "the main view sees every label on the ground")
        assertEquals(uploaded, renderer.created, "a label out of one view keeps its quads for the other")
        assertEquals(0, renderer.destroyed, "and none is freed while it lives")
    }

    private companion object {
        const val LABELS = 40
        const val ROW = 8
        const val HIGH = 20_000f
        const val FRAMES = 5
    }
}
