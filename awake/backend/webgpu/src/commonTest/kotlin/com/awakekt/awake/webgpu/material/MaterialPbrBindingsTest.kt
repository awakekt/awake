/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.material

import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every other bind-group entry is a one-to-one lookup; the PBR views are the only ones filled
 * positionally from a list, so a wrong order here binds the normal map where the shader reads
 * occlusion -- a wrong picture, not an error.
 */
class MaterialPbrBindingsTest {

    @Test
    fun standardDeclarationKeepsTheFiveThroughEightOrder() {
        assertEquals(listOf(5, 6, 7, 8), materialPbrBindings(GroupBindings.StandardMaterial))
    }

    @Test
    fun theBaseColorImageAndItsSamplerAreNotPbrSlots() {
        val pbr = materialPbrBindings(GroupBindings.StandardMaterial)

        assertEquals(false, pbr.contains(1))
        assertEquals(false, pbr.contains(2))
    }

    /** Declaration order must not decide the mapping -- binding order does. */
    @Test
    fun outOfOrderDeclarationsStillMapByBindingIndex() {
        val bindings = GroupBindings(
            listOf(
                ResourceBinding(7, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex)),
                ResourceBinding(5, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
            ),
        )

        assertEquals(listOf(5, 7), materialPbrBindings(bindings))
    }

    /** A shader that samples only the emissive map, as skinned_textured does, gets the emissive view (#1829). */
    @Test
    fun aShaderDeclaringOnlyBindingEightGetsTheEmissiveMap() {
        assertEquals(EMISSIVE, pbrIndexOf(8, listOf(8)))
    }

    /** Bindings 5 to 8 name their map by number, whichever of them a shader declares. */
    @Test
    fun aSubsetOfTheStandardBindingsMapsByNumber() {
        assertEquals(METALLIC_ROUGHNESS, pbrIndexOf(5, listOf(5, 7)))
        assertEquals(OCCLUSION, pbrIndexOf(7, listOf(5, 7)))
        assertEquals((5..8).map { it - 5 }, (5..8).map { pbrIndexOf(it, listOf(5, 6, 7, 8)) })
    }

    /** A texture outside 5 to 8, such as the celestial shader's second image at 2, keeps its declared position. */
    @Test
    fun anotherSampledTextureTakesItsDeclaredPosition() {
        assertEquals(0, pbrIndexOf(2, listOf(2)))
    }

    @Test
    fun aDeclarationWithNoExtraTexturesHasNoPbrSlots() {
        val bindings = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
            ),
        )

        assertEquals(emptyList(), materialPbrBindings(bindings))
    }

    private companion object {
        const val METALLIC_ROUGHNESS = 0
        const val OCCLUSION = 2
        const val EMISSIVE = 3
    }
}
