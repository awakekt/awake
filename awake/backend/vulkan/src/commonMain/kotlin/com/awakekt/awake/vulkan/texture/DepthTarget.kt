/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.vulkan.VK_SUBPASS_EXTERNAL
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkAttachmentStoreOp
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageLayout
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkAttachmentReference
import com.awakekt.awake.vulkan.models.VkSubpassDependency
import com.awakekt.awake.vulkan.models.info.VkCompareOp2
import com.awakekt.awake.vulkan.models.info.VkDescriptorImageInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolSize
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerAddressMode
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSubpassDescription
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding

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
 * [comparison] decides how [sampler] reads: a hardware comparison sampler (LessEqual,
 * linear-filtered -- the GPU compares each texel BEFORE filtering, so the blend is over 0/1
 * comparison results and is meaningful) for the shadow map, or a plain `NEAREST` sampler for a
 * consumer that wants raw depth values, where linear filtering would blend across a depth
 * discontinuity and mean nothing.
 */
class DepthTarget(
    graphicsDevice: GraphicsDevice,
    val size: Int = DEFAULT_SIZE,
    /**
     * How many layers the image holds -- one shadow cascade each.
     *
     * Rendering targets ONE layer at a time ([framebufferFor]) while sampling reads the whole
     * array through [imageView]. That split is the reason cascades are layers rather than tiles
     * of one big map: a tile needs gutters and UV arithmetic to stop filtering bleeding between
     * neighbours, and a layer cannot bleed into another by construction.
     */
    val layers: Int = 1,
    /**
     * Whether [imageView] is an ARRAY view.
     *
     * Not derived from `layers > 1`: a shader declares `texture_depth_2d` or
     * `texture_depth_2d_array` at compile time, and a one-cascade configuration must still bind
     * an array view to a shader that declares one. Deriving it would make a cascade count of 1
     * fail at bind time with a message about view dimensions.
     */
    val arrayed: Boolean = false,
    /**
     * Whether [sampler] is a comparison sampler. A flag, not the default, because this class
     * also serves the scene-depth target: `depth_fog` declares a plain `sampler` and reads raw
     * depth, which a comparison sampler cannot produce.
     */
    private val comparison: Boolean = false,
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

    /** Layer 0's framebuffer -- the only one a single-layer target has. */
    val framebuffer: Long get() = framebuffers[0]

    /** One per layer, each attaching that layer alone. */
    private var framebuffers: LongArray = LongArray(0)

    /** Per-layer views, kept for teardown; [framebuffers] reference them without owning them. */
    private var layerViews: LongArray = LongArray(0)

    /** The framebuffer that renders into [layer] -- a cascade's own slot. */
    fun framebufferFor(layer: Int): Long = framebuffers[layer]

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
                arrayLayers = layers,
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
                viewType = if (arrayed) {
                    VkImageViewType.VK_IMAGE_VIEW_TYPE_2D_ARRAY
                } else {
                    VkImageViewType.VK_IMAGE_VIEW_TYPE_2D
                },
                format = DEPTH_FORMAT,
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value,
                    baseMipLevel = 0,
                    levelCount = 1,
                    baseArrayLayer = 0,
                    layerCount = layers,
                ),
            ),
        )
        sampler = VulkanImages.vkCreateSampler(
            device,
            VkSamplerCreateInfo(
                magFilter = if (comparison) VkFilter.VK_FILTER_LINEAR else VkFilter.VK_FILTER_NEAREST,
                minFilter = if (comparison) VkFilter.VK_FILTER_LINEAR else VkFilter.VK_FILTER_NEAREST,
                addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                compareEnable = comparison,
                compareOp = VkCompareOp2.VK_COMPARE_OP_LESS_OR_EQUAL,
            ),
        )
        // A framebuffer attaches ONE layer, so each cascade needs its own view and its own
        // framebuffer -- an array view here would render every layer at once through multiview,
        // which is a different feature with its own extension.
        layerViews = LongArray(layers) { layer ->
            Vulkan.vkCreateImageView(
                device,
                VkImageViewCreateInfo(
                    image = image,
                    viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                    format = DEPTH_FORMAT,
                    subresourceRange = VkImageSubresourceRange(
                        aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value,
                        baseMipLevel = 0,
                        levelCount = 1,
                        baseArrayLayer = layer,
                        layerCount = 1,
                    ),
                ),
            )
        }
        framebuffers = LongArray(layers) { layer ->
            Vulkan.vkCreateFramebuffer(
                device,
                VkFramebufferCreateInfo(
                    renderPass = renderPass,
                    pAttachments = arrayOf(layerViews[layer]),
                    width = size,
                    height = size,
                    layers = 1,
                ),
            )
        }
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
        framebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
        layerViews.forEach { Vulkan.vkDestroyImageView(device, it) }
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
