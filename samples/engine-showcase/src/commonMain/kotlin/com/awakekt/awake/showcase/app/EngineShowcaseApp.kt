/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.engine.bootstrap.dsl.WindowDsl
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.showcase.DEFAULT_SHOWCASE_ID
import com.awakekt.awake.showcase.EngineShowcases
import com.awakekt.awake.showcase.engineShowcaseModule

/** Creates a focused showcase session; callers choose an ID from [EngineShowcases]. */
fun engineShowcaseApp(initialShowcaseId: String = DEFAULT_SHOWCASE_ID): AwakeAppLifecycle =
    appDefinition(createState = {}) {
        window { configureShowcaseWindow() }
        module(engineShowcaseModule(initialShowcaseId))
    }.createApp()

private fun WindowDsl.configureShowcaseWindow() {
    title = "Awake Engine Showcase"
    size(1600, 900)
}
