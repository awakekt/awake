/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.compose.composeAppModule
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState

internal fun uiShowcaseUiModule(state: UiShowcaseRuntimeState): AppModule = appModule {
    // This sample has no scene or 3D pass. Install the application-level Compose host so it
    // presents the staged UI frame directly, including on WebGPU where a scene-less runtime has
    // no camera system to trigger presentation.
    module(composeAppModule(content = { ShowcaseApp(state) }))
}
