/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.ContentBundleDirectoryWriter
import com.awakekt.awake.project.ContentBundleGrouping
import com.awakekt.awake.project.ContentBundleWriter
import com.awakekt.awake.project.ProjectPathIds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/**
 * `awake bundle`: packs the files a loose project index lists, read from [projectRoot], into content
 * bundles under [output], with the bundled index beside them, so a published project loads in one
 * request per bundle.
 *
 * @param projectRoot The project folder the index's paths are relative to.
 * @param output The folder the bundles and the bundled index are written to.
 */
internal class ProjectBundle(private val projectRoot: File, private val output: File) {
    /** What one run wrote. */
    class Report(val index: File, val bundles: Int, val files: Int, val bytes: Long, val written: Int)

    /**
     * Packs [indexFile]'s files with [writer], writing only bundles not already in [output], and
     * answers what it wrote. With [prune], `.awkb` files under [output] that the new index doesn't
     * list are deleted; without it they stay, as a publish folder serving the last release needs.
     */
    fun run(indexFile: File, writer: ContentBundleWriter, prune: Boolean = false): Report {
        val report = runCatching { ContentBundleDirectoryWriter.write(indexFile, projectRoot, output, writer, pruneStaleBundles = prune) }
            .getOrElse { failure ->
                val reason = if (failure is IOException) "can't read a project file: ${failure.message}" else failure.message.orEmpty()
                throw CommandFailure(reason, failure)
            }
        return Report(report.indexFile, report.bundles, report.files, report.bytes, report.written)
    }

    companion object {
        /** The writer the command line's options describe. */
        fun writer(arguments: CliArguments): ContentBundleWriter {
            val grouping = when (val group = arguments.value("group") ?: "folder") {
                "folder" -> ContentBundleGrouping.byFolder(arguments.int("depth", 1, DEPTHS))
                "one" -> ContentBundleGrouping.oneGroup()
                else -> throw UsageException("--group takes folder or one, not $group")
            }
            val salt = arguments.value("salt")
            if (arguments.flag("opaque-ids") && salt.isNullOrBlank()) throw UsageException("--opaque-ids needs --salt <text>, such as the project's id")
            return ContentBundleWriter(
                grouping = grouping,
                targetBundleBytes = arguments.int("target-mb", DEFAULT_TARGET_MB, TARGET_MBS) * MEBIBYTE,
                pathIds = if (arguments.flag("opaque-ids")) ProjectPathIds(salt!!) else null,
            )
        }

        private const val DEFAULT_TARGET_MB = 16
        private const val MEBIBYTE = 1024L * 1024
        private val DEPTHS = 1..16
        private val TARGET_MBS = 1..1024
    }
}

/** What a bundle run wrote, as `--json` reports it. */
internal fun ProjectBundle.Report.toJson(): JsonObject = buildJsonObject {
    put("index", index.path)
    put("bundles", bundles)
    put("files", files)
    put("bytes", bytes)
    put("written", written)
}
