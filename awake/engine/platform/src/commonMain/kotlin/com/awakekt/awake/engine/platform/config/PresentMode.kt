/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.config

/**
 * How finished frames reach the display.
 *
 * Backend-neutral on purpose: the same four choices exist in Vulkan and WebGPU under different
 * spellings, and an app should not have to name `VkPresentModeKHR` to ask for a frame cap.
 *
 * This is a *request*. A backend takes the closest mode its surface actually offers and falls
 * back to [Vsync], which every implementation is required to support -- MoltenVK, for one, does
 * not always offer [LowLatency]. Whatever it settles on is reported back so a caller can tell
 * "I asked for no cap" from "I got one anyway"; see the Vulkan `SwapchainManager`'s own
 * `selectedPresentMode`.
 */
enum class PresentMode {
    /**
     * Prefer [LowLatency], fall back to [Vsync]. The engine's default and its historical
     * behaviour.
     */
    Auto,

    /**
     * Block until the display is ready. Frame rate is capped at the refresh rate, no tearing,
     * lowest power. `VK_PRESENT_MODE_FIFO_KHR`.
     *
     * A frame budget measured under this cap is measuring the display, not the engine: a 17 ms
     * frame on a 60 Hz panel may be 3 ms of work and 14 ms of waiting.
     */
    Vsync,

    /**
     * Replace the queued frame instead of blocking. Uncapped, no tearing, costs a third image.
     * `VK_PRESENT_MODE_MAILBOX_KHR`, and the one most likely to be unavailable.
     */
    LowLatency,

    /**
     * Present immediately, tearing and all. Uncapped. `VK_PRESENT_MODE_IMMEDIATE_KHR`.
     *
     * The honest choice when profiling: it is the only mode that lets a frame-time number mean
     * how long the frame took.
     */
    NoVsync,
}
