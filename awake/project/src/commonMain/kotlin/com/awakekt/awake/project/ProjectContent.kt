/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.serialization.Serializable

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

    fun assetsLockIssues(lock: AwakeAssetsLock): List<String> =
        AwakeProjectValidator.assetsLockIssues(lock)

    fun isUnderAssetRoot(path: String, assetRoots: List<String>): Boolean =
        assetRoots.any { root -> path == root || path.startsWith("$root/") }

    fun indexIssues(index: ProjectIndex): List<String> = buildList {
        if (index.formatVersion != 2) add("formatVersion must be 2")
        if (index.rootPath.isBlank()) add("rootPath must not be blank")
        val paths = mutableSetOf<String>()
        index.files.forEachIndexed { index, entry ->
            if (!AwakeProjectValidator.isSafeProjectPath(entry.path)) {
                add("files[$index].path must be a safe project-relative path")
            }
            if (!paths.add(entry.path)) add("duplicate indexed path: ${entry.path}")
            if (entry.sizeBytes < 0) add("files[$index].sizeBytes must not be negative")
            if (!isSha256Digest(entry.sha256)) {
                add("files[$index].sha256 must be a lowercase SHA-256 digest")
            }
            if (entry.url.isBlank()) add("files[$index].url must not be blank")
        }
    }
}
