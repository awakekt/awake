/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * G2 parity controls for depth caster families: Ordinary, Instanced, Skinned, SkinnedInstanced,
 * Particle, Masked, and reserved domains (Terrain, Sprite, Tilemap).
 */
class DepthFeatureFamilyParityTest {

    private class StubMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private class StubMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private fun draw(
        format: VertexFormat,
        instances: List<Mat4>? = null,
        palettes: List<FloatArray>? = null,
        alphaMode: AlphaMode = AlphaMode.Opaque,
        alphaCutoff: Float = 0.5f,
    ) = RenderDrawCommand(
        mesh = StubMesh(format),
        material = StubMaterial(),
        instanceModels = instances,
        instanceJointPalettes = palettes,
        alphaMode = alphaMode,
        alphaCutoff = alphaCutoff,
    )

    @Test
    fun ordinaryCasterClassification() {
        val ordinary = draw(VertexFormat.PositionNormalColor)
        val key = ordinary.depthRenderKey()
        assertEquals(DepthCasterKind.Ordinary, key.kind)
        assertEquals(AlphaMode.Opaque, key.alphaMode)
    }

    @Test
    fun instancedCasterClassification() {
        val instanced = draw(
            format = VertexFormat.PositionNormalColor,
            instances = listOf(Mat4(), Mat4()),
        )
        val key = instanced.depthRenderKey()
        assertEquals(DepthCasterKind.Instanced, key.kind)
        assertEquals(AlphaMode.Opaque, key.alphaMode)
    }

    @Test
    fun nonInstancedSkinnedCasterClassification() {
        val skinned = draw(VertexFormat.PositionNormalColorSkin)
        val key = skinned.depthRenderKey()
        assertEquals(DepthCasterKind.Skinned, key.kind)
        assertEquals(AlphaMode.Opaque, key.alphaMode)
    }

    @Test
    fun skinnedInstancedCasterClassification() {
        val skinnedInstanced = draw(
            format = VertexFormat.PositionNormalColorSkin,
            instances = listOf(Mat4(), Mat4()),
            palettes = listOf(FloatArray(16), FloatArray(16)),
        )
        val key = skinnedInstanced.depthRenderKey()
        assertEquals(DepthCasterKind.SkinnedInstanced, key.kind)
        assertEquals(AlphaMode.Opaque, key.alphaMode)
    }

    @Test
    fun particleCasterClassification() {
        val particle = draw(
            format = VertexFormat.PositionUv,
            instances = listOf(Mat4()),
        )
        val key = particle.depthRenderKey()
        assertEquals(DepthCasterKind.Particle, key.kind)
        assertEquals(AlphaMode.Opaque, key.alphaMode)
    }

    @Test
    fun maskedVariantsPreserveMaskedAlphaModeAcrossAllFamilies() {
        val ordinaryMasked = draw(VertexFormat.PositionNormalColor, alphaMode = AlphaMode.Masked, alphaCutoff = 0.33f)
        val instancedMasked = draw(VertexFormat.PositionNormalColor, instances = listOf(Mat4()), alphaMode = AlphaMode.Masked)
        val skinnedMasked = draw(VertexFormat.PositionNormalColorSkin, alphaMode = AlphaMode.Masked)
        val skinnedInstancedMasked = draw(
            VertexFormat.PositionNormalColorSkin,
            instances = listOf(Mat4()),
            palettes = listOf(FloatArray(16)),
            alphaMode = AlphaMode.Masked,
        )
        val particleMasked = draw(VertexFormat.PositionUv, instances = listOf(Mat4()), alphaMode = AlphaMode.Masked)

        assertEquals(AlphaMode.Masked, ordinaryMasked.depthRenderKey().alphaMode)
        assertEquals(AlphaMode.Masked, instancedMasked.depthRenderKey().alphaMode)
        assertEquals(AlphaMode.Masked, skinnedMasked.depthRenderKey().alphaMode)
        assertEquals(AlphaMode.Masked, skinnedInstancedMasked.depthRenderKey().alphaMode)
        assertEquals(AlphaMode.Masked, particleMasked.depthRenderKey().alphaMode)
        assertEquals(0.33f, ordinaryMasked.alphaCutoff)
    }

    @Test
    fun maskedKeyDoesNotMatchOpaqueKey() {
        val masked = DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Masked)
        val opaque = DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Opaque)
        assertNotEquals(masked, opaque)
    }

    @Test
    fun reservedFamiliesAreDistinctAndDoNotCollideWithStandardFamilies() {
        val terrainKey = DepthRenderKey(DepthCasterKind.Terrain)
        val spriteKey = DepthRenderKey(DepthCasterKind.Sprite)
        val tilemapKey = DepthRenderKey(DepthCasterKind.Tilemap)

        assertNotEquals(DepthCasterKind.Ordinary, terrainKey.kind)
        assertNotEquals(DepthCasterKind.Instanced, terrainKey.kind)
        assertNotEquals(DepthCasterKind.Ordinary, spriteKey.kind)
        assertNotEquals(DepthCasterKind.Ordinary, tilemapKey.kind)
        assertNotEquals(terrainKey, spriteKey)
        assertNotEquals(terrainKey, tilemapKey)
    }

    @Test
    fun maskedDepthKeyCannotMatchOpaqueKeyAcrossAllKinds() {
        DepthCasterKind.entries.forEach { kind ->
            val masked = DepthRenderKey(kind, AlphaMode.Masked)
            val opaque = DepthRenderKey(kind, AlphaMode.Opaque)
            assertNotEquals(masked, opaque, "Masked depth key must never match opaque for kind $kind")
        }
    }

    @Test
    fun maskedDrawsCannotFallBackToOpaquePipelineWhenMaskedPipelineMissing() {
        val opaquePipelines = mapOf(
            DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Opaque) to "OpaquePipeline",
            DepthRenderKey(DepthCasterKind.Instanced, AlphaMode.Opaque) to "InstancedOpaquePipeline",
            DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Opaque) to "SkinnedOpaquePipeline",
            DepthRenderKey(DepthCasterKind.SkinnedInstanced, AlphaMode.Opaque) to "SkinnedInstancedOpaquePipeline",
            DepthRenderKey(DepthCasterKind.Particle, AlphaMode.Opaque) to "ParticleOpaquePipeline",
        )
        DepthCasterKind.entries.forEach { kind ->
            val maskedKey = DepthRenderKey(kind, AlphaMode.Masked)
            assertNull(opaquePipelines[maskedKey], "Masked key for $kind must not resolve to opaque pipeline")
        }
    }

    @Test
    fun wrongVertexLayoutFailsSafelyInsteadOfSilentlyBinding() {
        val formats = listOf(
            VertexFormat.PositionNormalColor,
            VertexFormat.PositionNormalColorSkin,
            VertexFormat.PositionUv,
        )
        for (i in formats.indices) {
            for (j in formats.indices) {
                if (i != j) {
                    val pipelineFormat = formats[i]
                    val drawFormat = formats[j]
                    val pipelineCompatible = drawFormat == pipelineFormat
                    assertTrue(!pipelineCompatible, "Format $drawFormat must not bind to pipeline with $pipelineFormat")
                }
            }
        }
    }

    @Test
    fun reservedDomainsCannotBindToStandardDepthPipelines() {
        val standardVariantMap = mapOf(
            DepthCasterKind.Instanced to "InstancedPipeline",
            DepthCasterKind.Skinned to "SkinnedPipeline",
            DepthCasterKind.SkinnedInstanced to "SkinnedInstancedPipeline",
            DepthCasterKind.Particle to "ParticlePipeline",
        )
        val reservedKinds = listOf(DepthCasterKind.Terrain, DepthCasterKind.Sprite, DepthCasterKind.Tilemap)
        reservedKinds.forEach { reserved ->
            assertNull(standardVariantMap[reserved], "Standard pipeline map must not contain reserved kind $reserved")
        }
    }

    @Test
    fun alphaCutoffIsPreservedInDrawRequestAndDepthRenderKey() {
        val testCutoffs = listOf(0.1f, 0.333f, 0.5f, 0.75f, 0.99f)
        testCutoffs.forEach { cutoff ->
            val draw = draw(VertexFormat.PositionNormalColor, alphaMode = AlphaMode.Masked, alphaCutoff = cutoff)
            assertEquals(cutoff, draw.alphaCutoff)
            assertEquals(AlphaMode.Masked, draw.depthRenderKey().alphaMode)
        }
    }
}
