/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import com.awakekt.awake.core.host.FrameRateMode

/**
 * What paces presents beside the frame loop, told about the device and every swapchain it lives
 * with (#1699): Swappy on Android when an app picks it, nothing elsewhere. Every call comes from the
 * render thread.
 */
internal interface SwapchainPacer {
    /** Device extensions the pacer wants enabled with the engine's own, out of [available]. */
    fun deviceExtensions(physicalDevice: Long, available: Set<String>): List<String>

    /** The device was created; [presentQueue] belongs to [presentFamily]. */
    fun deviceCreated(physicalDevice: Long, device: Long, presentQueue: Long, presentFamily: Int)

    /** The window, an `android.view.Surface` on Android, the next swapchain presents to; null when it is released. */
    fun windowChanged(window: Any?)

    /** A swapchain was just created. */
    fun swapchainCreated(swapchain: Long)

    /** [swapchain] is about to be destroyed: forget it first. */
    fun swapchainDestroying(swapchain: Long)

    /** A frame is about to present: apply a change of pacer or of frame-rate mode now. */
    fun beforePresent()

    /** The device is about to be destroyed: forget it, and give up pacing. */
    fun deviceDestroying()
}

/**
 * Swappy's Vulkan calls as [SwappyPacer] makes them, one for each `SwappyVk_*` function, so its
 * order is tested against a fake. Handles are raw Vulkan handles.
 */
internal interface SwappyCalls {
    fun determineDeviceExtensions(physicalDevice: Long, available: Array<String>): Array<String>

    fun setQueueFamilyIndex(device: Long, queue: Long, queueFamilyIndex: Int)

    /** The display's refresh period in nanoseconds, or 0 when Swappy could not start. */
    fun initAndGetRefreshCycleDuration(activity: Any, physicalDevice: Long, device: Long, swapchain: Long): Long

    fun setWindow(device: Long, swapchain: Long, window: Any)

    fun setSwapIntervalNs(device: Long, swapchain: Long, swapIntervalNs: Long)

    fun destroySwapchain(device: Long, swapchain: Long)

    fun destroyDevice(device: Long)

    /** Whether presents go through `SwappyVk_queuePresent` rather than `vkQueuePresentKHR`. */
    fun setPresentsThroughSwappy(enabled: Boolean)
}

/**
 * Where [SwappyPacer] reads what the app chose and tells the platform what it did: the frame loop
 * and the window, kept apart from Swappy's own calls.
 */
internal interface SwappyHost {
    /** Whether the app asks for Swappy now. */
    val swappySelected: Boolean

    /** The frame-rate mode frames run at now. */
    val frameRateMode: FrameRateMode

    /** The activity Swappy reads the display through, or null when there is none. */
    val activity: Any?

    /** Whether the present call paces frames, so the frame loop must not also sleep them. */
    fun setPresentPacing(enabled: Boolean)

    /**
     * The fixed rate [window] shows, [fps], so the display can switch to a rate that divides it;
     * 0 takes the preference away.
     */
    fun setWindowFrameRate(window: Any, fps: Float)
}

/** The pacer this platform runs for [window] (#1699), or null where nothing paces presents. */
internal expect fun platformSwapchainPacer(window: Any, configuredMode: () -> FrameRateMode): SwapchainPacer?
