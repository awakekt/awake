/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.bootstrap.dsl.WindowDsl
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseApp
import com.awakekt.awake.scene.authoring.scene

private val composeShowcaseDefinition = appDefinition(createState = { Unit }) {
    window {
        configureComposeShowcaseWindow()
    }
    module {
        appModule {
            // The app's own scope. Its UI reads it as LocalAppCoroutineScope, the State & DI page's
            // stores run under it, and the app closes it, cancelling them, when it is disposed.
            service(AppCoroutineScope::class, AppCoroutineScope(serviceLookup()))
            scene { content { ShowcaseApp() } }
        }
    }
}

fun composeShowcase() = composeShowcaseDefinition.createApp()

private fun WindowDsl.configureComposeShowcaseWindow() {
    title = "Awake Compose Showcase"
    size(1400, 900)
}
