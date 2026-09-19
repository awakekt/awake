/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A plugin declared by a version-one project manifest. */
@Serializable
data class AwakeProjectPluginReferenceV1(
    val id: String,
    val path: String,
    val version: String = "",
    val sha256: String? = null,
    val entrypointClass: String? = null,
    val required: Boolean = false,
)

/** The canonical version-one project manifest shared by tools and runtimes. */
@Serializable
data class AwakeProjectManifestV1(
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
    val plugins: List<AwakeProjectPluginReferenceV1> = emptyList(),
)

/** A content pin in the canonical version-one asset lock file. */
@Serializable
data class AwakeAssetLockEntryV1(
    val sha256: String,
    val sizeBytes: Long? = null,
)

/** The canonical version-one asset lock file shared by tools and runtimes. */
@Serializable
data class AwakeAssetsLockV1(
    @SerialName("\$schema")
    val schema: String? = null,
    val formatVersion: Int = 1,
    val assets: Map<String, AwakeAssetLockEntryV1> = emptyMap(),
)

/** Serialization and structural validation for the version-one project contract. */
object AwakeProjectV1Validator {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    private val semverPattern = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val projectIdPattern = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9-]+)+$")

    fun encodeManifest(manifest: AwakeProjectManifestV1): String =
        json.encodeToString(AwakeProjectManifestV1.serializer(), manifest)

    fun decodeManifest(value: String): AwakeProjectManifestV1 =
        json.decodeFromString(AwakeProjectManifestV1.serializer(), value)

    fun encodeAssetsLock(lock: AwakeAssetsLockV1): String =
        json.encodeToString(AwakeAssetsLockV1.serializer(), lock)

    fun decodeAssetsLock(value: String): AwakeAssetsLockV1 =
        json.decodeFromString(AwakeAssetsLockV1.serializer(), value)

    fun manifestIssues(manifest: AwakeProjectManifestV1): List<String> = buildList {
        if (manifest.formatVersion != 1) add("formatVersion must be 1")
        if (!manifest.id.matches(projectIdPattern)) add("id must be a reverse-domain identifier")
        if (manifest.name.isBlank()) add("name must not be blank")
        if (!manifest.version.matches(semverPattern)) add("version must be semantic version")
        if (manifest.minEngineVersion != null && !manifest.minEngineVersion.matches(semverPattern)) {
            add("minEngineVersion must be semantic version")
        }
        if (!isSafeProjectPath(manifest.entryScene)) {
            add("entryScene must be a safe project-relative path")
        }
        if (manifest.assetRoots.isEmpty()) add("assetRoots must not be empty")
        addAll(assetRootIssues(manifest.assetRoots))
        addAll(pluginIssues(manifest.plugins))
    }

    private fun assetRootIssues(assetRoots: List<String>): List<String> = buildList {
        assetRoots.forEachIndexed { index, root ->
            if (!isSafeProjectPath(root)) add("assetRoots[$index] must be a safe project-relative path")
        }
    }

    private fun pluginIssues(plugins: List<AwakeProjectPluginReferenceV1>): List<String> = buildList {
        plugins.forEachIndexed { index, plugin ->
            if (!plugin.id.matches(projectIdPattern)) {
                add("plugins[$index].id must be a reverse-domain identifier")
            }
            if (!isSafeProjectPath(plugin.path)) {
                add("plugins[$index].path must be a safe project-relative path")
            }
            if (plugin.version.isNotBlank() && !plugin.version.matches(semverPattern)) {
                add("plugins[$index].version must be semantic version")
            }
            if (plugin.sha256 != null && !plugin.sha256.matches(sha256Pattern)) {
                add("plugins[$index].sha256 must be a lowercase SHA-256 digest")
            }
            if (plugin.entrypointClass != null && plugin.entrypointClass.isBlank()) {
                add("plugins[$index].entrypointClass must not be blank")
            }
        }
    }

    fun assetsLockIssues(lock: AwakeAssetsLockV1): List<String> = buildList {
        if (lock.formatVersion != 1) {
            add("formatVersion must be 1")
        }
        lock.assets.forEach { (path, pin) ->
            if (!isSafeProjectPath(path)) {
                add("asset path '$path' must be a safe project-relative path")
            }
            if (!pin.sha256.matches(sha256Pattern)) {
                add("asset '$path' must have a lowercase SHA-256 digest")
            }
            if (pin.sizeBytes != null && pin.sizeBytes < 0) {
                add("asset '$path' sizeBytes must not be negative")
            }
        }
    }

    fun isSafeProjectPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith('/') || normalized.matches(Regex("^[A-Za-z]:.*"))) {
            return false
        }
        return normalized.split('/').none { segment -> segment.isBlank() || segment == ".." }
    }
}
