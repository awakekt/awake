/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.pipeline

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.DrawCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins how an instanced draw picks its flavour and its pipeline.
 *
 * No renderer test reaches instanced or particle drawing on either backend, so this is the only
 * thing standing behind the classification. It matters because the two backends had drifted:
 * WebGPU populated `PipelineTable.particlePipelines`, while Vulkan left it empty and folded the
 * particle pipeline into `instancedByFormat` under `PositionUv`. Shared code reading
 * `particlePipelines` worked on one backend and silently returned null on the other. Both now
 * populate it, and these cases fix the meaning of each map.
 */
class InstancedDrawKindTest {

    private object PlainPipeline
    private object SkinnedPipeline
    private object ParticlePipeline

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private class FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private fun drawCall(
        format: VertexFormat = VertexFormat.PositionNormalColor,
        instances: List<Mat4>? = listOf(Mat4()),
        palettes: List<FloatArray>? = null,
    ) = DrawCall(
        mesh = FakeMesh(format),
        material = FakeMaterial(),
        instanceModels = instances,
        instanceJointPalettes = palettes,
    )

    private val table = PipelineTable(
        primary = PlainPipeline,
        primaryFormat = VertexFormat.PositionColorUv,
        instancedByFormat = mapOf(VertexFormat.PositionNormalColor to PlainPipeline),
        skinnedInstancedByFormat = mapOf(VertexFormat.PositionNormalColorSkin to SkinnedPipeline),
        particlePipelines = mapOf(VertexFormat.PositionUv to ParticlePipeline),
    )

    @Test
    fun aDrawWithNoInstancesIsNotInstancedAtAll() {
        assertNull(drawCall(instances = null).instancedDrawKind())
        assertNull(drawCall(instances = emptyList()).instancedDrawKind())
    }

    @Test
    fun jointPalettesMakeItSkinnedWhateverTheVertexFormat() {
        val kind = drawCall(
            format = VertexFormat.PositionNormalColorSkin,
            palettes = listOf(FloatArray(16)),
        ).instancedDrawKind()

        assertEquals(InstancedDrawKind.Skinned, kind)
    }

    @Test
    fun positionUvWithoutPalettesIsAParticle() {
        assertEquals(
            InstancedDrawKind.Particle,
            drawCall(format = VertexFormat.PositionUv).instancedDrawKind(),
        )
    }

    @Test
    fun anythingElseInstancedIsPlain() {
        assertEquals(InstancedDrawKind.Plain, drawCall().instancedDrawKind())
    }

    /** The divergence this replaced: a particle must come from `particlePipelines`, NOT from
     * `instancedByFormat`, on both backends. */
    @Test
    fun eachKindResolvesFromItsOwnMap() {
        assertEquals(
            ParticlePipeline,
            table.resolveInstanced(VertexFormat.PositionUv, InstancedDrawKind.Particle),
        )
        assertEquals(
            SkinnedPipeline,
            table.resolveInstanced(
                VertexFormat.PositionNormalColorSkin,
                InstancedDrawKind.Skinned,
            ),
        )
        assertEquals(
            PlainPipeline,
            table.resolveInstanced(VertexFormat.PositionNormalColor, InstancedDrawKind.Plain),
        )
    }

    /** An app that built no particle pipeline draws nothing rather than borrowing another
     * flavour's -- the same skip-on-mismatch rule the non-instanced path already follows. */
    @Test
    fun anUnbuiltFlavourResolvesToNullRatherThanFallingBack() {
        val withoutParticles = PipelineTable(
            primary = PlainPipeline,
            primaryFormat = VertexFormat.PositionColorUv,
            instancedByFormat = mapOf(VertexFormat.PositionUv to PlainPipeline),
        )

        assertNull(
            withoutParticles.resolveInstanced(
                VertexFormat.PositionUv,
                InstancedDrawKind.Particle,
            ),
        )
    }
}
