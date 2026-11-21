/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import android.view.Surface
import com.awakekt.awake.core.host.AndroidFrameLoop
import com.awakekt.awake.core.host.AndroidFramePacing
import com.awakekt.awake.core.host.FrameRateMode
import com.awakekt.awake.core.host.SteadyRate
import com.awakekt.awake.vulkan.swappy.SwappyVk

/** Swappy for a window that is an Android `Surface`, run as [AndroidFrameLoop.framePacing] says (#1699). */
internal actual fun platformSwapchainPacer(window: Any, configuredMode: () -> FrameRateMode): SwapchainPacer? =
    if (window is Surface) SwappyPacer(SwappyVkCalls, AndroidSwappyHost(configuredMode)) else null

/** [SwappyCalls] as the JNI calls into Swappy they are. */
private object SwappyVkCalls : SwappyCalls {
    override fun determineDeviceExtensions(physicalDevice: Long, available: Array<String>) =
        SwappyVk.determineDeviceExtensions(physicalDevice, available)

    override fun setQueueFamilyIndex(device: Long, queue: Long, queueFamilyIndex: Int) =
        SwappyVk.setQueueFamilyIndex(device, queue, queueFamilyIndex)

    override fun initAndGetRefreshCycleDuration(activity: Any, physicalDevice: Long, device: Long, swapchain: Long) =
        SwappyVk.initAndGetRefreshCycleDuration(activity, physicalDevice, device, swapchain)

    override fun setWindow(device: Long, swapchain: Long, window: Any) = SwappyVk.setWindow(device, swapchain, window)

    override fun setSwapIntervalNs(device: Long, swapchain: Long, swapIntervalNs: Long) =
        SwappyVk.setSwapIntervalNs(device, swapchain, swapIntervalNs)

    override fun destroySwapchain(device: Long, swapchain: Long) = SwappyVk.destroySwapchain(device, swapchain)

    override fun destroyDevice(device: Long) = SwappyVk.destroyDevice(device)

    override fun setPresentsThroughSwappy(enabled: Boolean) = SwappyVk.setPresentsThroughSwappy(enabled)
}

/** The frame loop's settings, and the window's frame rate. */
private class AndroidSwappyHost(private val configuredMode: () -> FrameRateMode) : SwappyHost {
    override val swappySelected: Boolean get() = AndroidFrameLoop.framePacing == AndroidFramePacing.Swappy

    // The loop's live override wins, as it does for the loop's own pacing. With a steady rate in
    // effect, Swappy holds the rate the loop chose (#1699).
    override val frameRateMode: FrameRateMode
        get() = if (AndroidFrameLoop.steadyRate == SteadyRate.Off) {
            AndroidFrameLoop.frameRateOverride ?: configuredMode()
        } else {
            AndroidFrameLoop.frameRateModeInUse
        }

    override val activity: Any? get() = AndroidFrameLoop.pacingActivity?.get()

    override fun setPresentPacing(enabled: Boolean) {
        AndroidFrameLoop.presentPacing = enabled
    }

    override fun setWindowFrameRate(window: Any, fps: Float) {
        (window as? Surface)?.takeIf { it.isValid }?.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
    }
}
