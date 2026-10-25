/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Maven coordinates of one artifact a plugin's runtime needs, the code a played project loads
 * without any editor.
 *
 * @property group Maven group id (e.g. "com.example.weather").
 * @property artifact Maven artifact id (e.g. "weather-runtime").
 * @property version Exact version; a lock never holds a range.
 */
@Serializable
public data class PluginRuntimeModule(val group: String, val artifact: String, val version: String) {
    init {
        require(group.isCoordinatePart()) { "Maven group '$group' is blank or holds ':' or whitespace." }
        require(artifact.isCoordinatePart()) { "Maven artifact '$artifact' is blank or holds ':' or whitespace." }
        require(version.isCoordinatePart()) { "Maven version '$version' is blank or holds ':' or whitespace." }
    }

    /** `group:artifact:version`, as a Gradle dependency notation. */
    public val coordinates: String get() = "$group:$artifact:$version"

    /** Parsing of `group:artifact:version` notation. */
    public companion object {
        /** Parses `group:artifact:version`. */
        public fun parse(coordinates: String): PluginRuntimeModule {
            val parts = coordinates.split(':')
            require(parts.size == 3) { "Expected group:artifact:version, was '$coordinates'." }
            return PluginRuntimeModule(parts[0], parts[1], parts[2])
        }
    }
}

private fun String.isCoordinatePart(): Boolean = isNotBlank() && none { it == ':' || it.isWhitespace() }

/**
 * One plugin a project has pinned in its [AwakePluginsLock].
 *
 * @property id The plugin's [PluginManifest.id].
 * @property version The exact [PluginManifest.version] installed.
 * @property publisher The [PluginManifest.publisher] it was verified against, blank when unsigned.
 * @property digest Lowercase hex [PluginArchiveDigest.canonicalDigest] of the installed archive, so a
 *   later install of different bytes under the same version is noticed.
 * @property runtime Artifacts the plugin's runtime needs in a played project.
 */
@Serializable
public data class LockedPlugin(
    val id: String,
    val version: String,
    val publisher: String = "",
    val digest: String,
    val runtime: List<PluginRuntimeModule> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Locked plugin id must not be blank." }
        require(version.isNotBlank()) { "Locked plugin version must not be blank." }
        require(digest.length == 64 && digest.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Locked plugin digest must be 64 lowercase hex characters (SHA-256), was '$digest'."
        }
    }
}

/**
 * `plugins.lock.json`, the file a project keeps of the exact plugins it uses: which version of each,
 * from which publisher, the archive's digest, and the runtime artifacts a played project needs.
 *
 * JSON: `{"version": 1, "plugins": [{"id": "...", "version": "...", "publisher": "...", "digest": "...",
 * "runtime": [{"group": "...", "artifact": "...", "version": "..."}]}]}`. [toJson] writes plugins
 * sorted by id with stable formatting, so the file diffs cleanly under version control.
 *
 * @property version The lock file format version, [FORMAT_VERSION] for files this writes.
 * @property plugins The pinned plugins; an id appears at most once.
 */
@Serializable
public data class AwakePluginsLock(val version: Int = FORMAT_VERSION, val plugins: List<LockedPlugin> = emptyList()) {
    init {
        require(version in 1..FORMAT_VERSION) { "Unsupported plugins.lock.json version $version; this reads 1 to $FORMAT_VERSION." }
        val duplicates = plugins.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Plugin ids appear more than once: ${duplicates.sorted()}." }
    }

    /** The pinned entry for [id], or null. */
    public fun plugin(id: String): LockedPlugin? = plugins.firstOrNull { it.id == id }

    /** A lock with [plugin] added, replacing any entry with the same id. */
    public fun with(plugin: LockedPlugin): AwakePluginsLock = copy(plugins = plugins.filterNot { it.id == plugin.id } + plugin)

    /** A lock without the entry for [id]. */
    public fun without(id: String): AwakePluginsLock = copy(plugins = plugins.filterNot { it.id == id })

    /** Every runtime artifact the pinned plugins need, without duplicates, sorted by coordinates. */
    public fun runtimeModules(): List<PluginRuntimeModule> =
        plugins.flatMap { it.runtime }.distinct().sortedBy { it.coordinates }

    /** The file contents: pretty-printed, plugins sorted by id, ending with a newline. */
    public fun toJson(): String = lockJson.encodeToString(serializer(), copy(plugins = plugins.sortedBy { it.id })) + "\n"

    /** The file name, format version and parsing. */
    public companion object {
        /** The file name a project keeps the lock under, at its root. */
        public const val FILE_NAME: String = "plugins.lock.json"

        /** The newest lock file format this reads and writes. */
        public const val FORMAT_VERSION: Int = 1

        /** Parses `plugins.lock.json` contents. */
        public fun fromJson(jsonText: String): AwakePluginsLock = lockJson.decodeFromString(serializer(), jsonText)
    }
}

private val lockJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}
