/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.studio.plugins

import com.awakekt.awake.editor.core.plugin.PluginManifest
import kotlinx.serialization.json.Json
import java.io.File

internal class DesktopStudioPluginPersistence(
    private val storageFile: File = File(
        File(System.getProperty("user.home"), ".awake"),
        "studio-plugins.json",
    ),
) : StudioPluginPersistence {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val disabledFile: File = File(storageFile.parentFile, "studio-disabled-plugins.json")

    override fun loadInstalled(): List<PluginManifest> {
        if (!storageFile.exists() || !storageFile.isFile) return emptyList()
        return try {
            val content = storageFile.readText()
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveInstalled(manifests: List<PluginManifest>) {
        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(json.encodeToString(manifests))
        } catch (_: Exception) {
            // Ignore write failures in read-only environments
        }
    }

    override fun loadDisabledPluginIds(): Set<String> {
        if (!disabledFile.exists() || !disabledFile.isFile) return emptySet()
        return try {
            val content = disabledFile.readText()
            if (content.isBlank()) emptySet() else json.decodeFromString(content)
        } catch (_: Exception) {
            emptySet()
        }
    }

    override fun saveDisabledPluginIds(ids: Set<String>) {
        try {
            disabledFile.parentFile?.mkdirs()
            disabledFile.writeText(json.encodeToString(ids))
        } catch (_: Exception) {
            // Ignore write failures in read-only environments
        }
    }
}

actual fun createPlatformPluginPersistence(): StudioPluginPersistence =
    DesktopStudioPluginPersistence()
