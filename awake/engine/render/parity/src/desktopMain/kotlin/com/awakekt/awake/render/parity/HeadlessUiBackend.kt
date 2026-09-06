/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.vulkan.vulkanHeadlessScene
import com.awakekt.awake.vulkan.vulkanHeadlessUi
import com.awakekt.awake.webgpu.webGpuHeadlessScene
import com.awakekt.awake.webgpu.webGpuHeadlessUi

/** A backend this module can stand up windowless and draw the same scenario through. */
enum class HeadlessUiBackend {
    Vulkan,
    WebGpu,
}

/**
 * Opens [backend] headless, runs [block] against its renderer, and tears it down.
 *
 * Each backend built its own bootstrap inside its own test source set, so a question asked of both
 * -- do these draw the same thing? -- could not be written once and had nowhere to live. Each
 * backend now exposes its fixture as ordinary API and this picks between them, which is the whole
 * of what a parity test needs to know about backends.
 *
 * [size] sizes the render targets a scenario creates. WebGPU ignores it: it presents nothing, and
 * the 1x1 window it opens exists only to obtain an adapter.
 */
fun <T> withHeadlessUi(backend: HeadlessUiBackend, size: Int = SCENARIO_SIZE, block: (Renderer) -> T): T =
    openHeadlessUi(backend, size).use { block(it.renderer) }

/** [withHeadlessUi] without the scoping, for a caller holding one session across several renders. */
fun openHeadlessUi(backend: HeadlessUiBackend, size: Int = SCENARIO_SIZE): HeadlessRenderSession =
    when (backend) {
        HeadlessUiBackend.Vulkan -> vulkanHeadlessUi(size, size)
        HeadlessUiBackend.WebGpu -> webGpuHeadlessUi()
    }

/**
 * Opens [backend] headless with a lit, shadowed scene pipeline instead of the UI ones.
 *
 * A separate session rather than a flag on [openHeadlessUi]: the two build different pipelines from
 * different shaders, and a fixture that built both would make every UI comparison pay for a shadow
 * map it never samples.
 */
fun openHeadlessScene(backend: HeadlessUiBackend, size: Int = SCENE_SIZE): HeadlessRenderSession =
    when (backend) {
        HeadlessUiBackend.Vulkan -> vulkanHeadlessScene(size, size)
        HeadlessUiBackend.WebGpu -> webGpuHeadlessScene()
    }
