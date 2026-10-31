/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One generated level of detail.
 *
 * @property ratio The fraction of the full mesh's triangles this level keeps, in `0..1`.
 * @property screenCoverage The smallest screen coverage this level is drawn at: the model's projected
 *   bounding-sphere diameter over the viewport height. The last level's value is where a loader that
 *   culls small models stops drawing it; Awake draws the last level at any size, and `0` asks every
 *   loader to.
 */
@Serializable
data class LodLevelSetting(val ratio: Float, val screenCoverage: Float) {
    init {
        require(ratio > 0f && ratio < 1f) { "ratio is $ratio; a generated level keeps between 0 and 1 of the triangles" }
        require(screenCoverage >= 0f) { "screenCoverage is $screenCoverage; it can't be negative" }
    }
}

/**
 * How the cooker generates levels of detail for one asset.
 *
 * @property enabled Whether to generate levels at all; `false` opts the asset out.
 * @property baseScreenCoverage The smallest screen coverage the full mesh is drawn at, before the first
 *   generated level takes over.
 * @property levels The generated levels, from finest to coarsest; ratios and coverages both decrease.
 * @property lockBorders Keep open borders in place, so the primitives of one mesh, or parts modelled
 *   separately, don't open gaps where they meet.
 * @property normalWeight How much a normal change costs against geometric error.
 * @property uvWeight How much a texture-coordinate change costs.
 * @property colorWeight How much a vertex-colour change costs.
 * @property skinWeight How much a change in joint weights costs, for a skinned mesh.
 * @property meshes Per-mesh changes, keyed by the glTF mesh's name: different ratios for a mesh that
 *   should keep more detail, or `enabled = false` to give a mesh no levels.
 * @property jointImportance For a skinned mesh, how much more it costs to collapse vertices weighted
 *   to each named joint (keyed by the joint node's name): `0` is ordinary, `4` makes them five times as
 *   costly, so the parts they move keep their detail longest.
 */
@Serializable
data class MeshLodSettings(
    val enabled: Boolean = true,
    val baseScreenCoverage: Float = DEFAULT_BASE_COVERAGE,
    val levels: List<LodLevelSetting> = DEFAULT_LEVELS,
    val lockBorders: Boolean = true,
    val normalWeight: Float = DEFAULT_NORMAL_WEIGHT,
    val uvWeight: Float = 1f,
    val colorWeight: Float = DEFAULT_COLOR_WEIGHT,
    val skinWeight: Float = 1f,
    val meshes: Map<String, MeshLodOverride> = emptyMap(),
    val jointImportance: Map<String, Float> = emptyMap(),
) {
    init {
        require(baseScreenCoverage >= 0f) { "baseScreenCoverage is $baseScreenCoverage; it can't be negative" }
        levels.zipWithNext().forEach { (finer, coarser) ->
            require(coarser.ratio < finer.ratio) { "levels must keep fewer triangles each: ${coarser.ratio} follows ${finer.ratio}" }
            require(coarser.screenCoverage <= finer.screenCoverage) {
                "levels must draw at smaller coverage each: ${coarser.screenCoverage} follows ${finer.screenCoverage}"
            }
        }
        require(levels.firstOrNull()?.let { it.screenCoverage <= baseScreenCoverage } ?: true) {
            "the first level's screenCoverage is above baseScreenCoverage $baseScreenCoverage"
        }
        jointImportance.forEach { (joint, importance) -> require(importance >= 0f) { "jointImportance[$joint] is $importance; it can't be negative" } }
    }

    /** These settings with [override]'s non-null fields in their place. */
    fun overriddenBy(override: MeshLodOverride): MeshLodSettings = copy(
        enabled = override.enabled ?: enabled,
        baseScreenCoverage = override.baseScreenCoverage ?: baseScreenCoverage,
        levels = override.levels ?: levels,
        lockBorders = override.lockBorders ?: lockBorders,
        meshes = override.meshes ?: meshes,
        jointImportance = override.jointImportance ?: jointImportance,
    )

    /** The settings for the mesh named [name]: these, with its entry in [meshes] applied. */
    fun forMesh(name: String?): MeshLodSettings = name?.let(meshes::get)?.let(::overriddenBy) ?: this

    companion object {
        private const val DEFAULT_BASE_COVERAGE = 0.25f
        private const val DEFAULT_NORMAL_WEIGHT = 0.5f
        private const val DEFAULT_COLOR_WEIGHT = 0.5f

        /** 50%, 25% and 10% of the triangles, the last drawn at any distance. */
        val DEFAULT_LEVELS: List<LodLevelSetting> = listOf(
            LodLevelSetting(ratio = 0.5f, screenCoverage = 0.12f),
            LodLevelSetting(ratio = 0.25f, screenCoverage = 0.05f),
            LodLevelSetting(ratio = 0.1f, screenCoverage = 0f),
        )
    }
}

/**
 * Changes to [MeshLodSettings] for one asset, or for one mesh within it; a field left null keeps the
 * setting it overrides.
 *
 * @property enabled `false` opts the asset, or the mesh, out of generated levels.
 * @property baseScreenCoverage Replaces [MeshLodSettings.baseScreenCoverage].
 * @property levels Replaces [MeshLodSettings.levels], for example `0.7`, `0.5`, `0.3` for a mesh that
 *   should keep more detail than the rest.
 * @property lockBorders Replaces [MeshLodSettings.lockBorders].
 * @property meshes Replaces [MeshLodSettings.meshes]; ignored on a per-mesh entry.
 * @property jointImportance Replaces [MeshLodSettings.jointImportance].
 */
@Serializable
data class MeshLodOverride(
    val enabled: Boolean? = null,
    val baseScreenCoverage: Float? = null,
    val levels: List<LodLevelSetting>? = null,
    val lockBorders: Boolean? = null,
    val meshes: Map<String, MeshLodOverride>? = null,
    val jointImportance: Map<String, Float>? = null,
)

/**
 * A project's LOD settings, kept in [FILE_NAME] at the project root: defaults for every model, and
 * per-asset overrides keyed by project-relative path.
 */
@Serializable
data class ProjectLodSettings(
    val formatVersion: Int = 1,
    val defaults: MeshLodSettings = MeshLodSettings(),
    val assets: Map<String, MeshLodOverride> = emptyMap(),
) {
    /** The settings for the asset at project-relative [path]: the defaults with its override applied. */
    fun forAsset(path: String): MeshLodSettings {
        val override = assets[path.replace('\\', '/').removePrefix("./")] ?: return defaults
        return defaults.overriddenBy(override)
    }

    /** These settings as the JSON [FILE_NAME] holds. */
    fun encode(): String = SettingsJson.encodeToString(serializer(), this)

    companion object {
        /** The settings file's name at the project root. */
        const val FILE_NAME: String = "awake.lod.json"

        /** Reads [json], the contents of [FILE_NAME]. */
        fun decode(json: String): ProjectLodSettings = SettingsJson.decodeFromString(serializer(), json).also {
            require(it.formatVersion == 1) { "formatVersion is ${it.formatVersion}; only 1 is read" }
        }

        private val SettingsJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
