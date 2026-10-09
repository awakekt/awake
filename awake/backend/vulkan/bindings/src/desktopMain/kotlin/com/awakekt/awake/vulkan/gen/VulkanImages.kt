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

// Phase 1b (desktop native build) has not landed yet — see docs/mvp-plan.md.
/**
 * Desktop JVM actual of [VulkanImages]. Each function is a JNI `external` declaration backed by the
 * generated wrapper in the `awake-vulkan` library: a zero handle throws [IllegalStateException] and
 * a failed Vulkan call throws a [com.awakekt.awake.vulkan.utils.VkResultException]. The library is
 * loaded by [com.awakekt.awake.vulkan.Vulkan], so use that first.
 */
actual object VulkanImages {
    /**
     * Creates an image, two-dimensional unless [createInfo] says otherwise. It has no memory until
     * one is bound with [vkBindImageMemory].
     */
    actual external fun vkCreateImage(device: Long, createInfo: VkImageCreateInfo): Long

    /**
     * Destroys an image. It must no longer be used by pending GPU work, and its memory is freed
     * separately.
     */
    actual external fun vkDestroyImage(device: Long, image: Long)

    /**
     * Returns the size, alignment and acceptable memory types an image needs from its backing
     * memory.
     */
    actual external fun vkGetImageMemoryRequirements(device: Long, image: Long): VkMemoryRequirements

    /**
     * Binds a region of device memory to an image. This can be done once per image, before its
     * first use.
     */
    actual external fun vkBindImageMemory(device: Long, image: Long, memory: Long, memoryOffset: Long)

    /**
     * Creates a sampler, the filtering, addressing and comparison state a shader reads textures
     * with.
     */
    actual external fun vkCreateSampler(device: Long, createInfo: VkSamplerCreateInfo): Long

    /** Destroys a sampler. It must no longer be used by pending GPU work. */
    actual external fun vkDestroySampler(device: Long, sampler: Long)

    /**
     * Transitions `image`'s layout in `commandBuffer` between the plain-`Int`
     * [com.awakekt.awake.vulkan.models.info.VkImageLayout2] values `oldLayout`/`newLayout`.
     * `levelCount` defaults to `1` (every pre-existing caller is single-mip); a multi-mip
     * [com.awakekt.awake.vulkan.texture.Texture] must pass its real level count --
     * `VK_REMAINING_MIP_LEVELS` silently transitions only level 0 on MoltenVK.
     */
    @JniNative("awake_vulkan_images_transition_image_layout")
    actual external fun vkTransitionImageLayout(
        commandBuffer: Long,
        image: Long,
        oldLayout: Int,
        newLayout: Int,
        levelCount: Int,
        layerCount: Int,
    )

    /**
     * Records a global memory barrier: [srcAccessMask] writes in [srcStageMask] recorded before it
     * are made visible to [dstAccessMask] in [dstStageMask] after it.
     *
     * For orderings a render pass already declares as a `VkSubpassDependency`: MoltenVK up to 1.4.1
     * never encodes those as Metal fences, and only an explicit barrier orders its encoders.
     */
    @JniNative("awake_vulkan_images_cmd_memory_barrier")
    actual external fun vkCmdMemoryBarrier(
        commandBuffer: Long,
        srcStageMask: Int,
        srcAccessMask: Int,
        dstStageMask: Int,
        dstAccessMask: Int,
    )

    /**
     * Records a copy of texel data from a buffer into the image region described by [copy]. The
     * image must be in `TRANSFER_DST_OPTIMAL` layout.
     */
    actual external fun vkCmdCopyBufferToImage(
        commandBuffer: Long,
        srcBuffer: Long,
        dstImage: Long,
        copy: VkBufferImageCopy,
    )

    /**
     * Inverse of [vkCmdCopyBufferToImage] -- records into `commandBuffer` a copy of `srcImage`
     * (expected in `TRANSFER_SRC_OPTIMAL` layout) into `dstBuffer` per `copy`'s region, for
     * offscreen render-target CPU readback (`Renderer.readPixels`).
     */
    actual external fun vkCmdCopyImageToBuffer(
        commandBuffer: Long,
        srcImage: Long,
        dstBuffer: Long,
        copy: VkBufferImageCopy,
    )
}
