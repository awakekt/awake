/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.enums.VkResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** A swapchain that presents suboptimally every frame asks its surface once, not once a frame (#1688). */
class PresentActionTest {
    @Test
    fun aPanelPresentingSuboptimallyEveryFrameAsksItsSurfaceOnce() {
        var accepted = false
        var queries = 0
        var recreations = 0
        repeat(FRAMES) {
            when (presentAction(VkResult.VK_SUBOPTIMAL_KHR, accepted) { queries++; false }) {
                PresentAction.AcceptSuboptimal -> accepted = true
                PresentAction.Recreate -> recreations++
                PresentAction.None -> Unit
            }
        }
        assertEquals(1, queries, "the surface is asked once, then the answer is kept")
        assertEquals(0, recreations, "an unchanged surface keeps its swapchain")
    }

    @Test
    fun aChangedSurfaceOrAnOutOfDateSwapchainIsBuiltAgain() {
        assertEquals(PresentAction.Recreate, presentAction(VkResult.VK_SUBOPTIMAL_KHR, false) { true })
        assertEquals(PresentAction.Recreate, presentAction(VkResult.VK_ERROR_OUT_OF_DATE_KHR, true) { error("not asked") })
        assertEquals(PresentAction.None, presentAction(VkResult.VK_SUCCESS, false) { error("not asked") })
    }

    private companion object {
        const val FRAMES = 600
    }
}
