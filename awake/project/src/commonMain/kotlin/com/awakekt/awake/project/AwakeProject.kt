/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Settings embedded in [AwakeProjectManifest]. */
@Serializable
data class AwakeProjectSettings(
    val targetFrameRate: Int = 60,
    val physicsTickRate: Int = 60,
    val defaultRenderer: String = "Vulkan",
)

/** The portable `awake.project.json` metadata document. */
@Serializable
data class AwakeProjectManifest(
    val name: String,
    val id: String,
    val engineVersion: String = "0.1.0",
    val minEngineVersion: String = "0.1.0-alpha.1",
    val defaultScene: String = "scenes/main.scene.json",
    val createdWith: String = "0.1.0-dev.10",
    val assetDirectories: List<String> = listOf("assets"),
    /** Declarative plugin identifiers only; installation and loading remain product policy. */
    val plugins: List<String> = emptyList(),
    val settings: AwakeProjectSettings = AwakeProjectSettings(),
) {
    init {
        require(name.isNotBlank()) { "Project name must not be blank." }
        require(id.isNotBlank()) { "Project ID must not be blank." }
        require(defaultScene.isNotBlank()) { "Default scene path must not be blank." }
        require(assetDirectories.all { it.isNotBlank() && !it.startsWith("/") && !it.contains("..") }) {
            "Asset directories must be non-empty, root-relative paths."
        }
    }
}

/** Pure JSON and SemVer validation for project metadata. */
object AwakeProjectValidator {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(manifest: AwakeProjectManifest): String =
        json.encodeToString(AwakeProjectManifest.serializer(), manifest)

    fun decode(jsonString: String): AwakeProjectManifest =
        json.decodeFromString(AwakeProjectManifest.serializer(), jsonString)

    fun isCompatible(manifest: AwakeProjectManifest, currentEngineVersion: String): Boolean =
        parseSemVer(currentEngineVersion) >= parseSemVer(manifest.minEngineVersion)

    internal fun parseSemVer(version: String): SemVer {
        val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
            .matchEntire(version)
            ?: throw IllegalArgumentException("Invalid SemVer: $version")
        return SemVer(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
            preRelease = match.groupValues[4],
        )
    }

    internal data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            val numeric = compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
            if (numeric != 0) return numeric
            return when {
                preRelease.isEmpty() && other.preRelease.isNotEmpty() -> 1
                preRelease.isNotEmpty() && other.preRelease.isEmpty() -> -1
                else -> preRelease.compareTo(other.preRelease)
            }
        }
    }
}
