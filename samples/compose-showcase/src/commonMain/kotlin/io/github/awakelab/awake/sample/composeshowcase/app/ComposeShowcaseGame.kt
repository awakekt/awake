/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.composeshowcase.app

import io.github.awakelab.awake.engine.bootstrap.dsl.WindowDsl
import io.github.awakelab.awake.engine.bootstrap.dsl.appDefinition
import io.github.awakelab.awake.engine.bootstrap.dsl.appModule
import io.github.awakelab.awake.engine.bootstrap.dsl.select
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcaseApp
import io.github.awakelab.awake.scene.authoring.scene

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
