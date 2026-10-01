/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A plugin declared by an Awake project manifest.
 *
 * @property id Unique reverse-domain identifier of the plugin.
 * @property path Relative file path to the plugin bundle or archive.
 * @property version Semantic version string of the plugin.
 * @property sha256 Expected SHA-256 checksum of the plugin archive.
 * @property entrypointClass Fully qualified class name of the plugin entrypoint.
 * @property required Whether engine initialization requires this plugin to be present.
 */
@Serializable
data class AwakeProjectPluginReference(
    val id: String,
    val path: String,
    val version: String = "",
    val sha256: String? = null,
    val entrypointClass: String? = null,
    val required: Boolean = false,
)

/**
 * The canonical project manifest shared by tools and runtimes.
 *
 * @property schema Optional JSON schema URI.
 * @property formatVersion Format specification version (must be 1).
 * @property id Reverse-domain identifier of the project.
 * @property name Human-readable display name of the project.
 * @property version Semantic version string of the project.
 * @property minEngineVersion Minimum engine version required to open this project; omitted means no declared minimum.
 * @property entryScene Project-relative path to the initial scene document.
 * @property author Author or organization name.
 * @property assetRoots Project-relative directory paths scanned for project assets.
 * @property plugins List of declared plugin references.
 */
@Serializable
data class AwakeProjectManifest(
    @SerialName("\$schema")
    val schema: String? = null,
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String,
    val minEngineVersion: String? = null,
    val entryScene: String,
    val author: String = "",
    val assetRoots: List<String> = listOf("assets"),
    val plugins: List<AwakeProjectPluginReference> = emptyList(),
)

/**
 * A content pin in the canonical asset lock file.
 *
 * @property sha256 Hex-encoded SHA-256 digest of the asset content.
 * @property sizeBytes Size of the asset in bytes, or `null` if unmeasured.
 */
@Serializable
data class AwakeAssetLockEntry(
    val sha256: String,
    val sizeBytes: Long? = null,
)

/**
 * The canonical asset lock file shared by tools and runtimes.
 *
 * @property schema Optional JSON schema URI.
 * @property formatVersion Format specification version (must be 1).
 * @property assets Map of project-relative asset paths to their pinned hash and size records.
 */
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

    /** Encodes [manifest] into a JSON string. */
    fun encodeManifest(manifest: AwakeProjectManifest): String =
        json.encodeToString(AwakeProjectManifest.serializer(), manifest)

    /** Decodes [value] JSON string into an [AwakeProjectManifest]. */
    fun decodeManifest(value: String): AwakeProjectManifest =
        json.decodeFromString(AwakeProjectManifest.serializer(), value)

    /** Encodes [lock] into a JSON string. */
    fun encodeAssetsLock(lock: AwakeAssetsLock): String =
        json.encodeToString(AwakeAssetsLock.serializer(), lock)

    /** Decodes [value] JSON string into an [AwakeAssetsLock]. */
    fun decodeAssetsLock(value: String): AwakeAssetsLock =
        json.decodeFromString(AwakeAssetsLock.serializer(), value)

    /** Checks whether [manifest] is compatible with [currentEngineVersion]. */
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

    /**
     * Ordered the way Awake's own versions are cut: `alpha.10` after `alpha.9` (numeric parts as
     * numbers, which a plain string compare gets backwards), and a `-SNAPSHOT` just below the
     * version it leads to -- after the previous release, before this one.
     */
    internal data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String,
    ) : Comparable<SemVer> {
        private val snapshot: Boolean get() = preRelease == SNAPSHOT || preRelease.endsWith("-$SNAPSHOT")
        private val track: String get() = preRelease.removeSuffix(SNAPSHOT).removeSuffix("-")

        override fun compareTo(other: SemVer): Int {
            val numeric = compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
            if (numeric != 0) return numeric
            val byTrack = when {
                track == other.track -> 0
                // A release line (no alpha/beta/rc) comes after every pre-release of it.
                track.isEmpty() -> 1
                other.track.isEmpty() -> -1
                else -> compareIdentifiers(track.split('.'), other.track.split('.'))
            }
            return if (byTrack != 0) byTrack else compareValues(!snapshot, !other.snapshot)
        }

        private fun compareIdentifiers(left: List<String>, right: List<String>): Int {
            for ((a, b) in left.zip(right)) {
                val (na, nb) = a.toIntOrNull() to b.toIntOrNull()
                val order = when {
                    na != null && nb != null -> na.compareTo(nb)
                    na != null -> -1
                    nb != null -> 1
                    else -> a.compareTo(b)
                }
                if (order != 0) return order
            }
            return left.size.compareTo(right.size)
        }

        private companion object {
            const val SNAPSHOT = "SNAPSHOT"
        }
    }

    /** Validates [manifest] and returns human-readable diagnostic error messages. */
    fun manifestIssues(manifest: AwakeProjectManifest): List<String> =
        projectManifestIssueDetails(manifest).map { it.message }

    /** Validates [lock] and returns human-readable diagnostic error messages. */
    fun assetsLockIssues(lock: AwakeAssetsLock): List<String> =
        projectAssetsLockIssueDetails(lock).map { it.message }

    /** Returns `true` if [path] is safe and does not escape the project directory. */
    fun isSafeProjectPath(path: String): Boolean {
        if (path.isBlank()) return false
        val hasInvalidPrefix = '\\' in path || path.startsWith('/') || path.matches(projectDrivePathPattern)
        return !hasInvalidPrefix && path.split('/').none(::isUnsafePathSegment)
    }

    private fun isUnsafePathSegment(segment: String): Boolean =
        segment.isBlank() || segment == "." || segment == ".."
}

internal fun isSha256Digest(value: String): Boolean = value.matches(projectSha256Pattern)

internal fun projectManifestIssueDetails(manifest: AwakeProjectManifest): List<ProjectContentIssue> = buildList {
    if (manifest.formatVersion != 1) add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "formatVersion must be 1"))
    if (!manifest.id.matches(projectIdPattern)) {
        add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "id must be a reverse-domain identifier"))
    }
    if (manifest.name.isBlank()) add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "name must not be blank"))
    if (!manifest.version.matches(projectSemverPattern)) {
        add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "version must be semantic version"))
    }
    if (manifest.minEngineVersion != null && !manifest.minEngineVersion.matches(projectSemverPattern)) {
        add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "minEngineVersion must be semantic version"))
    }
    if (!AwakeProjectValidator.isSafeProjectPath(manifest.entryScene)) {
        add(
            ProjectContentIssue(
                code = ProjectIssueCode.UNSAFE_PATH,
                message = "entryScene must be a safe project-relative path",
                path = manifest.entryScene,
            ),
        )
    }
    if (manifest.assetRoots.isEmpty()) {
        add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "assetRoots must not be empty"))
    }
    addAll(assetRootIssues(manifest.assetRoots))
    addAll(pluginIssues(manifest.plugins))
}

internal fun projectAssetsLockIssueDetails(lock: AwakeAssetsLock): List<ProjectContentIssue> = buildList {
    if (lock.formatVersion != 1) {
        add(ProjectContentIssue(ProjectIssueCode.ASSET_LOCK_INVALID, "formatVersion must be 1"))
    }
    lock.assets.forEach { (path, pin) ->
        if (!AwakeProjectValidator.isSafeProjectPath(path)) {
            add(
                ProjectContentIssue(
                    code = ProjectIssueCode.UNSAFE_PATH,
                    message = "asset path '$path' must be a safe project-relative path",
                    path = path,
                ),
            )
        }
        if (!pin.sha256.matches(projectSha256Pattern)) {
            add(ProjectContentIssue(ProjectIssueCode.ASSET_LOCK_INVALID, "asset '$path' must have a lowercase SHA-256 digest", path))
        }
        if (pin.sizeBytes != null && pin.sizeBytes < 0) {
            add(ProjectContentIssue(ProjectIssueCode.ASSET_LOCK_INVALID, "asset '$path' sizeBytes must not be negative", path))
        }
    }
}

private fun assetRootIssues(assetRoots: List<String>): List<ProjectContentIssue> = buildList {
    if (assetRoots.distinct().size != assetRoots.size) {
        add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "assetRoots must not contain duplicates"))
    }
    assetRoots.forEachIndexed { index, root ->
        if (!AwakeProjectValidator.isSafeProjectPath(root)) {
            add(
                ProjectContentIssue(
                    code = ProjectIssueCode.UNSAFE_PATH,
                    message = "assetRoots[$index] must be a safe project-relative path",
                    path = root,
                ),
            )
        }
    }
}

private fun pluginIssues(plugins: List<AwakeProjectPluginReference>): List<ProjectContentIssue> = buildList {
    if (plugins.map { it.id }.distinct().size != plugins.size) {
        add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "plugins must not contain duplicate ids"))
    }
    plugins.forEachIndexed { index, plugin ->
        if (!plugin.id.matches(projectIdPattern)) {
            add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "plugins[$index].id must be a reverse-domain identifier"))
        }
        if (!AwakeProjectValidator.isSafeProjectPath(plugin.path)) {
            add(
                ProjectContentIssue(
                    code = ProjectIssueCode.UNSAFE_PATH,
                    message = "plugins[$index].path must be a safe project-relative path",
                    path = plugin.path,
                ),
            )
        }
        if (plugin.version.isNotBlank() && !plugin.version.matches(projectSemverPattern)) {
            add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "plugins[$index].version must be semantic version"))
        }
        if (plugin.sha256 != null && !plugin.sha256.matches(projectSha256Pattern)) {
            add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "plugins[$index].sha256 must be a lowercase SHA-256 digest"))
        }
        if (plugin.entrypointClass != null && plugin.entrypointClass.isBlank()) {
            add(ProjectContentIssue(ProjectIssueCode.INVALID_MANIFEST, "plugins[$index].entrypointClass must not be blank"))
        }
    }
}
