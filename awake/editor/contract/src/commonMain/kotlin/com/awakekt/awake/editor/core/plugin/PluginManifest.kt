/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Declares a dependency on another plugin required by this plugin.
 *
 * @property id Unique reversed-domain ID of the dependent plugin.
 * @property version Optional minimum SemVer version requirement.
 * @property optional Whether this dependency is optional (failure to resolve will not prevent loading).
 */
@Serializable
data class PluginDependency(
    val id: String,
    val version: String? = null,
    val optional: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Plugin dependency ID must not be blank." }
    }
}

/**
 * Serialized manifest schema for Studio Marketplace and project `.awakeplugin` packages (`plugin.json`).
 *
 * @property id Unique reversed-domain plugin ID (e.g. "awake.terrain" or "com.acme.dialogue").
 * @property name Human-readable display name.
 * @property version SemVer version string (e.g. "1.2.0").
 * @property author Author or studio name.
 * @property description Short description of the extension capabilities.
 * @property entrypointClass Fully-qualified class name implementing [EditorPlugin]. When non-blank
 *   the plugin archive **must** contain a non-empty bytecode payload; a metadata-only archive with
 *   a declared entrypoint is rejected by the pipeline.
 * @property requiredApiVersion Required [PluginApiVersion] integer value.
 * @property minEngineVersion Optional minimum Awake engine SemVer version string.
 * @property supportedPlatforms List of supported platform identifiers (e.g. "desktop", "android", "ios", "wasmJs").
 * @property targetJvmVersion Optional target JVM bytecode major version.
 * @property dependencies List of other plugin dependencies required by this plugin.
 * @property isPro Whether this plugin requires an active Awake Pro commercial license.
 * @property requiredLicense Specific optional entitlement identifier required by this plugin.
 * @property category Primary discovery category.
 * @property tags List of search/discovery tag keywords.
 * @property documentationUrl Optional URL link to documentation.
 */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "Community",
    val description: String = "",
    val entrypointClass: String = "",
    val requiredApiVersion: Int = 1,
    val minEngineVersion: String? = null,
    val supportedPlatforms: List<String> = emptyList(),
    val targetJvmVersion: Int? = null,
    val dependencies: List<PluginDependency> = emptyList(),
    val isPro: Boolean = false,
    val requiredLicense: String? = null,
    val category: String = "Tools",
    val tags: List<String> = emptyList(),
    val documentationUrl: String = "",
) {
    init {
        require(id.isNotBlank()) { "Plugin ID must not be blank." }
        require(name.isNotBlank()) { "Plugin name must not be blank." }
        require(version.isNotBlank()) { "Plugin version must not be blank." }
    }

    /** Converts this manifest to JSON string representation. */
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Parses a [PluginManifest] from JSON text. */
        fun fromJson(jsonText: String): PluginManifest =
            json.decodeFromString(serializer(), jsonText)
    }
}
