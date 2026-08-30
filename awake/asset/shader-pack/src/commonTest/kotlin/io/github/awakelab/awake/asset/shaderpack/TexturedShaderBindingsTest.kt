/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.bindingsForGroup
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.pipeline.GroupBindings
import io.github.awakelab.awake.render.pipeline.ResourceKind
import io.github.awakelab.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `GroupBindings.StandardMaterial` is hand-written in the render contract and describes what
 * both backends build; `textured.wgsl` is generated from ASL. Nothing but this test stops the
 * two from drifting -- which is the whole reason the derivation exists.
 */
class TexturedShaderBindingsTest {

    private val materialGroup = BindingLayout.Standard.slot(BindingSemantic.Material)

    @Test
    fun theTexturedShaderDeclaresExactlyTheStandardMaterialGroup() {
        val derived = assertNotNull(TexturedShader.bindingsForGroup(materialGroup))

        assertEquals(GroupBindings.StandardMaterial, derived)
    }

    /** Stated separately from the equality above so a failure says which half broke. */
    @Test
    fun derivedKindsAndOrderMatchTheContract() {
        val derived = assertNotNull(TexturedShader.bindingsForGroup(materialGroup))

        assertEquals(listOf(0, 1, 2, 5, 6, 7, 8), derived.entries.map { it.binding })
        assertEquals(ResourceKind.UniformBuffer, derived.at(0)?.kind)
        assertEquals(ResourceKind.SampledTexture, derived.at(1)?.kind)
        assertEquals(ResourceKind.Sampler, derived.at(2)?.kind)
    }

    /**
     * The fact the backends could only guess at. `textured.wgsl` reads its uniform block in both
     * stages -- mvp in the vertex shader, lighting in the fragment shader -- and samples every
     * texture in the fragment shader only.
     */
    @Test
    fun stagesComeFromWhereTheShaderActuallyReadsEachBinding() {
        val derived = assertNotNull(TexturedShader.bindingsForGroup(materialGroup))

        assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), derived.at(0)?.stages)
        assertEquals(
            listOf(1, 2, 5, 6, 7, 8),
            derived.entries.filter { it.stages == setOf(ShaderStage.Fragment) }.map { it.binding },
        )
    }

    @Test
    fun aGroupTheShaderDoesNotUseIsAbsentRatherThanEmpty() {
        assertNull(TexturedShader.bindingsForGroup(materialGroup + 7))
    }
}
