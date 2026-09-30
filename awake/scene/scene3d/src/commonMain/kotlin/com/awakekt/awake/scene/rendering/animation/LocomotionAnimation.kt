/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.reflect.KClass

/**
 * Which of a skinned model's clips plays for how the entity moves: [idle] standing, [walk] above
 * [walkAbove] units per second across the ground, [run] above [runAbove], and [jump] from when it
 * rises or falls faster than [airborneAbove] until it lands. Clip names are the model's own
 * animations; a clip left null, or one the model lacks, keeps whatever plays. Changes blend over
 * [crossFade] seconds.
 *
 * The speeds are measured from the entity's world position, so it animates however it is moved:
 * by a character controller, straight through the world, or by a script, and a model on a child
 * node of the moving entity animates with it. A mover stepped at a fixed rate stays put between
 * its steps, so a frame without motion counts as a stop only after a tenth of a second. It lands
 * when its fall stops, or when it holds its height for a quarter second. Climbing a slope also
 * moves it upward, so [airborneAbove] must sit above the vertical speed its steepest walkable slope
 * gives at a run.
 */
@Serializable
@SerialName("locomotion_animation")
data class SceneLocomotionAnimation(
    val idle: String? = null,
    val walk: String? = null,
    val run: String? = null,
    val jump: String? = null,
    val walkAbove: Float = DEFAULT_WALK_ABOVE,
    val runAbove: Float = DEFAULT_RUN_ABOVE,
    val airborneAbove: Float = DEFAULT_AIRBORNE_ABOVE,
    val crossFade: Float = DEFAULT_CROSS_FADE,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (walkAbove < 0f || runAbove < walkAbove) {
            add(SceneValidationIssue(path, "locomotion_animation needs 0 <= walkAbove <= runAbove"))
        }
        if (airborneAbove <= 0f) add(SceneValidationIssue(path, "locomotion_animation.airborneAbove must be greater than 0"))
        if (crossFade < 0f) add(SceneValidationIssue(path, "locomotion_animation.crossFade must not be negative"))
    }
}

/** [SceneLocomotionAnimation] on a live entity, with the last position it was seen at. */
class LocomotionAnimation(val clips: SceneLocomotionAnimation) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var seen = false

    /** Seconds since it was last seen to move. */
    private var sinceMoved = 0f

    /** Whether it has left the ground and not yet landed. */
    private var airborne = false
    private var falling = false
    private var heldHeight = 0f

    /** The clip it last chose, or null before it has moved or stood still. */
    var playing: String? = null
        internal set

    /**
     * The clip for how it moved to world ([x], [y], [z]) over the last [delta] seconds, or null when
     * this frame tells nothing new: its first, or a pause between a fixed-rate mover's steps.
     */
    internal fun clipFor(x: Float, y: Float, z: Float, delta: Float): String? {
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
        return if (first) null else choose(sqrt(dx * dx + dz * dz) / elapsed, vertical, elapsed)
    }

    private fun choose(across: Float, vertical: Float, elapsed: Float): String? {
        trackAirborne(vertical, clips.airborneAbove, elapsed)
        return when {
            airborne -> clips.jump
            across > clips.runAbove -> clips.run
            across > clips.walkAbove -> clips.walk
            else -> clips.idle
        }
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
            val wanted = locomotion.clipFor(placed.m03, placed.m13, placed.m23, delta)
            if (wanted != null && wanted != locomotion.playing && wanted in animator.player.clipEntries) {
                animator.player.crossFadeTo(wanted, locomotion.clips.crossFade)
                locomotion.playing = wanted
            }
        }
    }
}

private const val DEFAULT_WALK_ABOVE = 0.1f
private const val DEFAULT_RUN_ABOVE = 4f
private const val DEFAULT_AIRBORNE_ABOVE = 2f
private const val DEFAULT_CROSS_FADE = 0.15f

/** Seconds without motion before a frame counts as standing still. */
private const val STILL_AFTER = 0.1f

/** Vertical units per second below which it neither rises nor falls. */
private const val STILL = 0.1f

/** Seconds holding its height that count as landed. */
private const val LANDED_AFTER = 0.25f
