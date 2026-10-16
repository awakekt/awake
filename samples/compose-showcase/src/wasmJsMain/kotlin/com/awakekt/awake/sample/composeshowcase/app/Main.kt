/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.webgpu.application.launchWebGpuGame

fun main() {
    val game = composeShowcase()
    // The browser shows no pointer shape unless the host is handed the runtime's request.
    val runtime = game.requireService<SceneAppLifecycleRuntime>()
    launchWebGpuGame(cursor = { runtime.cursor }) { createComposeShowcaseWebGpuApplication(game) }
}
