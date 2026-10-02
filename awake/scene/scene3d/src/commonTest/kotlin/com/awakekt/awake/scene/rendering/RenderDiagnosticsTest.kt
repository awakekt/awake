/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderDiagnosticsTest {
    @Test
    fun unresolvedCountsTheDrawsThePreparerRejectedAfterInstancing() {
        val renderer = NoopRenderer()
        val world = World()
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)))
        val mesh = renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor))
        val material = renderer.createMaterial()
        repeat(3) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f(index.toFloat(), 0f, 0f)))
            world.add(entity, MeshRenderer(mesh, material))
        }
        val rejectsEverything = object : GpuDrawPreparer {
            override fun prepare(
                request: GpuDrawRequest,
                sourceIndex: Int,
                context: GpuDrawPreparationContext,
            ): GpuResolvedDraw? = null

            override fun canInstance(format: VertexFormat): Boolean = true
        }

        RenderSystem3D(renderer, drawPreparer = rejectsEverything).update(world, 1f / 60f)

        // Three copies fold into one instanced draw, and that one draw is what was rejected.
        // Comparing extracted draws with resolved ones reported all three.
        assertEquals(1, RenderDiagnostics.unresolvedDrawCalls)
    }
}
