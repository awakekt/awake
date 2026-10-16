/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.webgpu.application.WebGpuEngine

/** The UI showcase on WebGPU. */
fun createUiShowcaseWebGpuApplication(game: AwakeAppLifecycle = uiShowcase()): WebGpuEngine =
    WebGpuEngine(appLifecycle = game, requestedPlan = UiShowcaseRenderPlan)
