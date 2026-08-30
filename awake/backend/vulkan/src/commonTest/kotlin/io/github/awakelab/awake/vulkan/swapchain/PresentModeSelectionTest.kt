/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.swapchain

import io.github.awakelab.awake.engine.platform.config.PresentMode
import io.github.awakelab.awake.vulkan.enums.VkPresentModeKHR
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a present-mode request resolves to, including when the surface cannot honour it.
 *
 * The fallbacks are the point. FIFO is the only mode the spec requires everyone to support, and
 * MoltenVK in particular does not always offer mailbox -- so an app that asked not to be capped
 * can end up capped, and a frame-time number measured there describes the display rather than the
 * engine. Studio spent a session's worth of profiling on a 17.4 ms frame that was one 60 Hz
 * interval.
 */
class PresentModeSelectionTest {

    private val fifo = VkPresentModeKHR.VK_PRESENT_MODE_FIFO_KHR
    private val mailbox = VkPresentModeKHR.VK_PRESENT_MODE_MAILBOX_KHR
    private val immediate = VkPresentModeKHR.VK_PRESENT_MODE_IMMEDIATE_KHR

    @Test
    fun eachRequestTakesItsOwnModeWhenTheSurfaceOffersIt() {
        val all = listOf(fifo, mailbox, immediate)
        assertEquals(mailbox, chooseSwapPresentMode(all, PresentMode.Auto))
        assertEquals(mailbox, chooseSwapPresentMode(all, PresentMode.LowLatency))
        assertEquals(immediate, chooseSwapPresentMode(all, PresentMode.NoVsync))
        assertEquals(fifo, chooseSwapPresentMode(all, PresentMode.Vsync))
    }

    @Test
    fun aSurfaceWithoutMailboxCapsEveryRequestThatWantedNoCap() {
        // MoltenVK's common shape, and the reason selectedPresentMode is reported rather than the
        // request being assumed to have been honoured.
        val noMailbox = listOf(fifo)
        assertEquals(fifo, chooseSwapPresentMode(noMailbox, PresentMode.Auto))
        assertEquals(fifo, chooseSwapPresentMode(noMailbox, PresentMode.LowLatency))
        assertEquals(fifo, chooseSwapPresentMode(noMailbox, PresentMode.NoVsync))
    }

    @Test
    fun anExplicitNoVsyncTakesTheOtherUncappedModeBeforeAcceptingACap() {
        // Immediate missing but mailbox present: still uncapped, which is what was asked for.
        assertEquals(mailbox, chooseSwapPresentMode(listOf(fifo, mailbox), PresentMode.NoVsync))
        // Auto does not do the reverse -- it has no opinion beyond preferring mailbox.
        assertEquals(fifo, chooseSwapPresentMode(listOf(fifo, immediate), PresentMode.Auto))
    }
}
