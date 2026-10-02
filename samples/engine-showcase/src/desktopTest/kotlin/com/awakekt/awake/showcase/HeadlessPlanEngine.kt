/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.vulkan.application.VulkanEngine

/** Boots a showcase's real render plan on a windowless Vulkan surface, for frame tests. */
internal class HeadlessPlanEngine(
    lifecycle: AwakeAppLifecycle,
    plan: RenderPlan,
) : VulkanEngine(lifecycle, plan) {
    suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
}
