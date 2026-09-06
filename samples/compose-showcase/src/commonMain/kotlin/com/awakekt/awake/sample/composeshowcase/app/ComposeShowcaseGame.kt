/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.bootstrap.dsl.WindowDsl
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.bootstrap.dsl.select
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseApp
import com.awakekt.awake.scene.authoring.scene

private val composeShowcaseDefinition = appDefinition(createState = { Unit }) {
    window {
        configureComposeShowcaseWindow()
    }
    module { appModule { scene { content { ShowcaseApp() } } } }
}

fun composeShowcase() = composeShowcaseDefinition.createApp()

private fun WindowDsl.configureComposeShowcaseWindow() {
    title = "Awake Compose Showcase"
    size(1400, 900)
    backend.select(platformBackendPreference())
}
