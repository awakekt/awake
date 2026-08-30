/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.app

import io.github.awakelab.awake.engine.bootstrap.dsl.appModule
import io.github.awakelab.awake.engine.platform.core.AppModule
import io.github.awakelab.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import io.github.awakelab.awake.sample.uishowcase.ui.uiShowcaseUiModule

internal fun uiShowcaseModule(state: UiShowcaseRuntimeState): AppModule = appModule {
    module(uiShowcaseUiModule(state))
    // 3D content (the old background rotating-cube scene) moved out of this sample --
    // ui-showcase is the 2D UI component catalog only now. See samples/scene3d-playground/,
    // a standalone sibling sample app, for where that content lives now.
}
