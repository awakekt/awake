/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.material

import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The layout and the descriptor pool are two derivations of one [GroupBindings]. They used to
 * be two hand-written lists that happened to agree; these assert they still do, without needing
 * a device.
 */
class MaterialBindingDerivationTest {

    @Test
    fun standardLayoutMatchesWhatWasHardcodedBefore() {
        val layout = materialLayoutBindings(GroupBindings.StandardMaterial)

        assertEquals(listOf(0, 1, 2, 5, 6, 7, 8), layout.map { it.binding })
        assertEquals(VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, layout[0].descriptorType)
        assertEquals(VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, layout[1].descriptorType)
        assertEquals(VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER, layout[2].descriptorType)
        assertEquals(
            List(4) { VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE },
            layout.drop(3).map { it.descriptorType },
        )
    }

    /** The uniform block is the only entry Vulkan needs both stage bits for -- omitting either
     * trips VUID-VkGraphicsPipelineCreateInfo-layout-07988 once a shader reads it. */
    @Test
    fun standardStageFlagsMatchWhatWasHardcodedBefore() {
        val layout = materialLayoutBindings(GroupBindings.StandardMaterial)
        val bothStages = VkShaderStageFlagBits.VERTEX.value or VkShaderStageFlagBits.FRAGMENT.value

        assertEquals(bothStages, layout.first { it.binding == 0 }.stageFlags)
        assertEquals(
            List(6) { VkShaderStageFlagBits.FRAGMENT.value },
            layout.filter { it.binding != 0 }.map { it.stageFlags },
        )
    }

    /** Previously `1 + PBR_TEXTURE_BINDINGS.size` sampled images, one uniform, one sampler. */
    @Test
    fun standardPoolSizesMatchWhatWasHardcodedBefore() {
        val sizes = materialPoolSizes(GroupBindings.StandardMaterial).associate { it.type to it.descriptorCount }

        assertEquals(1, sizes[VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER])
        assertEquals(5, sizes[VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE])
        assertEquals(1, sizes[VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER])
    }

    /** Derived from `StandardMaterial` now rather than written as a literal, and the descriptor
     * writes still index `PbrImageViews.asList()` positionally against it. */
    @Test
    fun pbrTextureBindingsStillResolveToFiveThroughEight() {
        assertEquals(listOf(5, 6, 7, 8), Material.PBR_TEXTURE_BINDINGS)
    }

    @Test
    fun poolCountsFollowADeclarationThatIsNotTheStandardOne() {
        val bindings = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Vertex)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
                ResourceBinding(3, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(4, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
            ),
        )

        val sizes = materialPoolSizes(bindings).associate { it.type to it.descriptorCount }
        assertEquals(3, sizes[VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE])
        assertEquals(1, sizes[VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER])

        // A heightmap sampled in the vertex stage -- the case the fragment-only rule could not express.
        val layout = materialLayoutBindings(bindings)
        assertEquals(VkShaderStageFlagBits.VERTEX.value, layout.first { it.binding == 1 }.stageFlags)
    }
}
