/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.engine.bootstrap.dsl.WindowDsl
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState

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
}
