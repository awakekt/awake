/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.swapchain

import com.awakekt.awake.core.host.FrameRateMode

/** Nothing paces presents here: the frame loop and the present mode do. */
internal actual fun platformSwapchainPacer(window: Any, configuredMode: () -> FrameRateMode): SwapchainPacer? = null
