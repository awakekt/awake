/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.scene.authoring.scene

/**
 * Mounted on [SceneAppLifecycleRuntime][com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime]
 * with no entities and no systems -- ui-showcase dropped its 3D content before this module
 * existed (see `GameModule.kt`'s own comment), so the `World` this runtime owns sits unused.
 * That is the same runtime Studio's shell renders through; there is no separate "UI-only"
 * runtime, and building one would duplicate `content { }`'s frame loop for no reason.
 */
internal fun uiShowcaseUiModule(state: UiShowcaseRuntimeState): AppModule = appModule {
    scene {
        content { ShowcaseApp(state) }
    }
}
