/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.sample.uishowcase.ui.uiShowcaseUiModule

internal fun uiShowcaseModule(state: UiShowcaseRuntimeState): AppModule = appModule {
    module(uiShowcaseUiModule(state))
    // 3D content (the old background rotating-cube scene) moved out of this sample --
    // ui-showcase is the 2D UI component catalog only now. See samples/scene3d-playground/,
    // a standalone sibling sample app, for where that content lives now.
}
