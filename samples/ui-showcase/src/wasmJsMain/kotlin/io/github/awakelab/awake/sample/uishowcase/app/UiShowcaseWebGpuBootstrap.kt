/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.app

import io.github.awakelab.awake.webgpu.application.WebGpuEngine

/** The UI showcase on WebGPU. */
fun createUiShowcaseWebGpuApplication(): WebGpuEngine =
    WebGpuEngine(appLifecycle = uiShowcase(), requestedPlan = UiShowcaseRenderPlan)
