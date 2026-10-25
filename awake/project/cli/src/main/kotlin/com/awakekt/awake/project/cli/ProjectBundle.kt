/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.ContentBundleGrouping
import com.awakekt.awake.project.ContentBundleWriter
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectPathIds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

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
     * answers what it wrote.
     */
    fun run(indexFile: File, writer: ContentBundleWriter): Report {
        val loose = try {
            INDEX_JSON.decodeFromString(ProjectIndex.serializer(), indexFile.readText())
        } catch (failure: SerializationException) {
            throw CommandFailure("${indexFile.path} isn't a project index: ${failure.message}", failure)
        }
        var bundles = 0
        var written = 0
        var bytes = 0L
        val bundled = try {
            runBlocking {
                writer.write(loose, { entry -> projectRoot.resolve(entry.path).readBytes() }) { bundle ->
                    bundles++
                    bytes += bundle.bytes.size
                    val file = output.resolve(bundle.url)
                    // Named by SHA-256: one already there holds these bytes, so it isn't written again.
                    if (!file.isFile || file.length() != bundle.bytes.size.toLong()) {
                        file.parentFile.mkdirs()
                        file.writeBytes(bundle.bytes)
                        written++
                    }
                }
            }
        } catch (failure: IllegalArgumentException) {
            throw CommandFailure(failure.message.orEmpty(), failure)
        } catch (failure: IllegalStateException) {
            throw CommandFailure(failure.message.orEmpty(), failure)
        } catch (failure: java.io.IOException) {
            throw CommandFailure("can't read a project file: ${failure.message}", failure)
        }
        val indexOut = output.resolve(IndexedAssetSource.DEFAULT_INDEX_PATH)
        indexOut.parentFile.mkdirs()
        indexOut.writeText(INDEX_JSON.encodeToString(ProjectIndex.serializer(), bundled) + "\n")
        return Report(indexOut, bundles, bundled.files.size, bytes, written)
    }

    companion object {
        private val INDEX_JSON = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = false
        }

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
