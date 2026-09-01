/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.composeshowcase.app

import io.github.awakelab.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.awakelab.awake.vulkan.application.VulkanEngine

fun createComposeShowcaseVulkanApplication(
    game: AwakeAppLifecycle = composeShowcase(),
): VulkanEngine = VulkanEngine(appLifecycle = game, plan = ComposeShowcaseRenderPlan)
