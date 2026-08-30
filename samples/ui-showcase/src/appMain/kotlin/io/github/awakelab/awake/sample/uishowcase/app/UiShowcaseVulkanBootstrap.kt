/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.app

import io.github.awakelab.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.awakelab.awake.vulkan.application.VulkanEngine

/** The UI showcase on Vulkan -- android, iOS and desktop. */
fun createUiShowcaseVulkanApplication(
    game: AwakeAppLifecycle = uiShowcase(),
): VulkanEngine = VulkanEngine(appLifecycle = game, plan = UiShowcaseRenderPlan)
