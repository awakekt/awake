/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.app

import io.github.awakelab.awake.engine.bootstrap.dsl.WindowDsl
import io.github.awakelab.awake.engine.bootstrap.dsl.appDefinition
import io.github.awakelab.awake.engine.bootstrap.dsl.select
import io.github.awakelab.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.awakelab.awake.showcase.DEFAULT_SHOWCASE_ID
import io.github.awakelab.awake.showcase.EngineShowcases
import io.github.awakelab.awake.showcase.engineShowcaseModule

/** Creates a focused showcase session; callers choose an ID from [EngineShowcases]. */
fun engineShowcaseApp(initialShowcaseId: String = DEFAULT_SHOWCASE_ID): AwakeAppLifecycle =
    appDefinition(createState = {}) {
        window { configureShowcaseWindow() }
        module(engineShowcaseModule(initialShowcaseId))
    }.createApp()

private fun WindowDsl.configureShowcaseWindow() {
    title = "Awake Engine Showcase"
    size(1600, 900)
    backend.select(platformBackendPreference())
}
