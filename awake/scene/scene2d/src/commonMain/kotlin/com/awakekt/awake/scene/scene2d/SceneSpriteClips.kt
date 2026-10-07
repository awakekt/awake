/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.animation.FrameClip
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A named run of the sprite sheet's cells.
 *
 * @property firstFrame First cell, in reading order from the sheet's top left.
 * @property frameCount Cells in the run; at least one.
 * @property framesPerSecond Authored playback rate; zero holds the first cell.
 * @property loop Whether the run repeats or holds its final cell.
 */
@Serializable
data class SceneSpriteClip(
    @PropertyRange(min = 0.0) val firstFrame: Int = 0,
    @PropertyRange(min = 1.0) val frameCount: Int = 1,
    @PropertyRange(min = 0.0) val framesPerSecond: Float = 0f,
    val loop: Boolean = true,
)

/**
 * Named animation runs for the [SceneSprite] on the same node. Sheet dimensions belong to the
 * sprite, so the two components cannot disagree about the atlas layout. [SpriteClipSystem]
 * advances playback on simulation time and writes [Sprite.frame].
 *
 * @property clips Runs indexed by name. An empty library leaves the sprite's authored frame alone.
 * @property clip Initial run; omitted, the first listed run plays.
 */
@Serializable
@SerialName("sprite_clips")
data class SceneSpriteClips(
    val clips: Map<String, SceneSpriteClip> = emptyMap(),
    val clip: String? = null,
) : SceneComponent {
    /** Initial run, or none for an empty library. */
    val initialClip: String? get() = clip ?: clips.keys.firstOrNull()

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        clips.forEach { (name, run) ->
            val where = "sprite_clips.clips.$name"
            if (name.isEmpty()) add(SceneValidationIssue(path, "sprite_clips clip names must not be empty"))
            if (run.firstFrame < 0) add(SceneValidationIssue(path, "$where.firstFrame must not be negative"))
            if (run.frameCount < 1) add(SceneValidationIssue(path, "$where.frameCount must be at least 1"))
            if (run.firstFrame.toLong() + run.frameCount > Int.MAX_VALUE) add(SceneValidationIssue(path, "$where frame range must fit in an Int"))
            if (!run.framesPerSecond.isFinite() || run.framesPerSecond < 0f) add(SceneValidationIssue(path, "$where.framesPerSecond must be finite and non-negative"))
        }
        if (clip != null && clip !in clips) add(SceneValidationIssue(path, "sprite_clips.clip \"$clip\" is not one of its clips"))
    }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = buildList {
        addAll(validate(path))
        val sprite = peers.filterIsInstance<SceneSprite>().firstOrNull()
        if (sprite == null) {
            add(SceneValidationIssue(path, "sprite_clips requires a sprite on the same node"))
        } else if (sprite.cellCount > 0) {
            clips.forEach { (name, run) ->
                if (run.firstFrame >= 0 && run.frameCount > 0 && run.firstFrame.toLong() + run.frameCount > sprite.cellCount) {
                    add(SceneValidationIssue(path, "sprite_clips.clips.$name must stay within the sprite's ${sprite.cellCount} cells"))
                }
            }
        }
    }
}

/** Scene-to-capability mapping; the clock never depends on a scene schema. */
internal fun SceneSpriteClip.toFrameClip(): FrameClip = FrameClip(firstFrame, frameCount, framesPerSecond, loop)
