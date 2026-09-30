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
 * [walkAbove] units per second across the ground, [run] above [runAbove], and [jump] while it rises
 * or falls faster than [airborneAbove]. Clip names are the model's own animations; a clip left
 * null, or one the model lacks, keeps whatever plays. Changes blend over [crossFade] seconds.
 *
 * The speeds are measured from the entity's world position, so it animates however it is moved:
 * by a character controller, straight through the world, or by a script, and a model on a child
 * node of the moving entity animates with it. Climbing a slope also
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
    internal var lastX = 0f
    internal var lastY = 0f
    internal var lastZ = 0f
    internal var seen = false

    /** The clip it last chose, or null before it has moved or stood for a frame. */
    var playing: String? = null
        internal set
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
            val x = placed.m03
            val y = placed.m13
            val z = placed.m23
            if (locomotion.seen) {
                val dx = x - locomotion.lastX
                val dz = z - locomotion.lastZ
                val across = sqrt(dx * dx + dz * dz) / delta
                val vertical = abs(y - locomotion.lastY) / delta
                val clips = locomotion.clips
                val wanted = when {
                    vertical > clips.airborneAbove -> clips.jump
                    across > clips.runAbove -> clips.run
                    across > clips.walkAbove -> clips.walk
                    else -> clips.idle
                }
                if (wanted != null && wanted != locomotion.playing && wanted in animator.player.clipEntries) {
                    animator.player.crossFadeTo(wanted, clips.crossFade)
                    locomotion.playing = wanted
                }
            }
            locomotion.lastX = x
            locomotion.lastY = y
            locomotion.lastZ = z
            locomotion.seen = true
        }
    }
}

private const val DEFAULT_WALK_ABOVE = 0.1f
private const val DEFAULT_RUN_ABOVE = 4f
private const val DEFAULT_AIRBORNE_ABOVE = 2f
private const val DEFAULT_CROSS_FADE = 0.15f
