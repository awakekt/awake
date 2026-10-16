/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.engine.compose.ComposeAppRuntime
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.webgpu.application.launchWebGpuGame

fun main() {
    val game = uiShowcase()
    // The browser shows no pointer shape unless the host is handed the runtime's request.
    val runtime = game.requireService<ComposeAppRuntime>()
    launchWebGpuGame(cursor = { runtime.cursor }) { createUiShowcaseWebGpuApplication(game) }
}
