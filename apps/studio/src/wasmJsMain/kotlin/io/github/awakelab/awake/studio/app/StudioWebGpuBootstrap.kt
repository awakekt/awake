/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.app

import io.github.awakelab.awake.webgpu.application.WebGpuEngine

/**
 * Studio on WebGPU.
 *
 * Hands over [StudioRenderPlan] whole. What this backend cannot run, it narrows away itself and
 * says so -- see `WebGpuCapabilities`. This file used to carry a hand-narrowed second copy of the
 * plan, which had already lost a pipeline whose reason survived only as a comment.
 */
fun createStudioWebGpuApplication(): WebGpuEngine =
    WebGpuEngine(appLifecycle = studioApp(), requestedPlan = StudioRenderPlan)
