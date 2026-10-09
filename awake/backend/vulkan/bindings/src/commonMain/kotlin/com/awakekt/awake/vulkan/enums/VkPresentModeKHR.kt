/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How a swapchain queues and displays finished images (`VkPresentModeKHR`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPresentModeKHR(val value: Int) {
    /** Images are shown immediately without waiting for vertical blank, which can tear. */
    VK_PRESENT_MODE_IMMEDIATE_KHR(0),

    /**
     * A one-entry queue: a newer image replaces the waiting one, and display waits for vertical
     * blank without tearing.
     */
    VK_PRESENT_MODE_MAILBOX_KHR(1),

    /** A first-in first-out queue shown at vertical blank, like classic vsync; always supported. */
    VK_PRESENT_MODE_FIFO_KHR(2),

    /** Like FIFO, but an image that arrives late is shown immediately, which can tear. */
    VK_PRESENT_MODE_FIFO_RELAXED_KHR(3),

    /** A shared image that the display refreshes on demand. */
    VK_PRESENT_MODE_SHARED_DEMAND_REFRESH_KHR(1000111000),

    /** A shared image that the display refreshes continuously. */
    VK_PRESENT_MODE_SHARED_CONTINUOUS_REFRESH_KHR(1000111001),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_PRESENT_MODE_MAX_ENUM_KHR(0x7FFFFFFF),
}
