/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A plugin declared by an Awake project manifest. */
@Serializable
data class AwakeProjectPluginReference(
    val id: String,
    val path: String,
    val version: String = "",
    val sha256: String? = null,
    val entrypointClass: String? = null,
    val required: Boolean = false,
)

/** The canonical project manifest shared by tools and runtimes. */
@Serializable
data class AwakeProjectManifest(
    @SerialName("\$schema")
    val schema: String? = null,
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String,
    /** Minimum engine version required to open this project; omitted means no declared minimum. */
    val minEngineVersion: String? = null,
    val entryScene: String,
    val author: String = "",
    val assetRoots: List<String> = listOf("assets"),
    val plugins: List<AwakeProjectPluginReference> = emptyList(),
)

/** A content pin in the canonical asset lock file. */
@Serializable
data class AwakeAssetLockEntry(
    val sha256: String,
    val sizeBytes: Long? = null,
)

/** The canonical asset lock file shared by tools and runtimes. */
@Serializable
data class AwakeAssetsLock(
    @SerialName("\$schema")
    val schema: String? = null,
    val formatVersion: Int = 1,
    val assets: Map<String, AwakeAssetLockEntry> = emptyMap(),
)

private val projectSemverPattern = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")
private val projectSemverParserPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
private val projectSha256Pattern = Regex("^[0-9a-f]{64}$")
private val projectIdPattern = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9-]+)+$")
private val projectDrivePathPattern = Regex("^[A-Za-z]:.*")

/** Pure JSON, SemVer, and structural validation for the project contract. */
object AwakeProjectValidator {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    fun encodeManifest(manifest: AwakeProjectManifest): String =
        json.encodeToString(AwakeProjectManifest.serializer(), manifest)

    fun decodeManifest(value: String): AwakeProjectManifest =
        json.decodeFromString(AwakeProjectManifest.serializer(), value)

    fun encodeAssetsLock(lock: AwakeAssetsLock): String =
        json.encodeToString(AwakeAssetsLock.serializer(), lock)

    fun decodeAssetsLock(value: String): AwakeAssetsLock =
        json.decodeFromString(AwakeAssetsLock.serializer(), value)

    fun isCompatible(manifest: AwakeProjectManifest, currentEngineVersion: String): Boolean =
        manifest.minEngineVersion?.let { parseSemVer(currentEngineVersion) >= parseSemVer(it) } ?: true

    internal fun parseSemVer(version: String): SemVer {
        val match = projectSemverParserPattern.matchEntire(version)
            ?: throw IllegalArgumentException("Invalid SemVer: $version")
        return SemVer(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
            preRelease = match.groupValues[4],
        )
    }

    internal data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            val numeric = compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
            if (numeric != 0) return numeric
            return when {
                preRelease.isEmpty() && other.preRelease.isNotEmpty() -> 1
                preRelease.isNotEmpty() && other.preRelease.isEmpty() -> -1
                else -> preRelease.compareTo(other.preRelease)
            }
        }
    }

    fun manifestIssues(manifest: AwakeProjectManifest): List<String> = buildList {
        if (manifest.formatVersion != 1) add("formatVersion must be 1")
        if (!manifest.id.matches(projectIdPattern)) add("id must be a reverse-domain identifier")
        if (manifest.name.isBlank()) add("name must not be blank")
        if (!manifest.version.matches(projectSemverPattern)) add("version must be semantic version")
        if (manifest.minEngineVersion != null && !manifest.minEngineVersion.matches(projectSemverPattern)) {
            add("minEngineVersion must be semantic version")
        }
        if (!isSafeProjectPath(manifest.entryScene)) {
            add("entryScene must be a safe project-relative path")
        }
        if (manifest.assetRoots.isEmpty()) add("assetRoots must not be empty")
        addAll(assetRootIssues(manifest.assetRoots))
        addAll(pluginIssues(manifest.plugins))
    }

    fun assetsLockIssues(lock: AwakeAssetsLock): List<String> = buildList {
        if (lock.formatVersion != 1) {
            add("formatVersion must be 1")
        }
        lock.assets.forEach { (path, pin) ->
            if (!isSafeProjectPath(path)) {
                add("asset path '$path' must be a safe project-relative path")
            }
            if (!pin.sha256.matches(projectSha256Pattern)) {
                add("asset '$path' must have a lowercase SHA-256 digest")
            }
            if (pin.sizeBytes != null && pin.sizeBytes < 0) {
                add("asset '$path' sizeBytes must not be negative")
            }
        }
    }

    fun isSafeProjectPath(path: String): Boolean {
        if (path.isBlank()) return false
        val hasInvalidPrefix = '\\' in path || path.startsWith('/') || path.matches(projectDrivePathPattern)
        return !hasInvalidPrefix && path.split('/').none(::isUnsafePathSegment)
    }

    private fun isUnsafePathSegment(segment: String): Boolean =
        segment.isBlank() || segment == "." || segment == ".."
}

internal fun isSha256Digest(value: String): Boolean = value.matches(projectSha256Pattern)

private fun assetRootIssues(assetRoots: List<String>): List<String> = buildList {
    if (assetRoots.distinct().size != assetRoots.size) {
        add("assetRoots must not contain duplicates")
    }
    assetRoots.forEachIndexed { index, root ->
        if (!AwakeProjectValidator.isSafeProjectPath(root)) add("assetRoots[$index] must be a safe project-relative path")
    }
}

private fun pluginIssues(plugins: List<AwakeProjectPluginReference>): List<String> = buildList {
    if (plugins.map { it.id }.distinct().size != plugins.size) {
        add("plugins must not contain duplicate ids")
    }
    plugins.forEachIndexed { index, plugin ->
        if (!plugin.id.matches(projectIdPattern)) {
            add("plugins[$index].id must be a reverse-domain identifier")
        }
        if (!AwakeProjectValidator.isSafeProjectPath(plugin.path)) {
            add("plugins[$index].path must be a safe project-relative path")
        }
        if (plugin.version.isNotBlank() && !plugin.version.matches(projectSemverPattern)) {
            add("plugins[$index].version must be semantic version")
        }
        if (plugin.sha256 != null && !plugin.sha256.matches(projectSha256Pattern)) {
            add("plugins[$index].sha256 must be a lowercase SHA-256 digest")
        }
        if (plugin.entrypointClass != null && plugin.entrypointClass.isBlank()) {
            add("plugins[$index].entrypointClass must not be blank")
        }
    }
}
