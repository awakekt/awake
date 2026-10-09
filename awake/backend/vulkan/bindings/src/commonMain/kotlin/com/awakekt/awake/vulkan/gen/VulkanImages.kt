/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.JniNative
import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkBufferImageCopy
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo

/**
 * Phase 1d image/sampler API surface, same jni-binding-generator `.gen` package/pipeline as
 * [VulkanBuffers]/[VulkanDescriptors]. `vkTransitionImageLayout` is deliberately narrow --
 * only the specific transitions actual callers need (texture upload's UNDEFINED ->
 * TRANSFER_DST -> SHADER_READ_ONLY, plus offscreen render-target readback/compositing's
 * COLOR_ATTACHMENT_OPTIMAL <-> SHADER_READ_ONLY_OPTIMAL <-> TRANSFER_SRC_OPTIMAL round trip)
 * rather than exposing a fully generic `VkImageMemoryBarrier` -- the same simplification
 * vulkan-tutorial.com's own reference implementation uses, since the correct
 * `srcAccessMask`/`dstAccessMask`/pipeline-stage combination for every possible layout pair
 * is a large lookup table this MVP doesn't need yet. Generalize further if/when a
 * transition outside these five is actually needed.
 */
expect object VulkanImages {
    /**
     * Creates an image, two-dimensional unless [createInfo] says otherwise. It has no memory until
     * one is bound with [vkBindImageMemory].
     *
     * @param device The logical device to create the image on.
     * @param createInfo The size, format, usage, tiling, mip levels and array layers.
     * @return The new `VkImage` handle.
     */
    fun vkCreateImage(device: Long, createInfo: VkImageCreateInfo): Long

    /**
     * Destroys an image. It must no longer be used by pending GPU work, and its memory is freed
     * separately.
     *
     * @param device The logical device that created the image.
     * @param image The image to destroy.
     */
    fun vkDestroyImage(device: Long, image: Long)

    /**
     * Returns the size, alignment and acceptable memory types an image needs from its backing
     * memory.
     *
     * @param device The logical device that owns the image.
     * @param image The image to query.
     * @return The requirements; `memoryTypeBits` has bit `i` set when memory type `i` is usable.
     */
    fun vkGetImageMemoryRequirements(device: Long, image: Long): VkMemoryRequirements

    /**
     * Binds a region of device memory to an image. This can be done once per image, before its
     * first use.
     *
     * @param device The logical device that owns both objects.
     * @param image The image to back.
     * @param memory The memory to bind, of a type allowed by the image's requirements.
     * @param memoryOffset Byte offset into [memory], a multiple of the image's alignment.
     */
    fun vkBindImageMemory(device: Long, image: Long, memory: Long, memoryOffset: Long)

    /**
     * Creates a sampler, the filtering, addressing and comparison state a shader reads textures
     * with.
     *
     * @param device The logical device to create the sampler on.
     * @param createInfo The filters, address modes, anisotropy, border colour, LOD range and
     * compare state.
     * @return The new `VkSampler` handle.
     */
    fun vkCreateSampler(device: Long, createInfo: VkSamplerCreateInfo): Long

    /**
     * Destroys a sampler. It must no longer be used by pending GPU work.
     *
     * @param device The logical device that created the sampler.
     * @param sampler The sampler to destroy.
     */
    fun vkDestroySampler(device: Long, sampler: Long)

    /** Transitions `image`'s layout in `commandBuffer` between the plain-`Int`
     * [com.awakekt.awake.vulkan.models.info.VkImageLayout2] values `oldLayout`/`newLayout`.
     * `levelCount` defaults to `1` (every pre-existing caller is single-mip); a multi-mip
     * [com.awakekt.awake.vulkan.texture.Texture] must pass its real level count --
     * `VK_REMAINING_MIP_LEVELS` silently transitions only level 0 on MoltenVK. */
    @JniNative("awake_vulkan_images_transition_image_layout")
    fun vkTransitionImageLayout(
        commandBuffer: Long,
        image: Long,
        oldLayout: Int,
        newLayout: Int,
        levelCount: Int = 1,
        layerCount: Int = 1,
    )

    /**
     * Records a global memory barrier: [srcAccessMask] writes in [srcStageMask] recorded before
     * it are made visible to [dstAccessMask] in [dstStageMask] after it.
     *
     * For orderings a render pass already declares as a `VkSubpassDependency`: MoltenVK up to
     * 1.4.1 never encodes those as Metal fences, and only an explicit barrier orders its encoders.
     */
    @JniNative("awake_vulkan_images_cmd_memory_barrier")
    fun vkCmdMemoryBarrier(
        commandBuffer: Long,
        srcStageMask: Int,
        srcAccessMask: Int,
        dstStageMask: Int,
        dstAccessMask: Int,
    )

    /**
     * Records a copy of texel data from a buffer into the image region described by [copy]. The
     * image must be in `TRANSFER_DST_OPTIMAL` layout.
     *
     * @param commandBuffer The command buffer being recorded.
     * @param srcBuffer The buffer holding the texels.
     * @param dstImage The image to copy into.
     * @param copy The buffer offset and row layout, and the mip level, array layers and size of the
     * image region.
     */
    fun vkCmdCopyBufferToImage(
        commandBuffer: Long,
        srcBuffer: Long,
        dstImage: Long,
        copy: VkBufferImageCopy,
    )

    /** Inverse of [vkCmdCopyBufferToImage] -- records into `commandBuffer` a copy of `srcImage`
     * (expected in `TRANSFER_SRC_OPTIMAL` layout) into `dstBuffer` per `copy`'s region, for
     * offscreen render-target CPU readback (`Renderer.readPixels`). */
    fun vkCmdCopyImageToBuffer(
        commandBuffer: Long,
        srcImage: Long,
        dstBuffer: Long,
        copy: VkBufferImageCopy,
    )
}
