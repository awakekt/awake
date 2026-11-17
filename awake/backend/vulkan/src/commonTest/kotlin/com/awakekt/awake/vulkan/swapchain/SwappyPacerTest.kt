/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import com.awakekt.awake.core.host.FramePacer
import com.awakekt.awake.core.host.FramePacing
import com.awakekt.awake.core.host.FrameRateMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SwappyPacer]'s mode logic and call order against a fake of Swappy's JNI surface (#1699): what a
 * device would do is not tested here, only what Awake asks of Swappy and when.
 */
class SwappyPacerTest {
    private val calls = FakeSwappyCalls()
    private val host = FakeSwappyHost()
    private val pacer = SwappyPacer(calls, host)

    @Test
    fun withSleepChosenNoSwappyEntryPointIsCalledFromDeviceToDeviceDestruction() {
        host.mode = FrameRateMode.Capped(30)
        lifecycle(frames = 5)
        assertEquals(emptyList(), calls.log, "no Swappy call at all with Sleep chosen")
        assertEquals(emptyList(), host.pacing, "the frame loop keeps sleeping")
        assertEquals(emptyList(), host.frameRates, "the window's frame rate is left alone")
    }

    @Test
    fun withSwappyChosenEachStepCallsSwappyInOrder() {
        host.swappy = true
        host.mode = FrameRateMode.Capped(30)
        lifecycle(frames = 2)
        assertEquals(
            listOf(
                "determineDeviceExtensions",
                "setQueueFamilyIndex 7 9 2",
                "init 5 7 11",
                "setWindow 7 11",
                "setSwapIntervalNs 7 11 33333334",
                "setPresentsThroughSwappy true",
                "setPresentsThroughSwappy false",
                "destroySwapchain 7 11",
                "destroyDevice 7",
            ),
            calls.log,
        )
        assertEquals(listOf(true, false), host.pacing, "the loop stops sleeping while Swappy paces, and sleeps again after")
        assertEquals(listOf(30f, 0f), host.frameRates, "a cap fixes the window's frame rate, and lets it go after")
    }

    @Test
    fun aCapIsAWholeNumberOfRefreshesAndNeverPacedTwice() {
        host.swappy = true
        host.refreshNanos = 11_111_111 // 90 Hz
        host.mode = FrameRateMode.Capped(45)
        start()
        assertTrue("setSwapIntervalNs 7 11 22222222" in calls.log, "45 on 90 Hz holds each image for two refreshes: ${calls.log}")
        val mode = host.mode
        val pacer = FramePacing.pacer(mode, presentPacing = host.pacing.last())
        assertEquals(FramePacer.Present, pacer)
        assertEquals(0L, FramePacing.sleepNanos(mode, pacer, frameWorkNanos = 0L), "the loop does not also sleep a cap Swappy paces")
    }

    @Test
    fun theModeIsFollowedLiveAndUnlimitedIsNotPaced() {
        host.swappy = true
        host.mode = FrameRateMode.Capped(30)
        start()
        calls.log.clear()
        host.mode = FrameRateMode.Auto
        pacer.beforePresent()
        pacer.beforePresent()
        host.mode = FrameRateMode.Unlimited
        pacer.beforePresent()
        assertEquals(
            listOf("setSwapIntervalNs 7 11 16666667", "setPresentsThroughSwappy true", "setPresentsThroughSwappy false"),
            calls.log,
            "Auto presents every refresh, once; Unlimited presents straight through",
        )
        assertEquals(listOf(true, true, false), host.pacing)
    }

    @Test
    fun switchingLiveStartsAndStopsSwappyOnTheSameSwapchain() {
        host.mode = FrameRateMode.Capped(30)
        start()
        assertEquals(emptyList(), calls.log.filterNot { it == "determineDeviceExtensions" })
        host.swappy = true
        pacer.beforePresent()
        assertTrue(pacer.active, "Swappy took over the running swapchain")
        assertTrue("init 5 7 11" in calls.log)
        host.swappy = false
        pacer.beforePresent()
        assertTrue(!pacer.active)
        assertEquals("destroySwapchain 7 11", calls.log.last())
        assertEquals(listOf(true, false), host.pacing)
    }

    @Test
    fun aSwapchainSwappyCannotStartOnIsLeftToTheLoopUntilTheNextOne() {
        host.swappy = true
        host.refreshNanos = 0
        start()
        pacer.beforePresent()
        pacer.beforePresent()
        assertEquals(1, calls.log.count { it.startsWith("init") }, "not tried again on the same swapchain: ${calls.log}")
        assertEquals(emptyList(), host.pacing, "the loop keeps sleeping")
        pacer.swapchainDestroying(11)
        host.refreshNanos = 16_666_667
        pacer.swapchainCreated(12)
        assertTrue(pacer.active, "the next swapchain starts")
    }

    @Test
    fun withoutAnActivitySwappyIsNotStarted() {
        host.swappy = true
        host.activity = null
        start()
        pacer.beforePresent()
        assertTrue(calls.log.none { it.startsWith("init") || it.startsWith("setQueueFamilyIndex") })
        assertEquals(emptyList(), host.pacing)
    }

    private fun start() {
        pacer.deviceExtensions(5, setOf("VK_KHR_swapchain"))
        pacer.deviceCreated(physicalDevice = 5, device = 7, presentQueue = 9, presentFamily = 2)
        pacer.windowChanged("surface")
        pacer.swapchainCreated(11)
    }

    private fun lifecycle(frames: Int) {
        start()
        repeat(frames) { pacer.beforePresent() }
        pacer.swapchainDestroying(11)
        pacer.windowChanged(null)
        pacer.deviceDestroying()
    }

    private inner class FakeSwappyCalls : SwappyCalls {
        val log = mutableListOf<String>()

        override fun determineDeviceExtensions(physicalDevice: Long, available: Array<String>): Array<String> {
            log += "determineDeviceExtensions"
            return arrayOf("VK_GOOGLE_display_timing")
        }

        override fun setQueueFamilyIndex(device: Long, queue: Long, queueFamilyIndex: Int) {
            log += "setQueueFamilyIndex $device $queue $queueFamilyIndex"
        }

        override fun initAndGetRefreshCycleDuration(activity: Any, physicalDevice: Long, device: Long, swapchain: Long): Long {
            log += "init $physicalDevice $device $swapchain"
            return host.refreshNanos
        }

        override fun setWindow(device: Long, swapchain: Long, window: Any) {
            log += "setWindow $device $swapchain"
        }

        override fun setSwapIntervalNs(device: Long, swapchain: Long, swapIntervalNs: Long) {
            log += "setSwapIntervalNs $device $swapchain $swapIntervalNs"
        }

        override fun destroySwapchain(device: Long, swapchain: Long) {
            log += "destroySwapchain $device $swapchain"
        }

        override fun destroyDevice(device: Long) {
            log += "destroyDevice $device"
        }

        override fun setPresentsThroughSwappy(enabled: Boolean) {
            log += "setPresentsThroughSwappy $enabled"
        }
    }

    private class FakeSwappyHost : SwappyHost {
        var swappy = false
        var mode: FrameRateMode = FrameRateMode.Auto
        var refreshNanos = 16_666_667L
        override var activity: Any? = "activity"
        val pacing = mutableListOf<Boolean>()
        val frameRates = mutableListOf<Float>()

        override val swappySelected: Boolean get() = swappy
        override val frameRateMode: FrameRateMode get() = mode

        override fun setPresentPacing(enabled: Boolean) {
            pacing += enabled
        }

        override fun setWindowFrameRate(window: Any, fps: Float) {
            frameRates += fps
        }
    }
}
