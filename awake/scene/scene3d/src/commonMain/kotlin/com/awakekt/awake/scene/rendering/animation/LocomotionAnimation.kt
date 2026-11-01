/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.AnimationPlayback
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.reflect.KClass

/**
 * Which of a skinned model's clips plays for how the entity moves: [idle] standing, [walk] above
 * [walkAbove] units per second across the ground, [run] above [runAbove], and [jump] from when it
 * rises or falls faster than [airborneAbove] until it lands. A jump can be split into phases:
 * [takeOff] plays once as it leaves the ground, [jump] while it rises, [fall] while it comes down,
 * and [land] once as it touches down unless it is already walking away. The clips for the air play
 * once and hold their last frame, since a rise or a fall is a pose to hold. Clip names are the model's
 * own animations; a clip left null, or one the model lacks, keeps whatever plays. Changes blend over
 * [crossFade] seconds.
 *
 * The speeds are measured from the entity's world position, so it animates however it is moved:
 * by a character controller, straight through the world, or by a script, and a model on a child
 * node of the moving entity animates with it. A mover stepped at a fixed rate stays put between
 * its steps, so a frame without motion counts as a stop only after a tenth of a second. The speed
 * across the ground is smoothed over about as long, so a frame or two slower, up a step or against
 * a wall, does not switch the clip and restart it; a stop is still a stop at once. A single frame's
 * move too fast to be a stride — a controller reconcile, a respawn, a networked entity's leash home —
 * is a teleport, not locomotion, and is left out of that speed, so a jump in position neither switches
 * the clip to a run nor races speed-matched playback.
 *
 * When the entity or a node above it has a [GroundContact], as a `character_controller` keeps,
 * [jump] plays exactly while it is off the ground. Otherwise it lands when its fall stops, or when
 * it holds its height for a quarter second, and since climbing a slope also moves it upward,
 * [airborneAbove] must sit above the vertical speed its steepest walkable slope gives at a run.
 *
 * [walkReferenceSpeed] and [runReferenceSpeed] opt a clip into speed-matched playback: the ground
 * speed, in units per second, its walk or run cycle was authored for. When set, [LocomotionAnimationSystem]
 * scales that clip's playback by the mover's smoothed ground speed over this reference, so the stride
 * matches the distance covered and the feet plant instead of sliding. Left null (the default), the clip
 * plays at its authored rate, as before.
 */
@Serializable
@SerialName("locomotion_animation")
data class SceneLocomotionAnimation(
    val idle: String? = null,
    val walk: String? = null,
    val run: String? = null,
    val jump: String? = null,
    val takeOff: String? = null,
    val fall: String? = null,
    val land: String? = null,
    val walkAbove: Float = DEFAULT_WALK_ABOVE,
    val runAbove: Float = DEFAULT_RUN_ABOVE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val airborneAbove: Float = DEFAULT_AIRBORNE_ABOVE,
    @PropertyRange(min = 0.0) val crossFade: Float = DEFAULT_CROSS_FADE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val walkReferenceSpeed: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val runReferenceSpeed: Float? = null,
) : SceneComponent {
    /** [SceneLocomotionAnimation] as it was before the reference speeds: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        idle: String? = null,
        walk: String? = null,
        run: String? = null,
        jump: String? = null,
        takeOff: String? = null,
        fall: String? = null,
        land: String? = null,
        walkAbove: Float = DEFAULT_WALK_ABOVE,
        runAbove: Float = DEFAULT_RUN_ABOVE,
        airborneAbove: Float = DEFAULT_AIRBORNE_ABOVE,
        crossFade: Float = DEFAULT_CROSS_FADE,
    ) : this(idle, walk, run, jump, takeOff, fall, land, walkAbove, runAbove, airborneAbove, crossFade)

    /** [copy] as it was before the reference speeds: hidden, kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        idle: String? = this.idle,
        walk: String? = this.walk,
        run: String? = this.run,
        jump: String? = this.jump,
        takeOff: String? = this.takeOff,
        fall: String? = this.fall,
        land: String? = this.land,
        walkAbove: Float = this.walkAbove,
        runAbove: Float = this.runAbove,
        airborneAbove: Float = this.airborneAbove,
        crossFade: Float = this.crossFade,
    ): SceneLocomotionAnimation = copy(
        idle, walk, run, jump, takeOff, fall, land, walkAbove, runAbove, airborneAbove, crossFade,
    )

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (walkAbove < 0f || runAbove < walkAbove) {
            add(SceneValidationIssue(path, "locomotion_animation needs 0 <= walkAbove <= runAbove"))
        }
        if (airborneAbove <= 0f) add(SceneValidationIssue(path, "locomotion_animation.airborneAbove must be greater than 0"))
        if (crossFade < 0f) add(SceneValidationIssue(path, "locomotion_animation.crossFade must not be negative"))
        if (walkReferenceSpeed != null && walkReferenceSpeed <= 0f) {
            add(SceneValidationIssue(path, "locomotion_animation.walkReferenceSpeed must be greater than 0"))
        }
        if (runReferenceSpeed != null && runReferenceSpeed <= 0f) {
            add(SceneValidationIssue(path, "locomotion_animation.runReferenceSpeed must be greater than 0"))
        }
    }
}

/**
 * Live locomotion animation component tracking position delta and driving appropriate movement clips.
 *
 * @property clips The locomotion animation clip configuration descriptor.
 */
class LocomotionAnimation(val clips: SceneLocomotionAnimation) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var seen = false

    /** Seconds since it was last seen to move. */
    private var sinceMoved = 0f

    /** Speed across the ground, smoothed over about [SPEED_SMOOTHING] seconds. */
    private var across = 0f

    /**
     * Ground speed above which one frame's move is a teleport rather than a stride, far past any
     * run: [SceneLocomotionAnimation.runAbove] times [WARP_SPEED_MULTIPLE], and never below
     * [MIN_WARP_SPEED] so a tiny [SceneLocomotionAnimation.runAbove] still lets real movement through.
     */
    private val warpGroundSpeed = maxOf(clips.runAbove * WARP_SPEED_MULTIPLE, MIN_WARP_SPEED)

    /** Whether it has left the ground and not yet landed. */
    private var airborne = false
    private var falling = false
    private var heldHeight = 0f

    /** A take-off or landing clip playing once, and the seconds it has left. */
    private var once: String? = null
    private var onceLeft = 0f

    /** The clip it last chose, or null before it has moved or stood still. */
    var playing: String? = null
        internal set

    /**
     * Whether [playing] plays once and holds its last frame rather than looping: a take-off or
     * landing, or a clip for the air, whose last pose holds until it lands.
     */
    internal val playingOnce: Boolean
        get() = (once != null && once == playing) || (airborne && playing != null && (playing == clips.jump || playing == clips.fall))

    /** Speed across the ground this frame, smoothed over about [SPEED_SMOOTHING] seconds. */
    internal val groundSpeed: Float get() = across

    /** Whether speed-matched playback is configured on this component (either reference speed set). */
    internal val matchesPlaybackToSpeed: Boolean
        get() = clips.walkReferenceSpeed != null || clips.runReferenceSpeed != null

    /** The authored reference speed for [clip] under speed-matched playback, or null when it plays at its authored rate. */
    internal fun referenceSpeedFor(clip: String?): Float? = when {
        clip == null -> null
        clip == clips.walk -> clips.walkReferenceSpeed
        clip == clips.run -> clips.runReferenceSpeed
        else -> null
    }

    /**
     * The clip for how it moved to world ([x], [y], [z]) over the last [delta] seconds, or null when
     * this frame tells nothing new: its first, or a pause between a fixed-rate mover's steps.
     */
    internal fun clipFor(
        x: Float,
        y: Float,
        z: Float,
        delta: Float,
        ground: GroundContact? = null,
        durationOf: (String) -> Float? = { null },
    ): String? {
        val elapsed = sinceMoved + delta
        val stayedPut = x == lastX && y == lastY && z == lastZ
        if (seen && stayedPut && elapsed < STILL_AFTER) {
            sinceMoved = elapsed
            return null
        }
        val first = !seen
        val dx = x - lastX
        val dz = z - lastZ
        val vertical = (y - lastY) / elapsed
        lastX = x
        lastY = y
        lastZ = z
        sinceMoved = 0f
        seen = true
        if (!first) {
            val groundDelta2 = dx * dx + dz * dz
            across = when {
                // Staying put this long is a stop, which the smoothing would only blur into a walk.
                stayedPut -> 0f
                // A single frame's move too fast to be a stride is a teleport, not locomotion: a
                // controller reconcile, a respawn, a leash home. Folding it into the smoothed speed
                // would switch the clip to a run — and, under speed-matched playback, race it — for
                // the fraction of a second the smoothing takes to bleed the spike off. Ignore it;
                // lastX/Y/Z already advanced to where it landed, so the next frame measures the real
                // speed from there. Compared as distance against [warpGroundSpeed] times [elapsed] so a
                // vanishing step never divides.
                groundDelta2 > (warpGroundSpeed * elapsed) * (warpGroundSpeed * elapsed) -> across
                else -> across + (sqrt(groundDelta2) / elapsed - across) * (1f - exp(-elapsed / SPEED_SMOOTHING))
            }
        }
        return if (first) null else choose(across, vertical, elapsed, ground, durationOf)
    }

    private fun choose(across: Float, vertical: Float, elapsed: Float, ground: GroundContact?, durationOf: (String) -> Float?): String? {
        val wasAirborne = airborne
        if (ground != null) airborne = !ground.grounded else trackAirborne(vertical, clips.airborneAbove, elapsed)
        when {
            airborne && !wasAirborne -> playOnce(clips.takeOff, durationOf)
            !airborne && wasAirborne -> playOnce(clips.land, durationOf)
            else -> onceLeft -= elapsed
        }
        // A landing gives way as soon as it walks off; a take-off plays out while it rises.
        val walkingAway = !airborne && across > clips.walkAbove
        if (onceLeft <= 0f || walkingAway) once = null
        return once ?: loopFor(across, vertical)
    }

    private fun loopFor(across: Float, vertical: Float): String? = when {
        airborne && vertical < 0f -> clips.fall ?: clips.jump
        airborne -> clips.jump
        across > clips.runAbove -> clips.run
        across > clips.walkAbove -> clips.walk
        else -> clips.idle
    }

    private fun playOnce(clip: String?, durationOf: (String) -> Float?) {
        val duration = clip?.let(durationOf) ?: 0f
        once = clip.takeIf { duration > 0f }
        onceLeft = duration
    }

    /** Leaves the ground above [airborneAbove]; lands when a fall stops or its height holds. */
    private fun trackAirborne(vertical: Float, airborneAbove: Float, elapsed: Float) {
        if (abs(vertical) > airborneAbove) airborne = true
        if (!airborne) return
        if (vertical < -STILL) falling = true
        heldHeight = if (abs(vertical) < STILL) heldHeight + elapsed else 0f
        if ((falling && vertical >= -STILL) || heldHeight >= LANDED_AFTER) {
            airborne = false
            falling = false
            heldHeight = 0f
        }
    }
}

/**
 * Binding definition connecting [SceneLocomotionAnimation] descriptors to ECS [LocomotionAnimation] components.
 */
@RegisteredBinding
object LocomotionAnimationBinding : SceneComponentBinding<LocomotionAnimation, SceneLocomotionAnimation> {
    override val componentClass: KClass<LocomotionAnimation> = LocomotionAnimation::class
    override val schemaClass: KClass<SceneLocomotionAnimation> = SceneLocomotionAnimation::class
    override val serializer = SceneLocomotionAnimation.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneLocomotionAnimation,
        context: SceneResolutionContext,
    ) {
        world.add(entity, LocomotionAnimation(component))
    }

    override fun export(world: World, entity: Entity, component: LocomotionAnimation): SceneLocomotionAnimation =
        component.clips
}

/**
 * Plays each [LocomotionAnimation] entity's clip for how far it moved since the last frame. Runs
 * before [AnimationSystem], which advances the chosen clip.
 */
class LocomotionAnimationSystem : System {
    override fun update(world: World, delta: Float) {
        if (delta <= 0f) return
        world.queryEach(Transform::class, LocomotionAnimation::class) { entity, transform, locomotion ->
            val animator = world.get<Animator>(entity) ?: return@queryEach
            val placed = transform.worldMatrix
            val clips = animator.player.clipEntries
            val wanted = locomotion.clipFor(placed.m03, placed.m13, placed.m23, delta, world.groundContactOf(entity)) { clips[it]?.duration }
            if (wanted != null && wanted != locomotion.playing && wanted in clips) {
                locomotion.playing = wanted
                val playback = if (locomotion.playingOnce) AnimationPlayback.Once else AnimationPlayback.Loop
                animator.player.crossFadeTo(wanted, locomotion.clips.crossFade, playback)
            }
            // Speed-matched playback (opt-in): scale a walk/run clip by ground speed over its authored
            // reference speed, so the stride matches travel. Pure ratio, one division, no allocation;
            // a non-referenced clip (idle, air, or the feature off) keeps its authored rate.
            if (locomotion.matchesPlaybackToSpeed) {
                val reference = locomotion.referenceSpeedFor(locomotion.playing)
                animator.player.speed = if (reference != null) locomotion.groundSpeed / reference else 1f
            }
        }
    }
}

/** The [GroundContact] on [entity] or the nearest node above it, or null when none has one. */
private fun World.groundContactOf(entity: Entity): GroundContact? {
    var node: Entity? = entity
    while (node != null) {
        get<GroundContact>(node)?.let { return it }
        node = get<Transform>(node)?.parent
    }
    return null
}

private const val DEFAULT_WALK_ABOVE = 0.1f
private const val DEFAULT_RUN_ABOVE = 4f
private const val DEFAULT_AIRBORNE_ABOVE = 2f
private const val DEFAULT_CROSS_FADE = 0.15f

/** Seconds without motion before a frame counts as standing still. */
private const val STILL_AFTER = 0.1f

/** Seconds over which the speed across the ground is smoothed: two frames slower stay a run. */
private const val SPEED_SMOOTHING = 0.1f

/** Multiple of [SceneLocomotionAnimation.runAbove] above which one frame's move is a teleport, not a stride. */
private const val WARP_SPEED_MULTIPLE = 4f

/** Floor for the teleport threshold, in units per second, far past any character's run. */
private const val MIN_WARP_SPEED = 30f

/** Vertical units per second below which it neither rises nor falls. */
private const val STILL = 0.1f

/** Seconds holding its height that count as landed. */
private const val LANDED_AFTER = 0.25f
