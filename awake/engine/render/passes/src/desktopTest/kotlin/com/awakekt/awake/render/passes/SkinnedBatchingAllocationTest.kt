/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that steady-state automatic skinned draw batching allocates zero heap bytes per frame
 * once its per-group palette and tint pools have warmed up (even with escape analysis disabled).
 */
class SkinnedBatchingAllocationTest {
    @Test
    fun steadyStateSkinnedBatchingAllocatesNothing() {
        val mesh = object : Mesh {
            override val format = VertexFormat.PositionNormalColorSkin
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        val draws = List(COPIES) { index ->
            val pose = Mat4().data + Mat4().translate(0f, index * 0.05f, 0f).data
            RenderDrawCommand(
                mesh = mesh,
                material = material,
                model = Mat4().translate(index.toFloat(), 0f, 0f),
                extraUniformFloats = skinnedMaterialFloats(
                    palette = pose,
                    baseColorFactor = Color(1f, 0.5f + (index % 2) * 0.5f, 0.25f, 1f),
                    emissiveFactor = Color(0f, 0f, 0f, 0f),
                    metallic = 0.1f,
                    roughness = 0.6f,
                ),
            )
        }
        val batcher = InstanceBatcher()
        val noPlain: (VertexFormat, CullMode) -> Boolean = { _, _ -> false }
        val allowSkinned: (VertexFormat, CullMode) -> Boolean = { format, cull ->
            format == VertexFormat.PositionNormalColorSkin && cull == CullMode.None
        }

        val warmOut = batcher.batch(draws, noPlain, canInstanceSkinned = allowSkinned)
        assertEquals(1, warmOut.size)
        assertEquals(COPIES, warmOut[0].instanceJointPalettes?.size)

        var totalDraws = 0
        val reading = readAllocation(FRAMES) {
            totalDraws = 0
            repeat(FRAMES) {
                totalDraws += batcher.batch(draws, noPlain, canInstanceSkinned = allowSkinned).size
            }
        }
        assertEquals(FRAMES, totalDraws)
        println("SkinnedBatchingAllocationTest ($COPIES skinned draws): $reading")
        assertAllocatesNothing(reading, "InstanceBatcher.batch (skinned)")
    }

    private companion object {
        const val COPIES = 32
        const val FRAMES = 500
    }
}
