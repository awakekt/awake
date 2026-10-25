/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

private val opaqueIdPattern = Regex("^[0-9a-f]{${ProjectPathIds.ID_LENGTH}}$")

/** [ProjectContentValidator.indexIssueDetails]: every rule a [ProjectIndex] of either version follows. */
internal fun projectIndexIssueDetails(index: ProjectIndex): List<ProjectContentIssue> = buildList {
    val bundled = index.formatVersion == ProjectIndex.BUNDLED_FORMAT_VERSION
    if (index.formatVersion != ProjectIndex.LOOSE_FORMAT_VERSION && !bundled) {
        add(indexInvalid("formatVersion must be ${ProjectIndex.LOOSE_FORMAT_VERSION} or ${ProjectIndex.BUNDLED_FORMAT_VERSION}"))
    }
    if (index.rootPath.isBlank()) add(indexInvalid("rootPath must not be blank"))
    if (!bundled && (index.bundles.isNotEmpty() || index.pathIds != null)) {
        add(indexInvalid("bundles and pathIds need formatVersion ${ProjectIndex.BUNDLED_FORMAT_VERSION}"))
    }
    index.pathIds?.let { ids ->
        if (ids.scheme != ProjectPathIds.SCHEME) add(indexInvalid("pathIds.scheme must be ${ProjectPathIds.SCHEME}"))
        if (ids.salt.isBlank()) add(indexInvalid("pathIds.salt must not be blank"))
    }
    val bundles = bundleIssues(index.bundles, this)
    val keys = mutableSetOf<String>()
    index.files.forEachIndexed { position, entry ->
        addAll(entryKeyIssues(position, entry, index.pathIds != null, bundled))
        if (!keys.add(entry.key)) {
            add(ProjectContentIssue(ProjectIssueCode.DUPLICATE_INDEX_PATH, "duplicate indexed path: ${entry.key}", path = entry.key))
        }
        if (entry.sizeBytes < 0) add(indexInvalid("files[$position].sizeBytes must not be negative"))
        if (!isSha256Digest(entry.sha256)) add(indexInvalid("files[$position].sha256 must be a lowercase SHA-256 digest"))
        addAll(entryLocationIssues(position, entry, bundles))
    }
}

/** Each bundle's own rules, added to [issues]; answers the bundles by SHA-256. */
private fun bundleIssues(bundles: List<ProjectIndexBundle>, issues: MutableList<ProjectContentIssue>): Map<String, ProjectIndexBundle> {
    val bySha = mutableMapOf<String, ProjectIndexBundle>()
    bundles.forEachIndexed { position, bundle ->
        if (!isSha256Digest(bundle.sha256)) issues += indexInvalid("bundles[$position].sha256 must be a lowercase SHA-256 digest")
        if (bundle.sizeBytes < ContentBundleFormat.HEADER_SIZE) {
            issues += indexInvalid("bundles[$position].sizeBytes must be at least ${ContentBundleFormat.HEADER_SIZE}")
        }
        if (bundle.url.isBlank()) issues += indexInvalid("bundles[$position].url must not be blank")
        if (bySha.put(bundle.sha256, bundle) != null) issues += indexInvalid("bundles[$position] is listed twice: ${bundle.sha256}")
    }
    return bySha
}

private fun entryKeyIssues(position: Int, entry: ProjectIndexEntry, opaque: Boolean, bundled: Boolean): List<ProjectContentIssue> = buildList {
    when {
        entry.id != null && !bundled -> add(indexInvalid("files[$position].id needs formatVersion ${ProjectIndex.BUNDLED_FORMAT_VERSION}"))
        opaque && (entry.id == null || !entry.id.matches(opaqueIdPattern)) ->
            add(indexInvalid("files[$position].id must be ${ProjectPathIds.ID_LENGTH} lowercase hex digits"))
        opaque && entry.path.isNotEmpty() -> add(indexInvalid("files[$position].path must be empty in an index with pathIds"))
        !opaque && entry.id != null -> add(indexInvalid("files[$position].id needs pathIds"))
        !opaque && !AwakeProjectValidator.isSafeProjectPath(entry.path) -> add(
            ProjectContentIssue(
                code = ProjectIssueCode.UNSAFE_PATH,
                message = "files[$position].path must be a safe project-relative path",
                path = entry.path,
            ),
        )
    }
}

private fun entryLocationIssues(position: Int, entry: ProjectIndexEntry, bundles: Map<String, ProjectIndexBundle>): List<ProjectContentIssue> =
    buildList {
        if (entry.bundle == null) {
            if (entry.url.isBlank()) add(indexInvalid("files[$position].url must not be blank"))
            if (entry.offset != null) add(indexInvalid("files[$position].offset needs a bundle"))
            return@buildList
        }
        val bundle = bundles[entry.bundle]
        val offset = entry.offset
        when {
            bundle == null -> add(indexInvalid("files[$position].bundle names no listed bundle: ${entry.bundle}"))
            offset == null -> add(indexInvalid("files[$position].offset must be given for a bundle member"))
            offset < ContentBundleFormat.HEADER_SIZE || offset > bundle.sizeBytes - maxOf(entry.sizeBytes, 0) ->
                add(indexInvalid("files[$position] must lie inside its bundle: offset $offset, ${entry.sizeBytes} bytes"))
        }
    }

private fun indexInvalid(message: String) = ProjectContentIssue(ProjectIssueCode.INDEX_INVALID, message)
