/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.floor
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
 * should not also carry an authored `texture_animation`: whichever was written last wins.
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
    val columns: Int = 1,
    val rows: Int = 1,
    val clips: Map<String, SceneTextureClip> = emptyMap(),
    val clip: String? = null,
) : SceneComponent {
    /** The clip that plays first: [clip], else the first of [clips], else none. */
    val initialClip: String? get() = clip ?: clips.keys.firstOrNull()

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        val fits = columns >= 1 && rows >= 1
        if (!fits) add(SceneValidationIssue(path, "texture_clips needs at least one column and row"))
        for ((name, run) in clips) {
            run.problems(name, if (fits) columns * rows else 0).forEach { add(SceneValidationIssue(path, it)) }
        }
        if (clip != null && clip !in clips) add(SceneValidationIssue(path, "texture_clips.clip \"$clip\" is not one of its clips"))
    }
}

/** What is wrong with the clip called [name] on a sheet of [cells] cells; [cells] is 0 when the sheet itself is invalid. */
private fun SceneTextureClip.problems(name: String, cells: Int): List<String> = buildList {
    val where = "texture_clips.clips.$name"
    if (name.isEmpty()) add("texture_clips clip names must not be empty")
    if (frameCount < 1) add("$where.frameCount must be at least 1")
    if (firstFrame < 0) {
        add("$where.firstFrame must not be negative")
    } else if (cells > 0 && frameCount >= 1 && firstFrame + frameCount > cells) {
        add("$where must stay within the sheet's $cells cells: firstFrame + frameCount is ${firstFrame + frameCount}")
    }
    if (framesPerSecond < 0f || !framesPerSecond.isFinite()) add("$where.framesPerSecond must be finite and >= 0")
}

/**
 * A hair of a frame the clock is credited with, so a time that should land exactly on the next cell
 * is not left a rounding error short of it. It is far below anything a frame time can mean.
 */
private const val FRAME_EPSILON = 1e-4f

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

    /** The clip that is playing, or null when the sheet has none. */
    var activeClipId: String? = sheet.initialClip
        private set

    /** Playback rate multiplier: 1 is real time, 0 holds the cell, and a game can slow or speed one entity. */
    var speed: Float = 1f
        set(value) {
            require(value >= 0f && value.isFinite()) { "Clip speed must be finite and non-negative." }
            field = value
        }

    /** Seconds into the active clip. A looping clip's stays inside one loop. */
    var elapsedSeconds: Float = 0f
        private set

    /** The cell the last system step showed, or -1 before it has shown any. */
    internal var shownFrame: Int = -1

    /** Whether the system has yet to show any cell of this entity. */
    internal val neverShown: Boolean get() = shownFrame < 0

    private val active: SceneTextureClip? get() = activeClipId?.let { sheet.clips[it] }

    /**
     * Whether a clip that does not loop has played to its end and is holding its last cell.
     * A looping clip, and one at 0 frames a second, never finishes.
     */
    val isFinished: Boolean
        get() = active?.let { !it.loop && it.framesPerSecond > 0f && framesPlayed(it) >= it.frameCount } ?: false

    /** The cell showing now, counted in reading order from 0 at the top left of the sheet. */
    val frame: Int
        get() = active?.let { it.firstFrame + offsetInRun(it) } ?: 0

    /**
     * Plays [clipId] from its first cell. The clip that is already playing is left alone unless
     * [restart], so asking for the clip a game wants every frame does not keep it on its first cell.
     *
     * @throws IllegalArgumentException when the sheet has no clip called [clipId].
     */
    fun play(clipId: String, restart: Boolean = false) {
        require(clipId in sheet.clips) { "No clip \"$clipId\"; the sheet has ${sheet.clips.keys}." }
        if (clipId == activeClipId && !restart) return
        activeClipId = clipId
        elapsedSeconds = 0f
    }

    /** Moves the active clip on by [delta] seconds, scaled by [speed]. A looping clip's clock wraps, so it keeps its precision however long it runs. */
    internal fun advance(delta: Float) {
        val run = active ?: return
        if (run.framesPerSecond <= 0f) return
        val duration = run.frameCount / run.framesPerSecond
        val next = elapsedSeconds + maxOf(delta, 0f) * speed
        elapsedSeconds = if (run.loop) next.mod(duration) else minOf(next, duration)
    }

    /** What the entity's `TextureAnimation` is while [frame] shows: that cell, held. */
    internal fun held(): TextureAnimation =
        TextureAnimation(sheet.columns, sheet.rows, frameCount = 1, framesPerSecond = 0f, firstFrame = frame)

    private fun framesPlayed(run: SceneTextureClip): Int = floor(elapsedSeconds * run.framesPerSecond + FRAME_EPSILON).toInt()

    private fun offsetInRun(run: SceneTextureClip): Int {
        if (run.framesPerSecond <= 0f) return 0
        val played = framesPlayed(run)
        return if (run.loop) played % run.frameCount else minOf(played, run.frameCount - 1)
    }
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
