/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.texture

import io.github.awakelab.awake.vulkan.VK_SUBPASS_EXTERNAL
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.enums.VkAttachmentStoreOp
import io.github.awakelab.awake.vulkan.enums.VkFormat
import io.github.awakelab.awake.vulkan.enums.VkImageAspectFlagBits
import io.github.awakelab.awake.vulkan.enums.VkImageLayout
import io.github.awakelab.awake.vulkan.enums.VkImageViewType
import io.github.awakelab.awake.vulkan.enums.VkPipelineBindPoint
import io.github.awakelab.awake.vulkan.enums.VkShaderStageFlagBits
import io.github.awakelab.awake.vulkan.enums.flags.VkAccessFlagBits
import io.github.awakelab.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.awakelab.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import io.github.awakelab.awake.vulkan.gen.VulkanBuffers
import io.github.awakelab.awake.vulkan.gen.VulkanDescriptors
import io.github.awakelab.awake.vulkan.gen.VulkanImages
import io.github.awakelab.awake.vulkan.models.VkAttachmentDescription
import io.github.awakelab.awake.vulkan.models.VkAttachmentReference
import io.github.awakelab.awake.vulkan.models.VkSubpassDependency
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorImageInfo
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorPoolSize
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorType
import io.github.awakelab.awake.vulkan.models.info.VkFilter
import io.github.awakelab.awake.vulkan.models.info.VkFramebufferCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkImageCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkImageSubresourceRange
import io.github.awakelab.awake.vulkan.models.info.VkImageUsageFlagBits2
import io.github.awakelab.awake.vulkan.models.info.VkImageViewCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkMemoryAllocateInfo
import io.github.awakelab.awake.vulkan.models.info.VkRenderPassCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkSamplerAddressMode
import io.github.awakelab.awake.vulkan.models.info.VkSamplerCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkSubpassDescription
import io.github.awakelab.awake.vulkan.pipeline.VulkanMaterialBinding

/**
 * A square depth-only render target a feature can render into and then sample: image, view,
 * sampler, render pass, framebuffer and its own descriptor set at the pipeline's shadow-depth
 * binding slot.
 *
 * [OffscreenRenderTarget]'s equivalent for depth. No color attachment, and a render pass whose
 * `finalLayout` is already `SHADER_READ_ONLY_OPTIMAL`, so nothing has to call the separate
 * transition [OffscreenRenderTarget.transitionToShaderReadOnly] needs for its color image.
 *
 * Hardware only, by name and by content: it knows a depth format and a set index, and nothing
 * about why a caller wants depth rendered offscreen. A shadow map is the one use today; a
 * depth pre-pass for occlusion or SSAO would be the same object.
 *
 * Sampled as a plain (non-comparison) texture, not via a hardware comparison sampler: this
 * repo's `VkSamplerCreateInfo` JNI binding has no `compareEnable`/`compareOp` fields today
 * (extending the native binding generator is out of scope here), so a consumer does the depth
 * comparison in its own fragment shader. [sampler] is therefore `NEAREST`-filtered --
 * linear-filtering raw, un-compared depth values blends across a depth discontinuity, which
 * means nothing.
 */
class DepthTarget(
    graphicsDevice: GraphicsDevice,
    val size: Int = DEFAULT_SIZE,
) {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device

    var renderPass: Long = 0
        private set
    var image: Long = 0
        private set
    private var imageMemory: Long = 0
    var imageView: Long = 0
        private set
    var sampler: Long = 0
        private set
    var framebuffer: Long = 0
        private set

    /**
     * This map's own descriptor set -- image at binding 0 and sampler at binding 1 in the
     * pipeline's shadow-depth binding group.
     *
     * Owned here rather than appended to every material's set 0, which is what forced the map to
     * exist before any material layout, before the render pass and before any pipeline. One
     * per-frame resource, one descriptor set, bound once per pass.
     */
    var descriptorSetLayout: Long = 0
        private set
    private var descriptorPool: Long = 0
    private var descriptorSet: Long = 0

    /** For the scene pass to bind through its shadow-depth semantic. */
    fun binding(): VulkanMaterialBinding = Binding(descriptorSet)

    private class Binding(override val descriptorSetHandle: Long) : VulkanMaterialBinding

    init {
        renderPass = createRenderPass()
        image = VulkanImages.vkCreateImage(
            device,
            VkImageCreateInfo(
                width = size,
                height = size,
                format = DEPTH_FORMAT.value,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or
                    VkImageUsageFlagBits2.VK_IMAGE_USAGE_SAMPLED_BIT,
            ),
        )
        val requirements = VulkanImages.vkGetImageMemoryRequirements(device, image)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        )
        imageMemory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanImages.vkBindImageMemory(device, image, imageMemory, 0)
        imageView = Vulkan.vkCreateImageView(
            device,
            VkImageViewCreateInfo(
                image = image,
                viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                format = DEPTH_FORMAT,
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value,
                    baseMipLevel = 0,
                    levelCount = 1,
                    baseArrayLayer = 0,
                    layerCount = 1,
                ),
            ),
        )
        sampler = VulkanImages.vkCreateSampler(
            device,
            VkSamplerCreateInfo(
                magFilter = VkFilter.VK_FILTER_NEAREST,
                minFilter = VkFilter.VK_FILTER_NEAREST,
                addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            ),
        )
        framebuffer = Vulkan.vkCreateFramebuffer(
            device,
            VkFramebufferCreateInfo(
                renderPass = renderPass,
                pAttachments = arrayOf(imageView),
                width = size,
                height = size,
                layers = 1,
            ),
        )
        createDescriptorSet()
    }

    /** Set 1's layout and its single set: the depth image and its sampler, written once. */
    private fun createDescriptorSet() {
        descriptorSetLayout = VulkanDescriptors.vkCreateDescriptorSetLayout(
            device,
            VkDescriptorSetLayoutCreateInfo(
                pBindings = arrayOf(
                    VkDescriptorSetLayoutBinding(
                        binding = 0,
                        descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                        stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                    ),
                    VkDescriptorSetLayoutBinding(
                        binding = 1,
                        descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
                        stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                    ),
                ),
            ),
        )
        descriptorPool = VulkanDescriptors.vkCreateDescriptorPool(
            device,
            VkDescriptorPoolCreateInfo(
                maxSets = 1,
                pPoolSizes = arrayOf(
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, descriptorCount = 1),
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER, descriptorCount = 1),
                ),
            ),
        )
        descriptorSet = VulkanDescriptors.vkAllocateDescriptorSet(device, descriptorPool, descriptorSetLayout)
        // Two writes, image and sampler separately -- WGSL has no combined-sampler type, so naga
        // always emits the pair. Same split Material's own bindings 1/2 use.
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            descriptorSet,
            0,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(sampler = 0L, imageView = imageView),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            descriptorSet,
            1,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
            VkDescriptorImageInfo(sampler = sampler, imageView = 0L),
        )
    }

    private fun createRenderPass(): Long = Vulkan.vkCreateRenderPass(
        device,
        VkRenderPassCreateInfo(
            pAttachments = arrayOf(
                VkAttachmentDescription(
                    format = DEPTH_FORMAT,
                    storeOp = VkAttachmentStoreOp.STORE,
                    initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
                    // Vulkan performs the DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> SHADER_READ_ONLY
                    // transition implicitly at render-pass end (unlike OffscreenRenderTarget's
                    // color image, which needs a manual barrier). The write-before-sample
                    // ordering is the outgoing dependency below, not a fence.
                    finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                ),
            ),
            pSubpasses = arrayOf(
                VkSubpassDescription(
                    pipelineBindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pDepthStencilAttachment = arrayOf(
                        VkAttachmentReference(
                            attachment = 0,
                            layout = VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                        ),
                    ),
                ),
            ),
            pDependencies = arrayOf(
                VkSubpassDependency(
                    srcSubpass = VK_SUBPASS_EXTERNAL,
                    dstSubpass = 0,
                    srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value or
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
                    srcAccessMask = 0,
                    dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value or
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
                    dstAccessMask = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
                ),
                // Depth written here, sampled by the scene pass's fragment shader. That ordering
                // used to come from draining the GPU on a fence between the two passes, which
                // cost a full CPU round-trip every frame to express a dependency the GPU can
                // honour by itself. Declared here, both passes record into one command buffer and
                // the CPU never waits.
                VkSubpassDependency(
                    srcSubpass = 0,
                    dstSubpass = VK_SUBPASS_EXTERNAL,
                    srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
                    srcAccessMask = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
                    dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value,
                    dstAccessMask = VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value,
                ),
            ),
        ),
    )

    fun destroy() {
        Vulkan.vkDestroyFramebuffer(device, framebuffer)
        VulkanImages.vkDestroySampler(device, sampler)
        Vulkan.vkDestroyImageView(device, imageView)
        VulkanImages.vkDestroyImage(device, image)
        VulkanBuffers.vkFreeMemory(device, imageMemory)
        VulkanDescriptors.vkDestroyDescriptorPool(device, descriptorPool)
        VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout)
        Vulkan.vkDestroyRenderPass(device, renderPass)
    }

    companion object {
        /** 2048x2048 -- enough resolution for this demo's grid-sized scene without being a
         * real memory/bandwidth cost (a single D32 depth image, ~16MB). */
        const val DEFAULT_SIZE = 2048
        val DEPTH_FORMAT = VkFormat.VK_FORMAT_D32_SFLOAT
    }
}
