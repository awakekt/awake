/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import com.awakekt.awake.core.host.FramePacing
import com.awakekt.awake.core.host.FrameRateMode

/**
 * Swappy pacing the swapchain while the app asks for it (#1699), and nothing at all while it does not:
 * with Sleep chosen, no Swappy call is made, from the device's creation to its destruction.
 *
 * With Swappy chosen, each swapchain is started on as it is created:
 * - the queue family is set once;
 * - Swappy starts with the activity;
 * - the window is set;
 * - the swap interval comes from the frame-rate mode;
 * - presents go through `SwappyVk_queuePresent`.
 *
 * Swappy forgets a swapchain before it is destroyed, and the device before it is. A cap also fixes
 * the window's frame rate, so the display can switch to one that divides it.
 *
 * The choice can change while the app runs. [beforePresent] starts Swappy on the current swapchain or
 * stops it there, with no new swapchain. The device extensions Swappy asks for are chosen when the
 * device is created: an app that switches to Swappy only later runs it without them, on Swappy's own
 * timing fallback.
 *
 * [FrameRateMode.Unlimited] is never paced, so Swappy steps aside for it and presents go straight to
 * `vkQueuePresentKHR`. [FrameRateMode.Auto] presents every refresh, and a cap every whole number of
 * refreshes that keeps under it ([FramePacing.swapIntervalNanos]). Swappy failing to start leaves the
 * frame loop sleeping, as with Sleep chosen.
 */
internal class SwappyPacer(private val calls: SwappyCalls, private val host: SwappyHost) : SwapchainPacer {
    private var physicalDevice = 0L
    private var device = 0L
    private var presentQueue = 0L
    private var presentFamily = -1
    private var familySet = false
    private var window: Any? = null
    private var swapchain = 0L

    /** Whether Swappy runs [swapchain]: started, and not yet stopped. */
    var active = false
        private set
    private var refreshNanos = 0L
    private var appliedMode: FrameRateMode? = null

    /** The swapchain Swappy last failed to start on: not tried again until the next one. */
    private var failedOn = 0L

    override fun deviceExtensions(physicalDevice: Long, available: Set<String>): List<String> =
        if (host.swappySelected) calls.determineDeviceExtensions(physicalDevice, available.toTypedArray()).toList() else emptyList()

    override fun deviceCreated(physicalDevice: Long, device: Long, presentQueue: Long, presentFamily: Int) {
        this.physicalDevice = physicalDevice
        this.device = device
        this.presentQueue = presentQueue
        this.presentFamily = presentFamily
    }

    override fun windowChanged(window: Any?) {
        this.window = window
    }

    override fun swapchainCreated(swapchain: Long) {
        this.swapchain = swapchain
        if (host.swappySelected) start()
    }

    override fun swapchainDestroying(swapchain: Long) {
        if (active && swapchain == this.swapchain) stop()
        if (swapchain == this.swapchain) this.swapchain = 0L
        if (swapchain == failedOn) failedOn = 0L
    }

    override fun beforePresent() {
        val wanted = host.swappySelected
        when {
            wanted && !active && failedOn != swapchain -> start()
            !wanted && active -> stop()
            active && host.frameRateMode != appliedMode -> apply(host.frameRateMode)
        }
    }

    override fun deviceDestroying() {
        if (active) stop()
        if (familySet) calls.destroyDevice(device)
        familySet = false
        device = 0L
    }

    private fun start() {
        val activity = host.activity
        val window = window
        val ready = swapchain != 0L && device != 0L
        if (!ready || activity == null || window == null) return
        if (!familySet) {
            calls.setQueueFamilyIndex(device, presentQueue, presentFamily)
            familySet = true
        }
        refreshNanos = calls.initAndGetRefreshCycleDuration(activity, physicalDevice, device, swapchain)
        if (refreshNanos <= 0L) {
            // Swappy forgets a swapchain it could not start on, and is tried again on the next one.
            calls.destroySwapchain(device, swapchain)
            failedOn = swapchain
            return
        }
        calls.setWindow(device, swapchain, window)
        active = true
        appliedMode = null
        apply(host.frameRateMode)
    }

    private fun apply(mode: FrameRateMode) {
        appliedMode = mode
        val paced = mode !is FrameRateMode.Unlimited
        if (paced) calls.setSwapIntervalNs(device, swapchain, swapIntervalNanos(mode))
        calls.setPresentsThroughSwappy(paced)
        host.setPresentPacing(paced)
        window?.let { host.setWindowFrameRate(it, if (mode is FrameRateMode.Capped) mode.targetFps.toFloat() else 0f) }
    }

    private fun swapIntervalNanos(mode: FrameRateMode): Long =
        if (mode is FrameRateMode.Capped) FramePacing.swapIntervalNanos(mode.targetFps, refreshNanos) else refreshNanos

    private fun stop() {
        calls.setPresentsThroughSwappy(false)
        calls.destroySwapchain(device, swapchain)
        host.setPresentPacing(false)
        window?.let { host.setWindowFrameRate(it, 0f) }
        active = false
        appliedMode = null
    }
}
