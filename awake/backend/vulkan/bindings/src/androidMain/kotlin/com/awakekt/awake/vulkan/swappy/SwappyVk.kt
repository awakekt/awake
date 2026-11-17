/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swappy

/**
 * Swappy, Android's frame pacer from `games-frame-pacing`, over Vulkan (#1699). Each call is one
 * `SwappyVk_*` function of Swappy's C API; handles are the raw Vulkan handles as `Long`.
 *
 * Nothing in the engine calls this yet: the Android frame-pacing setting that switches between
 * sleeping and Swappy, with sleeping the default, arrives in #1699's next change. Until a device
 * shows Swappy pacing evenly, it is not proven beyond linking.
 */
object SwappyVk {
    init {
        // The same library `Vulkan` loads: these entry points live beside its own.
        System.loadLibrary("awake-vulkan")
    }

    /**
     * The device extensions Swappy wants enabled, out of [available]: pass them to
     * `vkCreateDevice` with the engine's own.
     */
    external fun determineDeviceExtensions(physicalDevice: Long, available: Array<String>): Array<String>

    /** Tells Swappy which family [queue], the one that presents, belongs to. */
    external fun setQueueFamilyIndex(device: Long, queue: Long, queueFamilyIndex: Int)

    /**
     * Starts Swappy for [swapchain] and returns the display's refresh period in nanoseconds, or 0
     * when Swappy could not start. [activity] is the `android.app.Activity` showing the surface.
     */
    external fun initAndGetRefreshCycleDuration(activity: Any, physicalDevice: Long, device: Long, swapchain: Long): Long

    /**
     * Gives Swappy the window [swapchain] presents to, from its `android.view.Surface`. The window
     * is held until [destroySwapchain].
     */
    external fun setWindow(device: Long, swapchain: Long, surface: Any)

    /** The interval Swappy presents [swapchain] at, in nanoseconds: a whole number of refresh periods. */
    external fun setSwapIntervalNs(device: Long, swapchain: Long, swapIntervalNs: Long)

    /** Makes Swappy forget [swapchain], before the swapchain itself is destroyed. */
    external fun destroySwapchain(device: Long, swapchain: Long)

    /**
     * Whether a present goes through `SwappyVk_queuePresent` rather than `vkQueuePresentKHR`. Its
     * result is read as before: suboptimal and out-of-date are answered, other errors throw.
     */
    external fun setPresentsThroughSwappy(enabled: Boolean)
}
