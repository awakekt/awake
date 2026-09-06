/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.studio.plugins

internal class WasmJsStudioPluginPersistence : StudioPluginPersistence {
    private val memory = InMemoryStudioPluginPersistence()

    override fun loadInstalled() = memory.loadInstalled()

    override fun saveInstalled(manifests: List<com.awakekt.awake.editor.core.plugin.PluginManifest>) {
        memory.saveInstalled(manifests)
    }
}

actual fun createPlatformPluginPersistence(): StudioPluginPersistence =
    WasmJsStudioPluginPersistence()
