/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256
import kotlinx.serialization.Serializable

/** Severity of a project-content diagnostic. */
enum class ProjectIssueSeverity {
    /** Critical validation error preventing project execution or packaging. */
    ERROR,

    /** Non-fatal warning indicating sub-optimal configuration or potential issues. */
    WARNING,
}

/**
 * Stable machine-readable project-content diagnostic codes.
 *
 * @property value The string representation of the issue code.
 */
enum class ProjectIssueCode(val value: String) {
    /** Manifest is structurally invalid or missing required fields. */
    INVALID_MANIFEST("invalid_manifest"),

    /** Path contains illegal characters, absolute references, or attempts directory traversal. */
    UNSAFE_PATH("unsafe_path"),

    /** The declared entry scene does not exist in the project tree. */
    ENTRY_SCENE_MISSING("entry_scene_missing"),

    /** An asset referenced by a scene or manifest is not found. */
    ASSET_REFERENCE_MISSING("asset_reference_missing"),

    /** The assets lockfile is malformed or invalid. */
    ASSET_LOCK_INVALID("asset_lock_invalid"),

    /** The computed asset hash differs from the pin in the lockfile. */
    ASSET_HASH_MISMATCH("asset_hash_mismatch"),

    /** The project file index is malformed. */
    INDEX_INVALID("index_invalid"),

    /** The project file index contains duplicate file paths. */
    DUPLICATE_INDEX_PATH("duplicate_index_path"),

    /** A required plugin declared by the manifest is missing. */
    REQUIRED_PLUGIN_MISSING("required_plugin_missing"),

    /** A scene uses a tag the manifest's `tags` list does not name, often a typo. A warning. */
    UNLISTED_TAG("unlisted_tag"),

    /**
     * A scene's sprite or tilemap names a sorting layer the manifest's `sortingLayers` list does not, so
     * it draws on the default layer: often a typo. A warning.
     */
    UNLISTED_SORTING_LAYER("unlisted_sorting_layer"),
}

/**
 * A stable, path-aware diagnostic shared by runtime, tooling, and CI.
 *
 * @property code Diagnostic issue classification code.
 * @property message Human-readable description of the issue.
 * @property path Project-relative path associated with the issue, if applicable.
 * @property severity Diagnostic severity level.
 */
data class ProjectContentIssue(
    val code: ProjectIssueCode,
    val message: String,
    val path: String? = null,
    val severity: ProjectIssueSeverity = ProjectIssueSeverity.ERROR,
) {
    /** The raw string value of [code]. */
    val codeValue: String get() = code.value
}

/**
 * Build-time and transport-neutral configuration for an Awake project content tree.
 *
 * @property manifestPath Expected project-relative path to the project manifest file.
 * @property assetsLockPath Expected project-relative path to the assets lock file.
 * @property assetRoots List of asset root directories.
 * @property indexRoots Directories included when generating the transport index.
 * @property indexRootPath Base URI mount path for the indexed project.
 */
data class ProjectContentSpec(
    val manifestPath: String = "awake.project.json",
    val assetsLockPath: String = "assets.lock.json",
    val assetRoots: List<String> = listOf("assets"),
    val indexRoots: List<String> = listOf("assets", "scenes", "plugins", "strings"),
    val indexRootPath: String = "/project",
)

/**
 * Metadata-only transport index: what a published project holds, and where each file's bytes are.
 * Asset bytes are fetched on demand, from a loose file's `url` or from inside a content bundle.
 *
 * [formatVersion] 2 lists loose files only, each at its own `url`. [formatVersion] 3, which
 * [ContentBundleWriter] writes, adds [bundles]: an entry may then name a bundle and its offset in it
 * instead of a `url`, and with [pathIds] set, entries carry opaque ids instead of readable paths.
 * [formatVersion] 4 is a bundled index whose members are sealed, as [encryption] names; every entry
 * is then a bundle member, and its sealed bytes run [ProjectIndexEntry.sizeBytes] plus
 * [ProjectBundleEncryption.TAG_BYTES] from its offset. An engine that predates a version refuses it
 * rather than misreading it.
 *
 * @property formatVersion Transport index format version: [LOOSE_FORMAT_VERSION] or [BUNDLED_FORMAT_VERSION].
 * @property rootPath Base URI mount path for indexed files.
 * @property files List of indexed file entries.
 * @property bundles The content bundles that [files] entries point into; empty in a loose index.
 * @property pathIds How each entry's opaque id is derived from its path, or null when entries carry paths.
 * @property encryption How bundle members are sealed and which key opens them; null when they aren't.
 */
@Serializable
data class ProjectIndex(
    val formatVersion: Int = LOOSE_FORMAT_VERSION,
    val rootPath: String = "/project",
    val files: List<ProjectIndexEntry> = emptyList(),
    val bundles: List<ProjectIndexBundle> = emptyList(),
    val pathIds: ProjectPathIds? = null,
    val encryption: ProjectBundleEncryption? = null,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        formatVersion: Int = LOOSE_FORMAT_VERSION,
        rootPath: String = "/project",
        files: List<ProjectIndexEntry> = emptyList(),
        bundles: List<ProjectIndexBundle> = emptyList(),
        pathIds: ProjectPathIds? = null,
    ) : this(formatVersion, rootPath, files, bundles, pathIds, null)

    /** The copy that predates [encryption], kept for binary compatibility. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        formatVersion: Int = this.formatVersion,
        rootPath: String = this.rootPath,
        files: List<ProjectIndexEntry> = this.files,
        bundles: List<ProjectIndexBundle> = this.bundles,
        pathIds: ProjectPathIds? = this.pathIds,
    ): ProjectIndex = ProjectIndex(formatVersion, rootPath, files, bundles, pathIds, encryption)

    /**
     * What [ContentCache] keeps [entry]'s bytes under: its SHA-256, or, in an encrypted index, a
     * SHA-256 of the key id and that, since the cache then holds the sealed bytes, which differ per key.
     */
    fun cacheKeyOf(entry: ProjectIndexEntry): String =
        encryption?.let { Sha256.digestHex("awake-awkb-v2-cache\n${it.keyId}\n${entry.sha256}") } ?: entry.sha256

    /** How many bytes [entry] takes where it is stored: sealed, in an encrypted index. */
    fun storedSizeOf(entry: ProjectIndexEntry): Long =
        entry.sizeBytes + if (encryption != null) ProjectBundleEncryption.TAG_BYTES else 0

    /** The index format versions this engine reads. */
    companion object {
        /** An index of loose files, each at its own `url`. */
        const val LOOSE_FORMAT_VERSION: Int = 2

        /** An index whose entries may point into content bundles and may carry opaque ids. */
        const val BUNDLED_FORMAT_VERSION: Int = 3

        /** A bundled index whose members are sealed, as [ProjectIndex.encryption] names. */
        const val ENCRYPTED_FORMAT_VERSION: Int = 4
    }
}

/**
 * One project-relative file in a [ProjectIndex].
 *
 * A loose file has a [url] and no [bundle]. A bundle member has a [bundle] and an [offset] instead,
 * and its bytes are the [sizeBytes] bytes at [offset] in that bundle. In an index with
 * [ProjectIndex.pathIds], [id] replaces [path], which is then empty.
 *
 * @property path Project-relative path of the file; empty when [id] stands in for it.
 * @property sizeBytes Size of the file in bytes.
 * @property sha256 Lowercase hex-encoded SHA-256 digest of the file contents.
 * @property url Resolvable URL from which a loose file's payload can be fetched; empty for a bundle member.
 * @property id The opaque id [ProjectPathIds.idOf] derives from the path, or null when [path] is given.
 * @property bundle The SHA-256 of the [ProjectIndexBundle] holding this file, or null for a loose file.
 * @property offset Where this file's bytes start in [bundle], from the bundle's first byte; null for a loose file.
 */
@Serializable
data class ProjectIndexEntry(
    val path: String = "",
    val sizeBytes: Long,
    val sha256: String,
    val url: String = "",
    val id: String? = null,
    val bundle: String? = null,
    val offset: Long? = null,
) {
    /** What the index looks this file up by: its [id] when it has one, otherwise its [path]. */
    val key: String get() = id ?: path
}

/**
 * One content bundle a [ProjectIndex] lists: an archive of many files in the [ContentBundleFormat],
 * stored and fetched by its own SHA-256 like any other content.
 *
 * @property sha256 Lowercase hex-encoded SHA-256 digest of the whole bundle, which entries name it by.
 * @property sizeBytes Size of the whole bundle in bytes.
 * @property url Resolvable URL from which the bundle can be fetched.
 */
@Serializable
data class ProjectIndexBundle(
    val sha256: String,
    val sizeBytes: Long,
    val url: String,
)

/**
 * How a [ProjectIndex] with opaque ids derives each entry's id from its path, so the published index
 * and its bundles list no readable paths while a game still asks for files by path.
 *
 * An id is the first 32 hex digits of the SHA-256 of [salt], a line feed, and the path's UTF-8 bytes.
 * The salt is published with the index, so ids hide the file list from a reader of the index; they
 * don't stop someone who guesses a path from confirming it.
 *
 * @property salt Mixed into every id, so the same path gets different ids in different projects;
 * a project's id, for example. Not blank.
 * @property scheme The derivation's name; [SCHEME] is the only one.
 */
@Serializable
data class ProjectPathIds(
    val salt: String,
    val scheme: String = SCHEME,
) {
    /** The opaque id of [path]: 32 lowercase hex digits. */
    fun idOf(path: String): String = Sha256.digestHex("$salt\n${path.removePrefix("/")}").substring(0, ID_LENGTH)

    /** The supported id schemes. */
    companion object {
        /** The first 128 bits of a salted SHA-256, as lowercase hex. */
        const val SCHEME: String = "sha256-128"

        /** How many hex digits an id has. */
        const val ID_LENGTH: Int = 32
    }
}

/**
 * Result shape shared by project-content tooling and CI integrations.
 *
 * @property errors List of fatal error messages found during content inspection.
 * @property warnings List of non-fatal warning messages.
 * @property fileCount Total number of project files processed.
 * @property assetCount Total number of tracked project assets processed.
 */
data class ProjectContentReport(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val fileCount: Int = 0,
    val assetCount: Int = 0,
) {
    /** Returns `true` if no validation errors were encountered. */
    val isValid: Boolean get() = errors.isEmpty()
}

/** Generic project-content rules shared by hosts and build tooling. */
object ProjectContentValidator {
    /** Returns a list of error messages for [manifest], or empty if valid. */
    fun manifestIssues(manifest: AwakeProjectManifest): List<String> =
        AwakeProjectValidator.manifestIssues(manifest)

    /** Returns detailed diagnostic issues for [manifest]. */
    fun manifestIssueDetails(manifest: AwakeProjectManifest): List<ProjectContentIssue> =
        projectManifestIssueDetails(manifest)

    /** Returns a list of error messages for [lock], or empty if valid. */
    fun assetsLockIssues(lock: AwakeAssetsLock): List<String> =
        AwakeProjectValidator.assetsLockIssues(lock)

    /** Returns detailed diagnostic issues for [lock]. */
    fun assetsLockIssueDetails(lock: AwakeAssetsLock): List<ProjectContentIssue> =
        projectAssetsLockIssueDetails(lock)

    /**
     * A warning for each of [sceneTags], the tags the scene at [path] uses, that the manifest's `tags`
     * list does not name: a guard against a typo, never a refusal. A manifest with no list opts out
     * and gets none.
     */
    fun unlistedTagIssues(manifest: AwakeProjectManifest, sceneTags: Collection<String>, path: String? = null): List<ProjectContentIssue> {
        if (manifest.tags.isEmpty()) return emptyList()
        val listed = manifest.tags.toSet()
        return sceneTags.distinct().filterNot { it in listed }.map { tag ->
            ProjectContentIssue(
                code = ProjectIssueCode.UNLISTED_TAG,
                message = "tag \"$tag\" is not in the project's tags list",
                path = path,
                severity = ProjectIssueSeverity.WARNING,
            )
        }
    }

    /**
     * A warning for each of [layers], the sorting layers the sprites and tilemaps of the scene at [path]
     * name, that the manifest's `sortingLayers` list does not: such a one draws on the default layer.
     * The default layer, [DEFAULT_SORTING_LAYER], is always known. A manifest with no list opts out and
     * gets none.
     */
    fun unlistedSortingLayerIssues(
        manifest: AwakeProjectManifest,
        layers: Collection<String>,
        path: String? = null,
    ): List<ProjectContentIssue> {
        if (manifest.sortingLayers.isEmpty()) return emptyList()
        val listed = manifest.sortingLayers.toSet() + DEFAULT_SORTING_LAYER
        return layers.distinct().filterNot { it in listed }.map { layer ->
            ProjectContentIssue(
                code = ProjectIssueCode.UNLISTED_SORTING_LAYER,
                message = "sorting layer \"$layer\" is not in the project's sortingLayers list, so it draws on \"$DEFAULT_SORTING_LAYER\"",
                path = path,
                severity = ProjectIssueSeverity.WARNING,
            )
        }
    }

    /** Returns `true` if [path] is contained within one of the [assetRoots]. */
    fun isUnderAssetRoot(path: String, assetRoots: List<String>): Boolean =
        assetRoots.any { root -> path == root || path.startsWith("$root/") }

    /**
     * Validates [index] structure and returns detailed diagnostic issues: its format version, each
     * entry's path or id, size, SHA-256 and location, and, in a [ProjectIndex.BUNDLED_FORMAT_VERSION]
     * index, that each bundle member lies inside a listed bundle.
     */
    fun indexIssueDetails(index: ProjectIndex): List<ProjectContentIssue> = projectIndexIssueDetails(index)

    /** Validates [index] structure and returns error message strings. */
    fun indexIssues(index: ProjectIndex): List<String> =
        indexIssueDetails(index).map { it.message }
}
