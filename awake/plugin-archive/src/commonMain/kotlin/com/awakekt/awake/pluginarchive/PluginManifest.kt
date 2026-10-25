/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

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
public data class PluginDependency(
    val id: String,
    val version: String? = null,
    val optional: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Plugin dependency ID must not be blank." }
    }
}

/**
 * Serialized manifest schema for plugin marketplaces and project `.awakeplugin` packages (`plugin.json`).
 *
 * @property id Unique reversed-domain plugin ID (e.g. "awake.terrain" or "com.acme.dialogue").
 * @property name Human-readable display name.
 * @property version SemVer version string (e.g. "1.2.0").
 * @property author Author or studio name. Free-text display only; carries no trust meaning.
 * @property publisher Reverse-domain publisher id (e.g. "com.example.studio"), blank when unset.
 *   A signed archive's `signature.sig` is checked against the public keys a [PublisherKeyList]
 *   holds for this id ([PluginTrust], [PluginArchiveVerifier]); `author` is not part of that lookup.
 * @property description Short description of the extension capabilities.
 * @property entrypointClass Fully-qualified class name of the plugin's entry point (an editor host's
 *   `EditorPlugin`). When non-blank the plugin archive **must** contain a non-empty bytecode payload;
 *   a metadata-only archive with a declared entrypoint is rejected by the host.
 * @property requiredApiVersion Required editor plugin API version integer.
 * @property minEngineVersion Optional minimum Awake engine SemVer version string.
 * @property supportedPlatforms List of supported platform identifiers (e.g. "desktop", "android", "ios", "wasmJs").
 * @property targetJvmVersion Optional target JVM bytecode major version.
 * @property dependencies List of other plugin dependencies required by this plugin.
 * @property requiredLicense Entitlement a host must hold before activating this plugin, or null when it
 *   is free. The format names no product; each host maps the identifier to its own tiers.
 * @property category Primary discovery category.
 * @property tags List of search/discovery tag keywords.
 * @property documentationUrl Optional URL link to documentation.
 */
@Serializable
public data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "Community",
    val publisher: String = "",
    val description: String = "",
    val entrypointClass: String = "",
    val requiredApiVersion: Int = 1,
    val minEngineVersion: String? = null,
    val supportedPlatforms: List<String> = emptyList(),
    val targetJvmVersion: Int? = null,
    val dependencies: List<PluginDependency> = emptyList(),
    val requiredLicense: String? = null,
    val category: String = "Tools",
    val tags: List<String> = emptyList(),
    val documentationUrl: String = "",
) {
    init {
        require(id.isNotBlank()) { "Plugin ID must not be blank." }
        require(name.isNotBlank()) { "Plugin name must not be blank." }
        require(version.isNotBlank()) { "Plugin version must not be blank." }
        require(publisher.isBlank() || isPublisherId(publisher)) {
            "Plugin publisher must be blank or a reverse-domain id (e.g. 'com.example'), was '$publisher'."
        }
    }

    /** Converts this manifest to JSON string representation. */
    public fun toJson(): String = Json.encodeToString(serializer(), this)

    /**
     * Serializer and parser utilities for [PluginManifest].
     */
    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Parses a [PluginManifest] from JSON text. */
        public fun fromJson(jsonText: String): PluginManifest =
            json.decodeFromString(serializer(), jsonText)
    }
}

// At least two lowercase, alphanumeric-and-hyphen labels joined by dots: "com.example", not a bare
// label or an uppercase one.
private val publisherIdPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*(\\.[a-z0-9]+(-[a-z0-9]+)*)+$")

/** Whether [id] is a reverse-domain publisher id, the form [PluginManifest.publisher] and [PublisherKeys.id] take. */
internal fun isPublisherId(id: String): Boolean = publisherIdPattern.matches(id)
