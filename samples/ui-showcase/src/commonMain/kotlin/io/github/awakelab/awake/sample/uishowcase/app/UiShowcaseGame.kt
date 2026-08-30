/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.app

import io.github.awakelab.awake.engine.bootstrap.dsl.WindowDsl
import io.github.awakelab.awake.engine.bootstrap.dsl.appDefinition
import io.github.awakelab.awake.engine.bootstrap.dsl.select
import io.github.awakelab.awake.sample.uishowcase.state.UiShowcaseRuntimeState

private val uiShowcaseDefinition = appDefinition(createState = ::UiShowcaseRuntimeState) {
    window {
        configureUiShowcaseWindow()
    }
    module(::uiShowcaseModule)
}

fun uiShowcase() = uiShowcaseDefinition.createApp()

private fun WindowDsl.configureUiShowcaseWindow() {
    title = "Awake UI Showcase"
    size(1600, 900)
    backend.select(platformBackendPreference())
}
