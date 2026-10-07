/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.animation.FrameClip
import com.awakekt.awake.core.animation.FrameClipPlayer
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** The rate a clip plays at when it does not say: a common rate for hand-drawn sprites. */
private const val DEFAULT_CLIP_FRAMES_PER_SECOND = 12f

/**
 * A named run of a frame sheet's cells: [frameCount] of them from [firstFrame], in reading order,
 * played at [framesPerSecond]. It starts over when it ends, or with [loop] false holds its last cell.
 *
 * @property firstFrame The run's first cell, counted in reading order from 0 at the top left.
 * @property frameCount Cells in the run; at least 1.
 * @property framesPerSecond Playback rate; 0 holds the first cell.
 * @property loop Whether the run starts over when it ends. A run that does not loop holds its last
 * cell, and [TextureClips.isFinished] says it has.
 */
@Serializable
data class SceneTextureClip(
    val firstFrame: Int = 0,
    val frameCount: Int = 1,
    val framesPerSecond: Float = DEFAULT_CLIP_FRAMES_PER_SECOND,
    val loop: Boolean = true,
)

/**
 * A frame sheet of [columns] x [rows] cells with named [clips] cut out of it, one of which plays on
 * the entity. Unlike `texture_animation`, which loops one run on the GPU's clock, this is stepped on
 * the CPU by [TextureClipSystem] from the simulation's time, so a game can switch clip by name
 * (`TextureClips.play`), see when a one-shot clip has finished, and have a paused game hold still.
 *
 * The system gives the entity a `TextureAnimation` that holds the cell now showing, so an entity
 * must not also carry an authored `texture_animation` (scene validation rejects nodes that declare both).
 *
 * @property columns Frame-sheet columns.
 * @property rows Frame-sheet rows.
 * @property clips The sheet's runs by name.
 * @property clip The clip that plays when the scene loads, one of [clips]. Left out, the first
 * listed plays; with no clips at all the entity shows the sheet's first cell.
 */
@Serializable
@SerialName("texture_clips")
data class SceneTextureClips(
    @PropertyRange(min = 1.0) val columns: Int = 1,
    @PropertyRange(min = 1.0) val rows: Int = 1,
    val clips: Map<String, SceneTextureClip> = emptyMap(),
    val clip: String? = null,
) : SceneComponent {
    /** The clip that plays first: [clip], else the first of [clips], else none. */
    val initialClip: String? get() = clip ?: clips.keys.firstOrNull()

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        val fits = columns >= 1 && rows >= 1 && columns.toLong() * rows <= Int.MAX_VALUE
        if (columns < 1 || rows < 1) add(SceneValidationIssue(path, "texture_clips needs at least one column and row"))
        if (columns.toLong() * rows > Int.MAX_VALUE) add(SceneValidationIssue(path, "texture_clips sheet cell count must fit in an Int"))
        for ((name, run) in clips) {
            run.problems(name, if (fits) columns * rows else 0).forEach { add(SceneValidationIssue(path, it)) }
        }
        if (clip != null && clip !in clips) add(SceneValidationIssue(path, "texture_clips.clip \"$clip\" is not one of its clips"))
    }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = buildList {
        addAll(validate(path))
        if (peers.any { it is SceneTextureAnimation }) {
            add(SceneValidationIssue(path, "node cannot have both texture_clips and texture_animation"))
        }
    }
}

/** What is wrong with the clip called [name] on a sheet of [cells] cells; [cells] is 0 when the sheet itself is invalid. */
private fun SceneTextureClip.problems(name: String, cells: Int): List<String> = buildList {
    val where = "texture_clips.clips.$name"
    if (name.isEmpty()) add("texture_clips clip names must not be empty")
    if (frameCount < 1) add("$where.frameCount must be at least 1")
    if (firstFrame < 0) {
        add("$where.firstFrame must not be negative")
    } else if (cells > 0 && frameCount >= 1 && firstFrame.toLong() + frameCount > cells) {
        add("$where must stay within the sheet's $cells cells: firstFrame + frameCount is ${firstFrame.toLong() + frameCount}")
    }
    if (framesPerSecond < 0f || !framesPerSecond.isFinite()) add("$where.framesPerSecond must be finite and >= 0")
}

/**
 * A [SceneTextureClips] on a live entity: which of its clips is playing and how far into it.
 *
 * [TextureClipSystem] advances it each frame and shows its cell. A game switches clip with [play]:
 * ```
 * world.get<TextureClips>(entity)?.play(if (moving) "walk" else "idle")
 * ```
 * Asking again for the clip that is already playing does nothing, so that line can run every frame.
 *
 * @property sheet The sheet and its clips. It is checked when this is made, so a clip that leaves
 * the sheet fails here rather than drawing the wrong cell.
 */
class TextureClips(val sheet: SceneTextureClips) {
    init {
        val problems = sheet.validate("texture_clips")
        require(problems.isEmpty()) { problems.joinToString("; ") { it.message } }
    }

    private val player = FrameClipPlayer(
        sheet.clips.mapValues { (_, run) -> FrameClip(run.firstFrame, run.frameCount, run.framesPerSecond, run.loop) },
        sheet.initialClip,
    )

    /** The clip that is playing, or null when the sheet has none. */
    val activeClipId: String? get() = player.activeClipId

    /** Playback rate multiplier: 1 is real time, 0 holds the cell, and a game can slow or speed one entity. */
    var speed: Float
        get() = player.speed
        set(value) {
            player.speed = value
        }

    /** Seconds into the active clip. A looping clip's stays inside one loop. */
    val elapsedSeconds: Float get() = player.elapsedSeconds

    /** The cell the last system step showed, or -1 before it has shown any. */
    internal var shownFrame: Int = -1

    /** Whether the system has yet to show any cell of this entity. */
    internal val neverShown: Boolean get() = shownFrame < 0

    /**
     * Whether a clip that does not loop has played to its end and is holding its last cell.
     * A looping clip, and one at 0 frames a second, never finishes.
     */
    val isFinished: Boolean get() = player.isFinished

    /** The cell showing now, counted in reading order from 0 at the top left of the sheet. */
    val frame: Int get() = player.frame

    /**
     * Plays [clipId] from its first cell. The clip that is already playing is left alone unless
     * [restart], so asking for the clip a game wants every frame does not keep it on its first cell.
     *
     * @throws IllegalArgumentException when the sheet has no clip called [clipId].
     */
    fun play(clipId: String, restart: Boolean = false) {
        player.play(clipId, restart)
    }

    /** Moves the active clip on by [delta] seconds, scaled by [speed]. A looping clip's clock wraps, so it keeps its precision however long it runs. */
    internal fun advance(delta: Float) {
        player.advance(delta)
    }

    /** What the entity's `TextureAnimation` is while [frame] shows: that cell, held. */
    internal fun held(): TextureAnimation =
        TextureAnimation(sheet.columns, sheet.rows, frameCount = 1, framesPerSecond = 0f, firstFrame = frame)

}

/** Loads [SceneTextureClips] as the entity's [TextureClips], showing the first cell of its first clip until the system steps it. */
object TextureClipsBinding : SceneComponentBinding<TextureClips, SceneTextureClips> {
    override val componentClass: KClass<TextureClips> = TextureClips::class
    override val schemaClass: KClass<SceneTextureClips> = SceneTextureClips::class
    override val serializer = SceneTextureClips.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneTextureClips,
        context: SceneResolutionContext,
    ) {
        val clips = TextureClips(component)
        world.add(entity, clips)
        world.add(entity, clips.held())
        clips.shownFrame = clips.frame
    }

    override fun export(world: World, entity: Entity, component: TextureClips): SceneTextureClips =
        component.sheet.copy(clip = component.activeClipId)
}
