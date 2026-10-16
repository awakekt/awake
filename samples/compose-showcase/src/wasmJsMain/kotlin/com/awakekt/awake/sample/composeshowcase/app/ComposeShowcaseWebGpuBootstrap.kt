/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.webgpu.application.WebGpuEngine

fun createComposeShowcaseWebGpuApplication(game: AwakeAppLifecycle = composeShowcase()): WebGpuEngine =
    WebGpuEngine(appLifecycle = game, requestedPlan = ComposeShowcaseRenderPlan)
