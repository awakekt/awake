/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [WorldTextBatch]: quads uploaded once per layout, transparent draws, and texts no frame draws freed. */
class WorldTextBatchTest {
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

    private val camera = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)

    @Test
    fun aTextIsUploadedOnceAndDrawnTransparentEveryFrame() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer)
        val sign = WorldText("Harbor Town")
        val draws = ArrayList<RenderDrawCommand>()
        repeat(3) {
            draws.clear()
            batch.collect(sign, Mat4(), camera, draws)
            batch.releaseUnseen()
        }

        assertEquals(1, renderer.created)
        assertEquals(1, draws.size)
        assertTrue(draws.single().transparent)
        assertTrue(!draws.single().castsShadows)
    }

    @Test
    fun changingTheTextUploadsItAgainAndATextNoFrameDrawsIsFreed() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer)
        val sign = WorldText("Market")
        val label = WorldText("Supplies", billboard = true)
        val draws = ArrayList<RenderDrawCommand>()
        batch.collect(sign, Mat4(), camera, draws)
        batch.collect(label, Mat4(), camera, draws)
        batch.releaseUnseen()

        sign.text = "Harbor Market"
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()

        assertEquals(3, renderer.created, "two texts, and the changed one again")
        assertEquals(2, renderer.destroyed, "the changed one's first quads, and the label no frame drew")
        batch.close()
        assertEquals(3, renderer.destroyed)
    }
}
