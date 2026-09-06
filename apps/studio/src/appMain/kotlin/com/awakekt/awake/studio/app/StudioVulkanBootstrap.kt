/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.app

import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.vulkan.application.VulkanEngine

/** Studio on Vulkan. Runs [StudioRenderPlan] whole -- this backend supports all of it. */
fun createStudioVulkanApplication(
    game: AwakeAppLifecycle = studioApp(),
): VulkanEngine = VulkanEngine(appLifecycle = game, plan = StudioRenderPlan)
