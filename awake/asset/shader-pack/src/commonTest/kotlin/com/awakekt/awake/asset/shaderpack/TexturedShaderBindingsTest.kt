/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
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
        val derived = assertNotNull(texturedShader(ClipSpace.WebGpu).bindingsForGroup(materialGroup))

        assertEquals(GroupBindings.StandardMaterial, derived)
        assertEquals(MaterialUniformLayouts.PbrTexturedDraw.total * Float.SIZE_BYTES.toLong(), derived.uniformBufferSize(0))
        assertEquals(MaterialUniformLayouts.Frame.total * Float.SIZE_BYTES.toLong(), derived.uniformBufferSize(FRAME_UNIFORM_BINDING))
    }

    /** Stated separately from the equality above so a failure says which half broke. */
    @Test
    fun derivedKindsAndOrderMatchTheContract() {
        val derived = assertNotNull(texturedShader(ClipSpace.WebGpu).bindingsForGroup(materialGroup))

        assertEquals(listOf(0, 1, 2, 5, 6, 7, 8, FRAME_UNIFORM_BINDING), derived.entries.map { it.binding })
        assertEquals(ResourceKind.UniformBuffer, derived.at(FRAME_UNIFORM_BINDING)?.kind)
        assertEquals(ResourceKind.UniformBuffer, derived.at(0)?.kind)
        assertEquals(ResourceKind.SampledTexture, derived.at(1)?.kind)
        assertEquals(ResourceKind.Sampler, derived.at(2)?.kind)
    }

    /**
     * The fact the backends could only guess at. `textured.wgsl` reads both uniform blocks in both
     * stages -- the draw's model and the frame's view-projection in the vertex shader, material
     * factors and lighting in the fragment shader -- and samples every texture in the fragment
     * shader only.
     */
    @Test
    fun stagesComeFromWhereTheShaderActuallyReadsEachBinding() {
        val derived = assertNotNull(texturedShader(ClipSpace.WebGpu).bindingsForGroup(materialGroup))

        assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), derived.at(0)?.stages)
        assertEquals(setOf(ShaderStage.Vertex, ShaderStage.Fragment), derived.at(FRAME_UNIFORM_BINDING)?.stages)
        assertEquals(
            listOf(1, 2, 5, 6, 7, 8),
            derived.entries.filter { it.stages == setOf(ShaderStage.Fragment) }.map { it.binding },
        )
    }

    @Test
    fun aGroupTheShaderDoesNotUseIsAbsentRatherThanEmpty() {
        assertNull(texturedShader(ClipSpace.WebGpu).bindingsForGroup(materialGroup + 7))
    }
}
