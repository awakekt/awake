/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.asset.sprite.SpriteSheet
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode

/** Maps imported grid dimensions to a sprite; [texture] is the caller's resolved texture name or path. */
fun SpriteSheet.toSceneSprite(texture: String = image): SceneSprite = SceneSprite(texture, columns, rows)

/** Maps imported named runs to scene data. [clip] selects a run; omitted, the first exported run plays. */
fun SpriteSheet.toSceneSpriteClips(clip: String? = null): SceneSpriteClips = SceneSpriteClips(
    clips = clips.mapValues { (_, run) -> SceneSpriteClip(run.firstFrame, run.frameCount, run.framesPerSecond, run.loop) },
    clip = clip,
)

/**
 * Bakes imported metadata into matching sprites, including nested nodes, before instantiation.
 * [sheets] is keyed by each sprite's authored texture name. Imported dimensions replace the grid;
 * locally authored runs override same-named imported runs and the selected clip is preserved.
 * Other sprite styling, transforms and unrelated components are preserved. The resulting document
 * saves as ordinary `sprite` and `sprite_clips` data and needs no importer at playback time.
 */
fun SceneDocument.withSpriteSheets(sheets: Map<String, SpriteSheet>): SceneDocument =
    copy(nodes = nodes.map { it.withSpriteSheets(sheets) })

private fun SceneNode.withSpriteSheets(sheets: Map<String, SpriteSheet>): SceneNode {
    val sprites = components.filterIsInstance<SceneSprite>()
    require(sprites.size <= 1 || sprites.none { it.texture in sheets }) { "A sprite-sheet import needs exactly one sprite per matching node." }
    val sprite = sprites.singleOrNull()
    val sheet = sprite?.let { sheets[it.texture] }
    val baked = if (sheet == null) {
        components
    } else {
        val authoredRuns = components.filterIsInstance<SceneSpriteClips>()
        require(authoredRuns.size <= 1) { "A sprite-sheet import cannot merge multiple sprite_clips on one node." }
        val authored = authoredRuns.singleOrNull()
        val imported = sheet.toSceneSpriteClips()
        val clips = imported.copy(
            clips = imported.clips + authored?.clips.orEmpty(),
            clip = authored?.clip,
        )
        components.map { component ->
            when (component) {
                is SceneSprite -> component.copy(columns = sheet.columns, rows = sheet.rows)
                is SceneSpriteClips -> clips
                else -> component
            }
        }.let { if (authored == null) it + clips else it }
    }
    return copy(components = baked, children = children.map { it.withSpriteSheets(sheets) })
}
