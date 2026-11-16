/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.enums.VkResult

/** What a frame does after `vkQueuePresentKHR` answered. */
internal enum class PresentAction {
    /** Nothing: the present succeeded, or it was suboptimal on a surface already found unchanged. */
    None,

    /** Build the swapchain again: it is out of date, or suboptimal on a surface that changed. */
    Recreate,

    /** Keep the swapchain, and stop asking the surface on later suboptimal presents. */
    AcceptSuboptimal,
}

/**
 * The [PresentAction] for a present that answered [result]. A suboptimal present asks [surfaceChanged]
 * only while [suboptimalAccepted] is false: a phone panel whose swapchain is not pre-rotated presents
 * suboptimally on every frame, and the surface query, three Vulkan calls and their arrays, answers the
 * same each time (#1688). The swapchain's build clears the acceptance, and a resize or rotation the
 * platform reports is checked at acquire.
 */
internal inline fun presentAction(result: VkResult, suboptimalAccepted: Boolean, surfaceChanged: () -> Boolean): PresentAction = when (result) {
    VkResult.VK_ERROR_OUT_OF_DATE_KHR -> PresentAction.Recreate
    VkResult.VK_SUBOPTIMAL_KHR -> when {
        suboptimalAccepted -> PresentAction.None
        surfaceChanged() -> PresentAction.Recreate
        else -> PresentAction.AcceptSuboptimal
    }
    else -> PresentAction.None
}
