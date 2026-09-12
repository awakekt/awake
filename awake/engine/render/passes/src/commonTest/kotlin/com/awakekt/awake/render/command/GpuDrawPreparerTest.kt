/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class GpuDrawPreparerTest {
    @Test
    fun prepareAllKeepsSourceOrderAndDropsNullResults() {
        val mesh = object : Mesh {
            override val format = VertexFormat.PositionColorUv
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        val commands = listOf(
            RenderDrawCommand(mesh = mesh, material = material, model = Mat4()),
            RenderDrawCommand(mesh = mesh, material = material, model = Mat4()),
            RenderDrawCommand(mesh = mesh, material = material, model = Mat4()),
        )
        val context = GpuDrawPreparationContext(viewProjection = Mat4(), cameraEye = Vec3f.ZERO)
        val provider = GpuDrawPreparer { _, index, _ ->
            if (index == 1) {
                null
            } else {
                GpuResolvedDraw(
                    pipeline = object : PipelineHandle {},
                    materialBinding = object : MaterialBinding {},
                    vertexBuffer = object : BufferHandle {},
                    indexBuffer = null,
                    elementCount = index + 1,
                )
            }
        }

        assertEquals(
            listOf(1, 3),
            provider.prepareAll(commands, context).map { it.elementCount },
        )
    }
}
