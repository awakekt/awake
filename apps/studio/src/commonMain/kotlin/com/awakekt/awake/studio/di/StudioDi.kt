/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.di

import com.awakekt.awake.core.di.Container
import com.awakekt.awake.core.di.Module
import com.awakekt.awake.core.di.container
import com.awakekt.awake.core.di.module
import com.awakekt.awake.core.di.resolve
import com.awakekt.awake.editor.core.files.EditorFileChooser
import com.awakekt.awake.editor.core.files.createPlatformFileChooser
import com.awakekt.awake.studio.config.StudioEnvironment
import com.awakekt.awake.studio.config.loadPlatformEnvironment
import com.awakekt.awake.studio.plugins.StudioPluginPersistence
import com.awakekt.awake.studio.plugins.createPlatformPluginPersistence
import com.awakekt.awake.studio.plugins.discovery.PluginDiscovery
import com.awakekt.awake.studio.plugins.discovery.PluginDiscoveryOfficial
import com.awakekt.awake.studio.plugins.repository.DefaultStudioPluginRepository
import com.awakekt.awake.studio.plugins.repository.StudioPluginRepository
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore

val studioCoreModule: Module = module {
    singleton<StudioEnvironment> { loadPlatformEnvironment() }
    singleton<StudioStore> { StudioStore() }
    singleton<EditorFileChooser> { createPlatformFileChooser() }
    singleton<PluginDiscovery> { PluginDiscoveryOfficial() }
    singleton<StudioPluginPersistence> { createPlatformPluginPersistence() }
    singleton<com.awakekt.awake.studio.ui.theme.StudioThemeState> {
        com.awakekt.awake.studio.ui.theme.StudioThemeState()
    }
    singleton<com.awakekt.awake.editor.core.license.AwakeLicenseRegistry> {
        com.awakekt.awake.editor.core.license.AwakeLicenseRegistry
    }
    singleton<com.awakekt.awake.ui.shadcn.components.ShadcnToastState> {
        com.awakekt.awake.ui.shadcn.components.ShadcnToastState()
    }
    singleton<StudioPluginRepository> {
        DefaultStudioPluginRepository(
            discovery = resolve(),
            persistence = resolve(),
        )
    }
    factory<StudioEditorBridge> {
        StudioEditorBridge(
            studio = resolve(),
            fileChooser = resolve(),
            persistence = resolve(),
            pluginRepository = resolve(),
        )
    }
}

fun createStudioContainer(overrideModule: Module? = null): Container {
    val modules = if (overrideModule != null) listOf(studioCoreModule, overrideModule) else listOf(studioCoreModule)
    return container(modules)
}
