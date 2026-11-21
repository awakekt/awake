/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile

/**
 * Fails a shader that carries a third-party algorithm's or project's name with no provenance for it
 * (#1791).
 *
 * `docs/reference/third-party-names.json` lists the names, each with a pattern. A main source file under
 * `awake/` that builds a shader ([SHADER_MARKER]) and matches a name needs one of these:
 * - an entry in `docs/reference/source-provenance.json` for its path whose `reason` matches the same
 *   pattern, so the entry says which algorithm it covers;
 * - a line under `pending` in the names file, naming the file, the name and the tracker that owns the
 *   review.
 *
 * A pending line whose file no longer carries the name fails too, so the list only shrinks. Before this
 * gate, `awakeVerify` checked only that the provenance file had a version and entries, and a file that
 * adapted third-party code with no entry failed nothing.
 */
internal object ProvenanceNameGate {
    /** What a shader source looks like: WGSL entry points, or the ASL builders that emit them. */
    private val SHADER_MARKER = Regex("@fragment|@vertex|AslShaderBuilder|aslShaderSet|\\bshader\\(")

    private val SOURCE_EXTENSIONS = setOf("kt", "wgsl", "glsl")

    private class Name(val name: String, val pattern: Regex)

    private class Pending(val path: String, val names: Set<String>, val tracker: String)

    fun failures(root: Path): List<String> {
        val namesFile = root.resolve("docs/reference/third-party-names.json")
        if (!namesFile.isRegularFile()) return listOf("docs/reference/third-party-names.json is missing")
        val registry = Json.parseToJsonElement(Files.readString(namesFile)).jsonObject
        val names = registry.getValue("names").jsonArray.map {
            val name = it.jsonObject
            Name(name.string("name"), Regex(name.string("pattern")))
        }
        val pending = registry["pending"]?.jsonArray.orEmpty().map {
            val line = it.jsonObject
            Pending(line.string("path"), line.getValue("names").jsonArray.map { n -> n.jsonPrimitive.content }.toSet(), line.string("tracker"))
        }
        val reasons = provenanceReasons(root)
        val failures = mutableListOf<String>()
        val carried = mutableMapOf<String, Set<String>>()
        shaderSources(root).forEach { (relative, text) ->
            val found = names.filter { it.pattern.containsMatchIn(text) }
            if (found.isNotEmpty()) carried[relative] = found.mapTo(HashSet()) { it.name }
            found.forEach { name ->
                val recorded = reasons[relative].orEmpty().any { name.pattern.containsMatchIn(it) }
                val waiting = pending.any { it.path == relative && name.name in it.names }
                if (!recorded && !waiting) {
                    failures += "$relative carries ${name.name} with no source-provenance.json entry whose reason names it, " +
                        "and no pending review in third-party-names.json"
                }
            }
        }
        pending.forEach { line ->
            if (line.tracker.isBlank()) failures += "third-party-names.json: pending ${line.path} names no tracker"
            val stale = line.names - carried[line.path].orEmpty()
            if (stale.isNotEmpty()) {
                failures += "third-party-names.json: pending ${line.path} no longer carries ${stale.joinToString()}; remove it"
            }
        }
        return failures
    }

    /** Each provenance entry's reason, by the path it covers. */
    private fun provenanceReasons(root: Path): Map<String, List<String>> {
        val provenance = root.resolve("docs/reference/source-provenance.json")
        if (!provenance.isRegularFile()) return emptyMap()
        return Json.parseToJsonElement(Files.readString(provenance)).jsonObject.getValue("entries").jsonArray
            .map { it.jsonObject }
            .groupBy({ it.string("path") }, { it.string("reason") })
    }

    /** Every main source file under `awake/` that builds a shader, by its path from [root]. */
    private fun shaderSources(root: Path): Sequence<Pair<String, String>> {
        val awake = root.resolve("awake")
        if (!Files.isDirectory(awake)) return emptySequence()
        val files = Files.walk(awake).use { stream ->
            stream.filter { path ->
                path.isRegularFile() && path.extension in SOURCE_EXTENSIONS && isMainSource(root.relativize(path).invariantSeparatorsPathString)
            }.toList()
        }
        return files.asSequence()
            .map { root.relativize(it).invariantSeparatorsPathString to Files.readString(it) }
            .filter { (_, text) -> SHADER_MARKER.containsMatchIn(text) }
    }

    private fun isMainSource(relative: String): Boolean =
        "/build/" !in relative && "/src/" in relative && Regex("/src/[A-Za-z]*Test/|/src/test/").find(relative) == null

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
