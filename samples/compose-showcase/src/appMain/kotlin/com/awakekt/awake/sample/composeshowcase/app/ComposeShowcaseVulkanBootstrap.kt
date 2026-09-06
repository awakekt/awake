/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.vulkan.application.VulkanEngine

fun createComposeShowcaseVulkanApplication(
    game: AwakeAppLifecycle = composeShowcase(),
): VulkanEngine = VulkanEngine(appLifecycle = game, plan = ComposeShowcaseRenderPlan)
