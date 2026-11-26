/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinnedInstanceBatchingTest {
    private val skinnedMesh = fakeMesh(VertexFormat.PositionNormalColorSkin)
    private val material = fakeMaterial()

    private val noPlain: (VertexFormat, CullMode) -> Boolean = { _, _ -> false }
    private val allowSkinned: (VertexFormat, CullMode) -> Boolean = { format, cullMode ->
        format == VertexFormat.PositionNormalColorSkin && cullMode == CullMode.None
    }

    @Test
    fun foldsMatchingBarePaletteSkinnedDrawsIntoOneBatch() {
        val poses = List(4) { i -> palette(i * 0.5f) }
        val draws = List(4) { i ->
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                model = Mat4().translate(i * 2f, 0f, 0f),
                extraUniformFloats = poses[i],
                worldBounds = Aabb(Vec3f(i * 2f - 1f, -1f, -1f), Vec3f(i * 2f + 1f, 1f, 1f)),
            )
        }

        val batched = batchInstances(draws, noPlain, allowSkinned)

        assertEquals(1, batched.size)
        val combined = batched[0]
        assertEquals(4, combined.instanceModels?.size)
        assertEquals(4, combined.instanceJointPalettes?.size)
        assertNull(combined.instanceColors, "untinted bare palettes should omit per-instance tint stream")
        assertEquals(0, combined.extraUniformFloats.size)
        for (i in 0 until 4) {
            assertTrue(poses[i].contentEquals(combined.instanceJointPalettes!![i]), "pose $i must match")
        }
        assertEquals(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(7f, 1f, 1f)), combined.worldBounds)
    }

    @Test
    fun foldsSkinnedMaterialLayoutDrawsWithPerInstanceTintsAndSharedPbrFactors() {
        val red = Color(1f, 0.2f, 0.1f, 0.5f)
        val blue = Color(0.1f, 0.3f, 1f, 1f)
        val draws = listOf(
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                model = Mat4().translate(-1f, 0f, 0f),
                extraUniformFloats = skinnedMaterialFloats(
                    palette = palette(-0.5f),
                    baseColorFactor = red,
                    emissiveFactor = Color(0f, 0f, 0f, 0f),
                    metallic = 0.25f,
                    roughness = 0.75f,
                ),
            ),
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                model = Mat4().translate(1f, 0f, 0f),
                extraUniformFloats = skinnedMaterialFloats(
                    palette = palette(0.5f),
                    baseColorFactor = blue,
                    emissiveFactor = Color(0f, 0f, 0f, 0f),
                    metallic = 0.25f,
                    roughness = 0.75f,
                ),
            ),
        )

        val batched = batchInstances(draws, noPlain, allowSkinned)

        assertEquals(1, batched.size)
        val combined = batched[0]
        assertEquals(2, combined.instanceModels?.size)
        assertEquals(2, combined.instanceJointPalettes?.size)
        assertEquals(SkinnedInstanceFold.PALETTE_FLOATS, combined.instanceJointPalettes!![0].size)
        assertEquals(SkinnedInstanceFold.PALETTE_FLOATS, combined.instanceJointPalettes!![1].size)
        assertEquals(-0.5f, combined.instanceJointPalettes!![0][16 + 13])
        assertEquals(0.5f, combined.instanceJointPalettes!![1][16 + 13])
        val colors = assertNotNull(combined.instanceColors)
        assertEquals(listOf(Vec4(red.r, red.g, red.b, red.a), Vec4(blue.r, blue.g, blue.b, blue.a)), colors)
        assertEquals(4, combined.extraUniformFloats.size)
        assertEquals(0.25f, combined.extraUniformFloats[0])
        assertEquals(0.75f, combined.extraUniformFloats[1])
    }

    @Test
    fun loneSkinnedDrawRemainsSingle() {
        val single = RenderDrawCommand(
            mesh = skinnedMesh,
            material = material,
            model = Mat4().translate(3f, 0f, 0f),
            extraUniformFloats = palette(0.25f),
        )

        val batched = batchInstances(listOf(single), noPlain, allowSkinned)

        assertEquals(1, batched.size)
        assertNull(batched[0].instanceModels)
        assertNull(batched[0].instanceJointPalettes)
    }

    @Test
    fun keepsUnlitEmissiveMaskedTransparentAndEmptySkinnedDrawsSeparate() {
        val unlit = List(2) {
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                extraUniformFloats = skinnedMaterialFloats(
                    palette = palette(it.toFloat()),
                    baseColorFactor = Color.White,
                    emissiveFactor = Color(0f, 0f, 0f, 0f),
                    unlit = true,
                ),
            )
        }
        val emissive = List(2) {
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                extraUniformFloats = skinnedMaterialFloats(
                    palette = palette(it.toFloat()),
                    baseColorFactor = Color.White,
                    emissiveFactor = Color(1f, 0f, 0f, 0f),
                ),
            )
        }
        val masked = List(2) {
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                alphaMode = AlphaMode.Masked,
                extraUniformFloats = palette(it.toFloat()),
            )
        }
        val emptyExtras = List(2) {
            RenderDrawCommand(mesh = skinnedMesh, material = material, extraUniformFloats = FloatArray(0))
        }

        assertEquals(2, batchInstances(unlit, noPlain, allowSkinned).size)
        assertEquals(2, batchInstances(emissive, noPlain, allowSkinned).size)
        assertEquals(2, batchInstances(masked, noPlain, allowSkinned).size)
        assertEquals(2, batchInstances(emptyExtras, noPlain, allowSkinned).size)
    }

    @Test
    fun splitsSkinnedDrawsByMetallicRoughnessAndFadeOpacity() {
        val metalA = List(2) {
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                extraUniformFloats = skinnedMaterialFloats(
                    palette = palette(it.toFloat()),
                    baseColorFactor = Color.White,
                    emissiveFactor = Color(0f, 0f, 0f, 0f),
                    metallic = 0.1f,
                    roughness = 0.5f,
                ),
            )
        }
        val metalB = List(2) {
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                extraUniformFloats = skinnedMaterialFloats(
                    palette = palette(it.toFloat()),
                    baseColorFactor = Color.White,
                    emissiveFactor = Color(0f, 0f, 0f, 0f),
                    metallic = 0.9f,
                    roughness = 0.5f,
                ),
            )
        }
        val faded = List(2) {
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                extraUniformFloats = palette(it.toFloat()),
            ).apply { fadeOpacity = 0.5f }
        }

        val batched = batchInstances(metalA + metalB + faded, noPlain, allowSkinned)

        assertEquals(3, batched.size)
        assertTrue(batched.all { it.instanceModels?.size == 2 && it.instanceJointPalettes?.size == 2 })
    }

    @Test
    fun chunksLargeSkinnedGroupAtMaxSkinnedBatchedInstances() {
        val count = MAX_SKINNED_BATCHED_INSTANCES + 44
        val draws = List(count) { i ->
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                model = Mat4().translate(i.toFloat(), 0f, 0f),
                extraUniformFloats = palette(i * 0.01f),
            )
        }

        val batched = batchInstances(draws, noPlain, allowSkinned)

        assertEquals(2, batched.size)
        assertEquals(MAX_SKINNED_BATCHED_INSTANCES, batched[0].instanceModels?.size)
        assertEquals(MAX_SKINNED_BATCHED_INSTANCES, batched[0].instanceJointPalettes?.size)
        assertEquals(44, batched[1].instanceModels?.size)
        assertEquals(44, batched[1].instanceJointPalettes?.size)
    }

    @Test
    fun diagnosisAgreesWithAutomaticSkinnedBatching() {
        val draws = List(5) { i ->
            RenderDrawCommand(
                mesh = skinnedMesh,
                material = material,
                model = Mat4().translate(i.toFloat(), 0f, 0f),
                extraUniformFloats = palette(i * 0.1f),
            )
        }

        val foldedStats = SceneDrawStats().also {
            diagnoseInstanceFolds(draws, noPlain, it, canInstanceSkinned = allowSkinned)
        }
        assertEquals(1, foldedStats.foldedDraws)
        assertEquals(5, foldedStats.foldedCopies)
        assertEquals(0, foldedStats.totalMisses)

        val uncapableStats = SceneDrawStats().also {
            diagnoseInstanceFolds(draws, noPlain, it)
        }
        assertEquals(0, uncapableStats.foldedDraws)
        assertEquals(5, uncapableStats.misses(InstanceFoldMiss.SkinnedNotInstanced))
    }

    private fun palette(yOffset: Float): FloatArray = Mat4().data + Mat4().translate(0f, yOffset, 0f).data

    private fun fakeMesh(format: VertexFormat) = object : Mesh {
        override val format = format
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial() = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
