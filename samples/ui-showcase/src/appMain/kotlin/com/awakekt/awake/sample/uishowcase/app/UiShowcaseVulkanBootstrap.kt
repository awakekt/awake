/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.vulkan.application.VulkanEngine

/** The UI showcase on Vulkan -- android, iOS and desktop. */
fun createUiShowcaseVulkanApplication(
    game: AwakeAppLifecycle = uiShowcase(),
): VulkanEngine = VulkanEngine(appLifecycle = game, plan = UiShowcaseRenderPlan)
