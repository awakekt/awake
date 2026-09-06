/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import com.awakekt.awake.engine.platform.config.PresentMode
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkColorSpaceKHR
import com.awakekt.awake.vulkan.enums.VkComponentSwizzle
import com.awakekt.awake.vulkan.enums.VkCompositeAlphaFlagBitsKHR
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageUsageFlagBits
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.VkPresentModeKHR
import com.awakekt.awake.vulkan.enums.VkSharingMode
import com.awakekt.awake.vulkan.enums.flags.VkFenceCreateFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.has
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkSurfaceCapabilitiesKHR
import com.awakekt.awake.vulkan.models.VkSurfaceFormatKHR
import com.awakekt.awake.vulkan.models.info.VkComponentMapping
import com.awakekt.awake.vulkan.models.info.VkFenceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkSemaphoreCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSwapchainCreateInfoKHR
import com.awakekt.awake.vulkan.utils.findQueueFamilies
import com.awakekt.awake.vulkan.utils.querySwapChainSupport

/**
 * Phase 2 (renderer abstraction): owns the swapchain (images, image views, format, extent),
 * per-frame-in-flight acquire/fence synchronization, and per-swapchain-image present
 * semaphores -- extracted
 * verbatim from `VulkanApplication`'s `swapChain`/`createImageViews`/`chooseSwap*`/
 * `cleanSwapChain`/`createSyncObjects` functions and their backing fields. Same calls, same
 * order, same synchronization ownership.
 *
 * Framebuffers are deliberately NOT owned here: they also depend on the render pass and
 * depth image view, neither of which is extracted yet -- `VulkanApplication` still owns
 * those and must destroy its framebuffers before calling [destroy].
 */
class SwapchainManager(
    graphicsDevice: GraphicsDevice,
    val maxFramesInFlight: Int,
    private val surfaceExtentProvider: (() -> VkExtent2D?)? = null,
    /** What the app asked for. What it got is [selectedPresentMode]. */
    private val presentPreference: PresentMode = PresentMode.Auto,
) {
    private val graphicsDevice = graphicsDevice
    private val physicalDevice get() = graphicsDevice.physicalDevice
    private val device get() = graphicsDevice.device
    private val surface get() = graphicsDevice.surface

    var swapChain: Long = 0
    var extent: VkExtent2D = VkExtent2D()
    var imageViews: List<Long> = emptyList()
    var imageFormat = VkFormat.VK_FORMAT_UNDEFINED

    /**
     * The mode the surface actually gave us, which is not always the one requested.
     *
     * Worth reading before trusting a frame-time number: under FIFO a frame is capped at the
     * refresh interval, so "17 ms" can be 3 ms of work and 14 ms of waiting for the display.
     */
    var selectedPresentMode: VkPresentModeKHR = VkPresentModeKHR.VK_PRESENT_MODE_FIFO_KHR
        private set

    val imageAvailableSemaphores = LongArray(maxFramesInFlight)
    var renderFinishedSemaphores = LongArray(0)

    /** The stand-in images [createHeadlessPresentable] allocated; empty for a real swapchain. */
    var headlessImages = LongArray(0)
        private set
    private var headlessImageMemory = LongArray(0)
    val inFlightFences = LongArray(maxFramesInFlight)
    internal var imagesInFlight = LongArray(0)
    var currentFrame = 0

    fun create() {
        val (capabilities, formats, presentModes) = querySwapChainSupport(physicalDevice, surface)
        val (format, colorSpace) = chooseSwapSurfaceFormat(formats)
        val presentMode = chooseSwapPresentMode(presentModes, presentPreference)
        selectedPresentMode = presentMode
        val chosenExtent = chooseSwapExtent(capabilities)

        val imageCount = chooseSwapchainImageCount(capabilities)

        val indices = findQueueFamilies(physicalDevice, surface)
        var queueFamilyIndices: Array<Int>? =
            arrayOf(indices.graphicsFamily!!, indices.presentFamily!!)
        val imageSharingMode: VkSharingMode
        if (indices.graphicsFamily != indices.presentFamily) {
            imageSharingMode = VkSharingMode.VK_SHARING_MODE_CONCURRENT
        } else {
            imageSharingMode = VkSharingMode.VK_SHARING_MODE_EXCLUSIVE
            queueFamilyIndices = null
        }
        val compositeAlpha =
            if (capabilities.supportedCompositeAlpha has VkCompositeAlphaFlagBitsKHR.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) {
                VkCompositeAlphaFlagBitsKHR.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
            } else if (capabilities.supportedCompositeAlpha has VkCompositeAlphaFlagBitsKHR.VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR) {
                VkCompositeAlphaFlagBitsKHR.VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
            } else {
                throw Exception("No valid compositeAlpha found")
            }

        val imageUsage =
            if (capabilities.supportedUsageFlags has VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) {
                VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value
            } else {
                throw Exception("No valid usage flags found")
            }

        val preTransform = capabilities.currentTransform

        val createInfo = VkSwapchainCreateInfoKHR(
            surface = surface,
            minImageCount = imageCount,
            imageFormat = format,
            imageColorSpace = colorSpace,
            imageExtent = chosenExtent,
            imageArrayLayers = 1,
            imageUsage = imageUsage,
            imageSharingMode = imageSharingMode,
            pQueueFamilyIndices = queueFamilyIndices?.toIntArray(),
            preTransform = preTransform,
            compositeAlpha = compositeAlpha,
            presentMode = presentMode,
            clipped = true,
            oldSwapchain = swapChain,
        )
        imageFormat = format
        swapChain = Vulkan.vkCreateSwapchainKHR(device, createInfo)
        extent = chosenExtent
        createImageViews()
        imagesInFlight = LongArray(imageViews.size)
        renderFinishedSemaphores = LongArray(imageViews.size) {
            Vulkan.vkCreateSemaphore(device, VkSemaphoreCreateInfo())
        }
    }

    /** Desktop-only headless stand-in for [create] (see `GraphicsDevice.createHeadless`'s doc
     * comment) -- a headless [GraphicsDevice] has no `VkSurfaceKHR`, so there is no real
     * `VkSwapchainKHR` to query/create here. Just fixes [imageFormat]/[extent] to the values
     * [RenderPipeline][com.awakekt.awake.vulkan.pipeline.RenderPipeline]/
     * [OffscreenRenderTarget][com.awakekt.awake.vulkan.texture.OffscreenRenderTarget]
     * read off this manager (both only ever read a format/extent value, never touch
     * [swapChain]/[imageViews] directly). [imageViews] stays empty -- nothing on the headless
     * path (`Renderer.renderToTexture`/`readPixels`) ever indexes into it; only `Renderer.draw`
     * (never called headless) does. */
    /**
     * Headless WITH images, so the on-screen path can run: acquire, record, submit, read back.
     *
     * [createHeadless] leaves [imageViews] empty, which is right for `renderToTexture` and wrong
     * for `Renderer.draw` -- that one needs something to render into. This allocates images that
     * stand in for a presentation engine's, so an app's real frame loop runs without a display
     * and the frame it produces can be inspected. Nothing presents them; [readImagePixels] copies
     * one back instead.
     *
     * Separate from [createHeadless] rather than a flag on it: every existing headless fixture
     * depends on that one creating no images and no framebuffers, and this is opt-in for the
     * engine's own headless boot.
     */
    fun createHeadlessPresentable(
        width: Int,
        height: Int,
        format: VkFormat = VkFormat.VK_FORMAT_R8G8B8A8_UNORM,
    ) {
        imageFormat = format
        extent = VkExtent2D(width, height)
        headlessImages = LongArray(HEADLESS_IMAGE_COUNT) {
            VulkanImages.vkCreateImage(
                device,
                VkImageCreateInfo(
                    width = width,
                    height = height,
                    format = format.value,
                    usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or
                        VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                ),
            )
        }
        headlessImageMemory = LongArray(HEADLESS_IMAGE_COUNT) { index ->
            val requirements = VulkanImages.vkGetImageMemoryRequirements(device, headlessImages[index])
            val memory = VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(
                    allocationSize = requirements.size,
                    memoryTypeIndex = VulkanBuffers.findMemoryType(
                        physicalDevice,
                        requirements.memoryTypeBits,
                        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    ),
                ),
            )
            VulkanImages.vkBindImageMemory(device, headlessImages[index], memory, 0)
            memory
        }
        imageViews = headlessImages.map { image ->
            Vulkan.vkCreateImageView(
                device,
                VkImageViewCreateInfo(
                    image = image,
                    viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                    format = format,
                    subresourceRange = VkImageSubresourceRange(
                        aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
                        baseMipLevel = 0,
                        levelCount = 1,
                        baseArrayLayer = 0,
                        layerCount = 1,
                    ),
                ),
            )
        }
        imagesInFlight = LongArray(imageViews.size)
        renderFinishedSemaphores = LongArray(imageViews.size) {
            Vulkan.vkCreateSemaphore(device, VkSemaphoreCreateInfo())
        }
    }

    /** Whether this manager stands in for a presentation engine rather than owning one. */
    val isHeadlessPresentable: Boolean get() = swapChain == 0L && imageViews.isNotEmpty()

    fun createHeadless(width: Int, height: Int, format: VkFormat = VkFormat.VK_FORMAT_R8G8B8A8_UNORM) {
        imageFormat = format
        extent = VkExtent2D(width, height)
        imagesInFlight = LongArray(imageViews.size)
        renderFinishedSemaphores = LongArray(0)
    }

    private fun createImageViews() {
        val swapChainImages = Vulkan.vkGetSwapchainImagesKHR(device, swapChain)
        imageViews = swapChainImages.map { swapChainImage ->
            val createInfo = VkImageViewCreateInfo(
                image = swapChainImage,
                viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                format = imageFormat,
                components = VkComponentMapping(
                    VkComponentSwizzle.VK_COMPONENT_SWIZZLE_R,
                    VkComponentSwizzle.VK_COMPONENT_SWIZZLE_G,
                    VkComponentSwizzle.VK_COMPONENT_SWIZZLE_B,
                    VkComponentSwizzle.VK_COMPONENT_SWIZZLE_A,
                ),
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
                    baseMipLevel = 0,
                    levelCount = 1,
                    baseArrayLayer = 0,
                    layerCount = 1,
                ),
            )
            Vulkan.vkCreateImageView(device, createInfo)
        }
    }

    /** Deliberately prefers `_UNORM` formats, not `_SRGB` -- every [com.awakekt
     * .awake.core.color.Color] this engine produces (`ShadcnTheme`'s OKLCH palette,
     * every widget's authored color) is already gamma-encoded sRGB bytes by the time it
     * reaches a draw call (see `OklchColor.toSrgbChannel()`). An `_SRGB` swapchain format
     * makes the GPU apply ITS OWN linear->sRGB encoding on write, double-encoding colors that
     * are already sRGB-encoded -- this washed every dark/mid-tone color toward gray (a
     * should-be-near-black `(10,10,10)` foreground rendered as `(56,56,56)`, confirmed by
     * sampling a real screenshot), while leaving pure black/white untouched (both endpoints
     * are fixed points of gamma encoding) -- exactly the "pale UI, colors look washed out"
     * symptom this fixes. `colorSpace` stays `SRGB_NONLINEAR_KHR` (unchanged) so the display
     * still presents the swapchain contents as sRGB, which they genuinely are now that
     * there's exactly one gamma-encoding step (software), not two. */
    internal fun chooseSwapExtent(capabilities: VkSurfaceCapabilitiesKHR): VkExtent2D {
        if (capabilities.currentExtent.width != Int.MAX_VALUE) {
            return capabilities.currentExtent
        }
        val surfaceExtent = requireNotNull(surfaceExtentProvider?.invoke()) {
            "Surface reported a variable swapchain extent, but no framebuffer extent provider was supplied."
        }
        require(surfaceExtent.width > 0 && surfaceExtent.height > 0) {
            "Surface framebuffer extent must be non-zero, was ${surfaceExtent.width}x${surfaceExtent.height}."
        }
        val actualWidth =
            surfaceExtent.width.coerceIn(capabilities.minImageExtent.width, capabilities.maxImageExtent.width)
        val actualHeight =
            surfaceExtent.height.coerceIn(capabilities.minImageExtent.height, capabilities.maxImageExtent.height)
        return VkExtent2D(actualWidth, actualHeight)
    }

    /** A variable-extent surface has no drawable while its native framebuffer is zero-sized
     * (for example, a minimized GLFW window or a temporarily unavailable CAMetalLayer).
     * Recreating the swapchain then would fail, so the renderer defers it until a later frame.
     * Platforms with a fixed surface extent do not need this preflight. */
    fun hasDrawableExtent(): Boolean = surfaceExtentProvider?.invoke()?.let {
        it.width > 0 && it.height > 0
    } ?: true

    internal fun chooseSwapchainImageCount(capabilities: VkSurfaceCapabilitiesKHR): Int {
        val requestedImageCount = capabilities.minImageCount + 1
        return if (capabilities.maxImageCount == 0) {
            requestedImageCount.coerceAtLeast(1)
        } else {
            requestedImageCount.coerceIn(1, capabilities.maxImageCount)
        }
    }

    /** Destroys the image views + swapchain itself. Framebuffers are the caller's
     * responsibility (see class doc comment) and must be destroyed BEFORE this. */
    fun destroy() {
        renderFinishedSemaphores.forEach { semaphore ->
            Vulkan.vkDestroySemaphore(device, semaphore)
        }
        renderFinishedSemaphores = LongArray(0)
        imageViews.forEach { imageView ->
            Vulkan.vkDestroyImageView(device, imageView)
        }
        // Stand-in images own their memory; a real swapchain's belong to the presentation engine.
        headlessImages.forEach { VulkanImages.vkDestroyImage(device, it) }
        headlessImageMemory.forEach { VulkanBuffers.vkFreeMemory(device, it) }
        headlessImages = LongArray(0)
        headlessImageMemory = LongArray(0)
        Vulkan.vkDestroySwapchainKHR(device, swapChain)
    }

    fun createSyncObjects() {
        val semaphoreInfo = VkSemaphoreCreateInfo()
        val fenceInfo = VkFenceCreateInfo(
            flags = VkFenceCreateFlagBits.VK_FENCE_CREATE_SIGNALED_BIT.value,
        )

        for (i in 0 until maxFramesInFlight) {
            imageAvailableSemaphores[i] = Vulkan.vkCreateSemaphore(device, semaphoreInfo)
            inFlightFences[i] = Vulkan.vkCreateFence(device, fenceInfo)
        }
    }

    fun destroySyncObjects() {
        repeat(maxFramesInFlight) { index ->
            Vulkan.vkDestroySemaphore(device, imageAvailableSemaphores[index])
            Vulkan.vkDestroyFence(device, inFlightFences[index])
        }
    }
}

/** Picks the surface format the swapchain images will use, preferring an SRGB-nonlinear
 * colour space in one of the common byte orders -- extracted from [SwapchainManager] because
 * it reads only [availableFormats], no instance state. */
private fun chooseSwapSurfaceFormat(availableFormats: List<VkSurfaceFormatKHR>): VkSurfaceFormatKHR {
    require(availableFormats.isNotEmpty()) { "AvailableFormats must not be empty." }
    val preferedFormats = listOf(
        VkFormat.VK_FORMAT_R8G8B8A8_UNORM,
        VkFormat.VK_FORMAT_B8G8R8A8_UNORM,
        VkFormat.VK_FORMAT_A8B8G8R8_UNORM_PACK32,
    )
    return availableFormats.find { surfaceFormat ->
        preferedFormats.contains(surfaceFormat.format) &&
            surfaceFormat.colorSpace == VkColorSpaceKHR.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
    } ?: availableFormats.first()
}

/** Picks the swapchain's present mode, preferring mailbox (low-latency triple buffering)
 * over FIFO's guaranteed-but-blocking vsync -- extracted from [SwapchainManager] for the same
 * reason as [chooseSwapSurfaceFormat]: no instance state read. */
internal fun chooseSwapPresentMode(
    availablePresetModes: List<VkPresentModeKHR>,
    preference: PresentMode,
): VkPresentModeKHR {
    require(availablePresetModes.isNotEmpty()) { "AvailablePresetModes must not be empty." }
    // FIFO is the only mode the spec requires every implementation to support, so it is the
    // fallback for every request. MoltenVK in particular does not always offer mailbox, which is
    // how an app that asked not to be capped ends up capped anyway -- hence `selectedPresentMode`
    // rather than assuming the request was honoured.
    val wanted = when (preference) {
        PresentMode.Auto, PresentMode.LowLatency -> VkPresentModeKHR.VK_PRESENT_MODE_MAILBOX_KHR
        PresentMode.NoVsync -> VkPresentModeKHR.VK_PRESENT_MODE_IMMEDIATE_KHR
        PresentMode.Vsync -> VkPresentModeKHR.VK_PRESENT_MODE_FIFO_KHR
    }
    availablePresetModes.find { it == wanted }?.let { return it }
    // An explicit "do not cap me" falls to the other uncapped mode before it gives up and caps.
    if (preference == PresentMode.NoVsync) {
        availablePresetModes.find { it == VkPresentModeKHR.VK_PRESENT_MODE_MAILBOX_KHR }
            ?.let { return it }
    }
    return VkPresentModeKHR.VK_PRESENT_MODE_FIFO_KHR
}

/** Two, like a double-buffered swapchain: enough for the frame loop's in-flight logic. */
private const val HEADLESS_IMAGE_COUNT = 2
