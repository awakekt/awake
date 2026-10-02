/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildPipelineTableTest {
    private val factory = PipelineFactory<PipelineVariant> { _, spec -> spec.variant }

    private fun request(key: PipelineKey, format: VertexFormat, variant: PipelineVariant) = PipelineRequest(
        key = key,
        spec = PipelineSpec(
            vertexFormat = format,
            vertexShader = ShaderSource.ResourcePath("shaders/a.vert.spv", "vertexMain"),
            fragmentShader = ShaderSource.ResourcePath("shaders/a.frag.spv", "fragmentMain"),
            variant = variant,
        ),
        buildAdditive = true,
    )

    /** A particle's additive twin keeps its instance bindings; a mesh's is the plain additive blend. */
    @Test
    fun theAdditiveTwinOfAParticlePipelineStaysInstanced() = runTest {
        val table = buildPipelineTable(
            listOf(
                request(PipelineKey.Particle, VertexFormat.PositionUv, PipelineVariant.AlphaBlendedParticle),
                request(PipelineKey.Primary, VertexFormat.PositionColorUv, PipelineVariant.Opaque),
            ),
            factory,
        )

        assertEquals(PipelineVariant.AdditiveBlendedParticle, table.getValue(PipelineKey.Particle).additive)
        assertEquals(PipelineVariant.AdditiveBlended, table.getValue(PipelineKey.Primary).additive)
    }
}
