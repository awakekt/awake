/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.serialization.Serializable

/** Severity of a project-content diagnostic. */
enum class ProjectIssueSeverity {
    ERROR,
    WARNING,
}

/** Stable machine-readable project-content diagnostic codes. */
enum class ProjectIssueCode(val value: String) {
    INVALID_MANIFEST("invalid_manifest"),
    UNSAFE_PATH("unsafe_path"),
    ENTRY_SCENE_MISSING("entry_scene_missing"),
    ASSET_REFERENCE_MISSING("asset_reference_missing"),
    ASSET_LOCK_INVALID("asset_lock_invalid"),
    ASSET_HASH_MISMATCH("asset_hash_mismatch"),
    INDEX_INVALID("index_invalid"),
    DUPLICATE_INDEX_PATH("duplicate_index_path"),
    REQUIRED_PLUGIN_MISSING("required_plugin_missing"),
}

/** A stable, path-aware diagnostic shared by runtime, tooling, and CI. */
data class ProjectContentIssue(
    val code: ProjectIssueCode,
    val message: String,
    val path: String? = null,
    val severity: ProjectIssueSeverity = ProjectIssueSeverity.ERROR,
) {
    val codeValue: String get() = code.value
}

/** Build-time and transport-neutral configuration for an Awake project content tree. */
data class ProjectContentSpec(
    val manifestPath: String = "awake.project.json",
    val assetsLockPath: String = "assets.lock.json",
    val assetRoots: List<String> = listOf("assets"),
    val indexRoots: List<String> = listOf("assets", "scenes", "plugins"),
    val indexRootPath: String = "/awake-project",
)

/** Metadata-only browser transport index. Asset bytes are fetched from [files] URLs on demand. */
@Serializable
data class ProjectIndex(
    val formatVersion: Int = 2,
    val rootPath: String = "/awake-project",
    val files: List<ProjectIndexEntry> = emptyList(),
)

/** One project-relative file in a [ProjectIndex]. */
@Serializable
data class ProjectIndexEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
)

/** Result shape shared by project-content tooling and CI integrations. */
data class ProjectContentReport(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val fileCount: Int = 0,
    val assetCount: Int = 0,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Generic project-content rules shared by hosts and build tooling. */
object ProjectContentValidator {
    fun manifestIssues(manifest: AwakeProjectManifest): List<String> =
        AwakeProjectValidator.manifestIssues(manifest)

    fun manifestIssueDetails(manifest: AwakeProjectManifest): List<ProjectContentIssue> =
        AwakeProjectValidator.manifestIssueDetails(manifest)

    fun assetsLockIssues(lock: AwakeAssetsLock): List<String> =
        AwakeProjectValidator.assetsLockIssues(lock)

    fun assetsLockIssueDetails(lock: AwakeAssetsLock): List<ProjectContentIssue> =
        AwakeProjectValidator.assetsLockIssueDetails(lock)

    fun isUnderAssetRoot(path: String, assetRoots: List<String>): Boolean =
        assetRoots.any { root -> path == root || path.startsWith("$root/") }

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

    fun indexIssues(index: ProjectIndex): List<String> =
        indexIssueDetails(index).map { it.message }
}
