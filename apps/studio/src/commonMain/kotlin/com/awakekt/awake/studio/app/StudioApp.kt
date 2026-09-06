/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.app

import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.keybinding.EditorKeymapBuilder
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.select
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.studio.fixture.StudioSceneDescriptor
import com.awakekt.awake.studio.fixture.StudioSceneRegistry
import com.awakekt.awake.studio.studioModule

/**
 * Creates and configures the Awake Studio runtime with optional consumer plugins and window settings.
 */
fun studioApp(
    plugins: List<EditorPlugin> = emptyList(),
    title: String = "Awake Studio",
    width: Int = 1600,
    height: Int = 900,
    includeDefaultFixture: Boolean = true,
    keymap: EditorKeymapBuilder? = null,
    container: com.awakekt.awake.core.di.Container? = null,
): AwakeAppLifecycle = app {
    window {
        this.title = title
        @Suppress("MagicNumber")
        size(width, height)
        backend.select(platformBackendPreference())
    }
    install(
        studioModule(
            plugins = plugins,
            includeDefaultFixture = includeDefaultFixture,
            keymap = keymap,
            container = container,
        ),
    )
}

/**
 * Builder configuration for [studioApp].
 */
class StudioAppConfig {
    var title: String = "Awake Studio"
    var width: Int = 1600
    var height: Int = 900
    var includeDefaultFixture: Boolean = true
    val plugins = mutableListOf<EditorPlugin>()
    var keymapBuilder: EditorKeymapBuilder? = null
    var container: com.awakekt.awake.core.di.Container? = null
    val customScenes = mutableListOf<StudioSceneDescriptor>()

    /** Installs an [EditorPlugin] (e.g. data inspectors, scene systems, or asset converters). */
    fun install(plugin: EditorPlugin) {
        plugins += plugin
    }

    /** Installs multiple [EditorPlugin]s. */
    fun installAll(vararg plugins: EditorPlugin) {
        this.plugins += plugins
    }

    /** Configures custom keybindings or rebinds existing actions. */
    fun keymap(block: EditorKeymapBuilder.() -> Unit) {
        val builder = keymapBuilder ?: EditorKeymapBuilder().also { keymapBuilder = it }
        builder.apply(block)
    }

    /** Registers an authored scene into the Studio scene selector and files view. */
    fun scene(id: String, title: String, path: String, description: String = "") {
        customScenes += StudioSceneDescriptor(id = id, title = title, path = path, description = description)
    }

    /** Registers an existing [StudioSceneDescriptor]. */
    fun scene(descriptor: StudioSceneDescriptor) {
        customScenes += descriptor
    }
}

/**
 * DSL entry point for configuring and launching Awake Studio.
 *
 * Example:
 * ```kotlin
 * fun main() = studioApp {
 *     title = "My Game Studio"
 *     includeDefaultFixture = false
 *     scene("level-1", "Dungeon Level 1", "scenes/level1.scene.json")
 *     install(CombatGamePlugin())
 *     install(InventoryGamePlugin())
 *     keymap {
 *         rebind(EditorStandardActions.TOOL_TRANSLATE, KeyChord(Key.G))
 *     }
 * }.start()
 * ```
 */
fun studioApp(configure: StudioAppConfig.() -> Unit): AwakeAppLifecycle {
    val config = StudioAppConfig().apply(configure)
    if (!config.includeDefaultFixture) {
        StudioSceneRegistry.clear()
    }
    config.customScenes.forEach { StudioSceneRegistry.register(it) }
    return studioApp(
        plugins = config.plugins,
        title = config.title,
        width = config.width,
        height = config.height,
        includeDefaultFixture = config.includeDefaultFixture,
        keymap = config.keymapBuilder,
        container = config.container,
    )
}
