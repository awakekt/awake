/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.asset.shaderpack.MAX_CLIPMAP_RINGS as SHADER_CLIPMAP_RINGS

private val DEFAULT_CLIPMAP = TerrainClipmapConfig()

/** `SceneTerrain.clipmapRings` when a document leaves it out: what every terrain drew with before #1809. */
val DEFAULT_CLIPMAP_RINGS: Int = DEFAULT_CLIPMAP.ringCount

/** `SceneTerrain.clipmapRingResolution` when a document leaves it out. */
val DEFAULT_CLIPMAP_RING_RESOLUTION: Int = DEFAULT_CLIPMAP.ringResolution

/** `SceneTerrain.clipmapBaseSpacing` when a document leaves it out, in metres. */
val DEFAULT_CLIPMAP_BASE_SPACING: Float = DEFAULT_CLIPMAP.baseSpacing

/** The fewest clipmap levels: the core and one ring, as `TerrainClipmapConfig` requires. */
const val MIN_CLIPMAP_RINGS: Int = 2

/** The most clipmap levels: the ring slots the terrain shader's uniform block reserves. */
const val MAX_CLIPMAP_RINGS: Int = SHADER_CLIPMAP_RINGS

/** The coarsest grid `TerrainClipmapConfig` accepts, in cells per level side. */
const val MIN_CLIPMAP_RING_RESOLUTION: Int = 16

/**
 * The finest grid a scene may ask for, in cells per level side. At 256, eight levels hold 528,392
 * vertices in one draw, against 25,350 at the defaults.
 */
const val MAX_CLIPMAP_RING_RESOLUTION: Int = 256

/** What is wrong with a terrain's clipmap fields, as [SceneTerrain.validate] reports it; empty when nothing is. */
internal fun clipmapIssues(path: String, rings: Int, resolution: Int, baseSpacing: Float): List<SceneValidationIssue> = buildList {
    if (rings !in MIN_CLIPMAP_RINGS..MAX_CLIPMAP_RINGS) {
        add(SceneValidationIssue(path, "terrain.clipmapRings must be $MIN_CLIPMAP_RINGS to $MAX_CLIPMAP_RINGS; was $rings"))
    }
    if (resolution !in MIN_CLIPMAP_RING_RESOLUTION..MAX_CLIPMAP_RING_RESOLUTION || resolution % 2 != 0) {
        add(
            SceneValidationIssue(
                path,
                "terrain.clipmapRingResolution must be an even count from $MIN_CLIPMAP_RING_RESOLUTION to " +
                    "$MAX_CLIPMAP_RING_RESOLUTION; was $resolution",
            ),
        )
    }
    if (!baseSpacing.isFinite() || baseSpacing <= 0f) {
        add(SceneValidationIssue(path, "terrain.clipmapBaseSpacing must be finite and above 0; was $baseSpacing"))
    }
}
