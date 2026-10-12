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

/** A `com.awakekt.awake` dependency or constraint that one variant of a Gradle module file declares. */
internal data class AwakeEdge(
    val module: Path,
    val variant: String,
    val group: String,
    val name: String,
    val requires: String?,
    val strictly: String?,
    val constraint: Boolean,
) {
    val coordinate: String get() = "$group:$name"

    /** The version a consumer's resolution starts from: the strict one, when the edge has it. */
    val version: String? get() = strictly ?: requires
}

/**
 * The Awake edges of the Maven-local Gradle module file [root], and of every platform module one of its
 * variants is available at. A Kotlin Multiplatform module keeps each platform's dependencies in that
 * platform's own file, so the root alone names almost none of them.
 */
internal class LocalModuleMetadata(val root: Path) {
    val edges = mutableListOf<AwakeEdge>()

    /** Module files the walk reached that Maven Local does not hold, the root among them. */
    val missing = mutableListOf<Path>()

    init {
        val pending = ArrayDeque(listOf(root.normalize()))
        val visited = mutableSetOf<Path>()
        while (pending.isNotEmpty()) {
            val module = pending.removeFirst()
            if (!visited.add(module)) continue
            if (!Files.isRegularFile(module)) {
                missing.add(module)
                continue
            }
            val variants = Json.parseToJsonElement(Files.readString(module)).jsonObject["variants"]?.jsonArray.orEmpty()
            variants.forEach { entry ->
                val variant = entry.jsonObject
                val name = variant["name"]?.jsonPrimitive?.content.orEmpty()
                variant["available-at"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let { url ->
                    pending.add(module.parent.resolve(url).normalize())
                }
                edges.addAll(variant.awakeEdges(module, name, "dependencies", constraint = false))
                edges.addAll(variant.awakeEdges(module, name, "dependencyConstraints", constraint = true))
            }
        }
    }
}

private fun JsonObject.awakeEdges(module: Path, variant: String, key: String, constraint: Boolean): List<AwakeEdge> =
    this[key]?.jsonArray.orEmpty().map { it.jsonObject }.filter { isAwakeGroup(it.string("group")) }.map { edge ->
        val version = edge["version"]?.jsonObject
        AwakeEdge(
            module = module,
            variant = variant,
            group = edge.string("group").orEmpty(),
            name = edge.string("module").orEmpty(),
            requires = version?.string("requires"),
            strictly = version?.string("strictly"),
            constraint = constraint,
        )
    }

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

private fun isAwakeGroup(group: String?): Boolean =
    group == "com.awakekt.awake" || group?.startsWith("com.awakekt.awake.") == true

/** Whether [artifact], a root or a platform artifact id, belongs to the publication [family]. */
internal fun belongsToFamily(artifact: String, family: String): Boolean = when (family) {
    "vulkan" -> artifact == "vulkan" || artifact.startsWith("vulkan-")
    "webgpu" -> artifact == "webgpu" || artifact.startsWith("webgpu-")
    "core" -> !belongsToFamily(artifact, "vulkan")
    else -> true
}

/**
 * Why the module metadata of a [family] module does not hold Core at exactly the release [pin].
 *
 * Each Awake edge outside the family must name the pin itself. A snapshot of it, or the next patch's
 * snapshot that `main` publishes, sorts beside or above the release and moves a consumer's Core with
 * it. A WebGPU release pin must also hold every Core coordinate `strictly` at the pin, its own
 * dependencies included, so a newer Core is a resolution error. Every coordinate must be one this
 * build publishes ([publishedModules]): a constraint on any other, such as a project name where an
 * artifact id was overridden, constrains nothing.
 */
internal fun pinnedCoreViolations(
    metadata: LocalModuleMetadata,
    pin: String,
    family: String,
    publishedModules: Set<String>,
    describe: (Path) -> String = Path::toString,
): List<String> {
    require(!pin.endsWith("-SNAPSHOT")) { "A pinned Core version is a release, not $pin" }
    val failures = metadata.missing.map { "${describe(it)}: Gradle module metadata is not in Maven Local" }.toMutableList()
    val core = metadata.edges.filterNot { belongsToFamily(it.name, family) }
    for (edge in core) {
        val at = "${describe(edge.module)} (${edge.variant})"
        when {
            edge.constraint && family == "webgpu" && edge.strictly != pin ->
                failures += "$at: the constraint on ${edge.coordinate} is not strictly $pin (requires ${edge.requires}, strictly ${edge.strictly})"
            edge.version != pin ->
                failures += "$at: ${edge.coordinate} is at ${edge.version}, not Core $pin"
        }
        if (edge.coordinate !in publishedModules) failures += "$at: ${edge.coordinate} is not a module this build publishes"
    }
    if (family == "webgpu") {
        core.groupBy { it.module to it.variant }.forEach { (_, edges) ->
            val constrained = edges.filter { it.constraint }.map { it.coordinate }.toSet()
            edges.filter { !it.constraint && it.coordinate !in constrained }.forEach { edge ->
                failures += "${describe(edge.module)} (${edge.variant}): ${edge.coordinate} has no strictly constraint"
            }
        }
    }
    return failures
}

/** The Core modules, as `group:name`, that the WebGPU release pin whose metadata is [root] depends on at [release]. */
internal fun pinnedCoreModules(root: Path, release: String): List<String> {
    val metadata = LocalModuleMetadata(root)
    require(metadata.missing.isEmpty()) { "Gradle module metadata is not in Maven Local: ${metadata.missing.joinToString()}" }
    val modules = metadata.edges
        .filter { !it.constraint && !belongsToFamily(it.name, "webgpu") && it.version == release }
        .map { it.coordinate }
        .distinct()
    require(modules.isNotEmpty()) { "$root names no Core module at $release" }
    return modules
}
