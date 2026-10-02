/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.engine.bootstrap.dsl.WindowDsl
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.engine.platform.config.PresentMode
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.showcase.DEFAULT_SHOWCASE_ID
import com.awakekt.awake.showcase.EngineShowcases
import com.awakekt.awake.showcase.ShowcaseLaunchOptions
import com.awakekt.awake.showcase.engineShowcaseModule

/** Creates a focused showcase session; callers choose an ID from [EngineShowcases]. */
fun engineShowcaseApp(
    initialShowcaseId: String = DEFAULT_SHOWCASE_ID,
    options: ShowcaseLaunchOptions = ShowcaseLaunchOptions(),
): AwakeAppLifecycle =
    appDefinition(createState = {}) {
        window { configureShowcaseWindow(options) }
        module(engineShowcaseModule(initialShowcaseId, options))
    }.createApp()

private fun WindowDsl.configureShowcaseWindow(options: ShowcaseLaunchOptions) {
    title = "Awake Engine Showcase"
    size(1600, 900)
    if (!options.vsync) presentMode = PresentMode.NoVsync
}
