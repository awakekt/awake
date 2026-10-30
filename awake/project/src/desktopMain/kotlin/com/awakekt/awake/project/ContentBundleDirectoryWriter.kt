/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Summary of a [ContentBundleDirectoryWriter] run on disk.
 *
 * @property indexFile The bundled `project.index.json` written under the output directory.
 * @property bundles Total bundles listed by the bundled index.
 * @property files Total project files packed across those bundles.
 * @property bytes Total encoded bundle bytes across all [bundles].
 * @property written How many bundle files were newly written (unchanged bundles already on disk are kept).
 */
class ContentBundleDirectoryReport(
    val indexFile: File,
    val bundles: Int,
    val files: Int,
    val bytes: Long,
    val written: Int,
)

/**
 * Packs a loose `project.index.json` file's entries from a project directory into content bundles on
 * disk and writes the bundled `project.index.json` beside them.
 *
 * Shared by the `awake bundle` CLI command and the `generateContentBundles` Gradle task so both run
 * the same [ContentBundleWriter] and emit byte-identical output.
 */
object ContentBundleDirectoryWriter {
    private val indexJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = false
    }

    /**
     * Packs [indexFile]'s files read from [projectRoot] into [outputDirectory] with [writer].
     *
     * Bundles whose `<sha256>.awkb` file already exists in [outputDirectory] with the expected byte
     * length are left untouched rather than rewritten. When [pruneStaleBundles] is `true`, `.awkb`
     * files under [outputDirectory] that are not listed by the new index are removed.
     *
     * @param indexFile Loose `project.index.json` listing the files to bundle.
     * @param projectRoot Project directory that [ProjectIndexEntry.path] entries resolve against.
     * @param outputDirectory Destination directory for bundle archives and the bundled `project.index.json`.
     * @param writer Configured [ContentBundleWriter] that plans and encodes the bundles.
     * @param pruneStaleBundles Whether to delete unreferenced `.awkb` files under [outputDirectory].
     * @return Summary of the written index and bundles.
     */
    fun write(
        indexFile: File,
        projectRoot: File,
        outputDirectory: File,
        writer: ContentBundleWriter = ContentBundleWriter(),
        pruneStaleBundles: Boolean = false,
    ): ContentBundleDirectoryReport {
        val loose = runCatching { indexJson.decodeFromString(ProjectIndex.serializer(), indexFile.readText()) }
            .getOrElse { throw IllegalArgumentException("${indexFile.path} isn't a project index: ${it.message}", it) }
        var bundles = 0
        var written = 0
        var bytes = 0L
        val bundled = runBlocking {
            writer.write(loose, { entry -> projectRoot.resolve(entry.path).readBytes() }) { bundle ->
                bundles++
                bytes += bundle.bytes.size
                val file = outputDirectory.resolve(bundle.url)
                // Named by SHA-256: one already there holds these bytes, so it isn't written again.
                if (!file.isFile || file.length() != bundle.bytes.size.toLong()) {
                    file.parentFile.mkdirs()
                    file.writeBytes(bundle.bytes)
                    written++
                }
            }
        }
        if (pruneStaleBundles) {
            pruneUnlistedBundles(outputDirectory, bundled)
        }
        val indexOut = outputDirectory.resolve(IndexedAssetSource.DEFAULT_INDEX_PATH)
        indexOut.parentFile.mkdirs()
        indexOut.writeText(indexJson.encodeToString(ProjectIndex.serializer(), bundled) + "\n")
        return ContentBundleDirectoryReport(indexOut, bundles, bundled.files.size, bytes, written)
    }

    /**
     * Entry point invoked reflectively from Gradle's isolated worker in `project-content`, using only
     * bootstrap JDK parameter and return types across the class-loader boundary.
     *
     * @param indexFile Loose `project.index.json` file.
     * @param projectRoot Project directory that indexed paths resolve against.
     * @param outputDirectory Output directory for the bundled index and `.awkb` files.
     * @param grouping Grouping mode: `"folder"` or `"one"`.
     * @param folderDepth Folder depth when [grouping] is `"folder"`.
     * @param targetBundleBytes Maximum target bytes per bundle.
     * @param opaqueIds Whether to replace readable paths with salted opaque ids.
     * @param pathSalt Salt for [ProjectPathIds] when [opaqueIds] is `true`.
     * @param bundleUrlPrefix Relative folder prefix for bundle URLs.
     * @return `[bundles, files, bytes, written]` as a 4-element [LongArray].
     */
    @JvmStatic
    @Suppress("LongParameterList")
    fun runFromWorker(
        indexFile: File,
        projectRoot: File,
        outputDirectory: File,
        grouping: String,
        folderDepth: Int,
        targetBundleBytes: Long,
        opaqueIds: Boolean,
        pathSalt: String?,
        bundleUrlPrefix: String,
    ): LongArray {
        val resolvedGrouping = when (grouping) {
            "folder" -> ContentBundleGrouping.byFolder(folderDepth)
            "one" -> ContentBundleGrouping.oneGroup()
            else -> throw IllegalArgumentException("bundleGrouping must be 'folder' or 'one', not '$grouping'")
        }
        val pathIds = if (opaqueIds) {
            require(!pathSalt.isNullOrBlank()) {
                "bundlePathSalt must be set when bundleOpaqueIds is true"
            }
            ProjectPathIds(pathSalt)
        } else {
            null
        }
        val writer = ContentBundleWriter(
            grouping = resolvedGrouping,
            targetBundleBytes = targetBundleBytes,
            pathIds = pathIds,
            bundleUrlPrefix = bundleUrlPrefix,
        )
        val report = write(
            indexFile = indexFile,
            projectRoot = projectRoot,
            outputDirectory = outputDirectory,
            writer = writer,
            pruneStaleBundles = true,
        )
        return longArrayOf(report.bundles.toLong(), report.files.toLong(), report.bytes, report.written.toLong())
    }

    private fun pruneUnlistedBundles(outputDirectory: File, index: ProjectIndex) {
        if (!outputDirectory.isDirectory) return
        val keep = index.bundles.mapTo(HashSet()) { outputDirectory.resolve(it.url).canonicalFile }
        val suffix = ".${ContentBundleFormat.FILE_EXTENSION}"
        outputDirectory.walkTopDown()
            .filter { it.isFile && it.name.endsWith(suffix) && it.canonicalFile !in keep }
            .forEach(File::delete)
    }
}
