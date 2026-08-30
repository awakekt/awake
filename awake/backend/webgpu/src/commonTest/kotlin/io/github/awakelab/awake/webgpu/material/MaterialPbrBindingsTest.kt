/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.material

import io.github.awakelab.awake.render.pipeline.GroupBindings
import io.github.awakelab.awake.render.pipeline.ResourceBinding
import io.github.awakelab.awake.render.pipeline.ResourceKind
import io.github.awakelab.awake.render.pipeline.ShaderStage
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
}
