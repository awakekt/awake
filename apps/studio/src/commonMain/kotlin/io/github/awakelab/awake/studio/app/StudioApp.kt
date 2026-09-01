/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.app

import io.github.awakelab.awake.engine.bootstrap.dsl.WindowDsl
import io.github.awakelab.awake.engine.bootstrap.dsl.appDefinition
import io.github.awakelab.awake.engine.bootstrap.dsl.select
import io.github.awakelab.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.awakelab.awake.studio.studioModule

private val studioDefinition = appDefinition(createState = {}) {
    window {
        configureStudioWindow()
    }
    module(studioModule())
}

fun studioApp(): AwakeAppLifecycle = studioDefinition.createApp()

private fun WindowDsl.configureStudioWindow() {
    title = "Awake Studio"
    @Suppress("MagicNumber") // Default window size, used exactly once.
    size(1600, 900)
    backend.select(platformBackendPreference())
}
