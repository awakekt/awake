/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

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
    val indexRoots: List<String> = listOf("assets", "scenes", "plugins"),
    val indexRootPath: String = "/project",
)

/**
 * Metadata-only browser transport index. Asset bytes are fetched from [files] URLs on demand.
 *
 * @property formatVersion Transport index format version (must be 2).
 * @property rootPath Base URI mount path for indexed files.
 * @property files List of indexed file entries.
 */
@Serializable
data class ProjectIndex(
    val formatVersion: Int = 2,
    val rootPath: String = "/project",
    val files: List<ProjectIndexEntry> = emptyList(),
)

/**
 * One project-relative file in a [ProjectIndex].
 *
 * @property path Project-relative path of the file.
 * @property sizeBytes Size of the file in bytes.
 * @property sha256 Lowercase hex-encoded SHA-256 digest of the file contents.
 * @property url Resolvable URL from which the file payload can be fetched.
 */
@Serializable
data class ProjectIndexEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
)

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

    /** Returns `true` if [path] is contained within one of the [assetRoots]. */
    fun isUnderAssetRoot(path: String, assetRoots: List<String>): Boolean =
        assetRoots.any { root -> path == root || path.startsWith("$root/") }

    /** Validates [index] structure and returns detailed diagnostic issues. */
    fun indexIssueDetails(index: ProjectIndex): List<ProjectContentIssue> = buildList {
        if (index.formatVersion != 2) {
            add(ProjectContentIssue(ProjectIssueCode.INDEX_INVALID, "formatVersion must be 2"))
        }
        if (index.rootPath.isBlank()) {
            add(ProjectContentIssue(ProjectIssueCode.INDEX_INVALID, "rootPath must not be blank"))
        }
        val paths = mutableSetOf<String>()
        index.files.forEachIndexed { index, entry ->
            if (!AwakeProjectValidator.isSafeProjectPath(entry.path)) {
                add(
                    ProjectContentIssue(
                        code = ProjectIssueCode.UNSAFE_PATH,
                        message = "files[$index].path must be a safe project-relative path",
                        path = entry.path,
                    ),
                )
            }
            if (!paths.add(entry.path)) {
                add(
                    ProjectContentIssue(
                        code = ProjectIssueCode.DUPLICATE_INDEX_PATH,
                        message = "duplicate indexed path: ${entry.path}",
                        path = entry.path,
                    ),
                )
            }
            if (entry.sizeBytes < 0) {
                add(ProjectContentIssue(ProjectIssueCode.INDEX_INVALID, "files[$index].sizeBytes must not be negative"))
            }
            if (!isSha256Digest(entry.sha256)) {
                add(ProjectContentIssue(ProjectIssueCode.INDEX_INVALID, "files[$index].sha256 must be a lowercase SHA-256 digest"))
            }
            if (entry.url.isBlank()) {
                add(ProjectContentIssue(ProjectIssueCode.INDEX_INVALID, "files[$index].url must not be blank"))
            }
        }
    }

    /** Validates [index] structure and returns error message strings. */
    fun indexIssues(index: ProjectIndex): List<String> =
        indexIssueDetails(index).map { it.message }
}
