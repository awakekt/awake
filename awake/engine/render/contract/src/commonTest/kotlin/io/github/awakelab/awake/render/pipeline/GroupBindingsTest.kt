/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.pipeline

import io.github.awakelab.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GroupBindingsTest {

    /** Phase 2/3 replace two hardcoded backend layouts with this value, so it has to keep
     * describing exactly what they build today -- including the 3/4 gap. */
    @Test
    fun standardMaterialMatchesTheLayoutBothBackendsHardcode() {
        val bindings = GroupBindings.StandardMaterial

        assertEquals(listOf(0, 1, 2, 5, 6, 7, 8), bindings.entries.map { it.binding })
        assertEquals(ResourceKind.UniformBuffer, bindings.at(0)?.kind)
        assertEquals(ResourceKind.SampledTexture, bindings.at(1)?.kind)
        assertEquals(ResourceKind.Sampler, bindings.at(2)?.kind)
        assertNull(bindings.at(3))
        assertNull(bindings.at(4))
        assertEquals(4, bindings.entries.count { it.kind == ResourceKind.SampledTexture && it.binding >= 5 })
    }

    @Test
    fun onlyTheUniformBlockSpansBothStages() {
        val bindings = GroupBindings.StandardMaterial

        assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), bindings.at(0)?.stages)
        assertEquals(
            listOf(1, 2, 5, 6, 7, 8),
            bindings.entries.filter { it.stages == setOf(ShaderStage.Fragment) }.map { it.binding },
        )
    }

    /** The case the whole phase exists for: a heightmap sampled in the vertex stage to displace
     * terrain, which the "textures are fragment-only" rule the backends hardcode cannot express. */
    @Test
    fun aTextureCanBeDeclaredForTheVertexStage() {
        val bindings = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Vertex)),
            ),
        )

        assertEquals(setOf(ShaderStage.Vertex), bindings.at(1)?.stages)
    }

    @Test
    fun duplicateBindingIndicesFail() {
        assertFailsWith<IllegalArgumentException> {
            GroupBindings(
                listOf(
                    ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                    ResourceBinding(1, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
                ),
            )
        }
    }

    @Test
    fun anEmptyGroupFails() {
        assertFailsWith<IllegalArgumentException> { GroupBindings(emptyList()) }
    }

    @Test
    fun aBindingNoStageReadsFails() {
        assertFailsWith<IllegalArgumentException> {
            ResourceBinding(0, ResourceKind.UniformBuffer, emptySet())
        }
    }

    @Test
    fun negativeBindingIndicesFail() {
        assertFailsWith<IllegalArgumentException> {
            ResourceBinding(-1, ResourceKind.Sampler, setOf(ShaderStage.Fragment))
        }
    }

    /** [PipelineSpec] keys the registry, so two specs differing only here must not collide. */
    @Test
    fun materialBindingsParticipateInSpecEquality() {
        val base = PipelineSpec(
            vertexFormat = VertexFormat.None,
            vertexShader = ShaderSource.ResourcePath("v.wgsl", "vs"),
            fragmentShader = ShaderSource.ResourcePath("f.wgsl", "fs"),
        )
        val declared = base.copy(materialBindings = GroupBindings.StandardMaterial)

        assertNull(base.materialBindings)
        assertEquals(base, base.copy())
        assertEquals(false, base == declared)
    }
}
