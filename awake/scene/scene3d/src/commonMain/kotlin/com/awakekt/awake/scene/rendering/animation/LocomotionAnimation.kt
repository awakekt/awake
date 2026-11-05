/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.LocomotionPlayer
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
 * [jump] plays exactly while it is off the ground, and [GroundContact.freeMotion] switches playback
 * to [free] (or reuses [idle], [walk] and [run] against 3D travel speed when [free] is null) without
 * firing [takeOff] or [land]. Otherwise it lands when its fall stops, or when it holds its height for
 * a quarter second, and since climbing a slope also moves it upward, [airborneAbove] must sit above
 * the vertical speed its steepest walkable slope gives at a run.
 *
 * [walkReferenceSpeed] and [runReferenceSpeed] opt a clip into speed-matched playback: the ground
 * speed, in units per second, its walk or run cycle was authored for. When set, [LocomotionAnimationSystem]
 * scales that clip's playback by the mover's smoothed ground speed over this reference, so the stride
 * matches the distance covered and the feet plant instead of sliding. Left null (the default), the clip
 * plays at its authored rate, as before.
 *
 * @property idle Clip played while standing still on the ground.
 * @property walk Clip played above [walkAbove] ground speed.
 * @property run Clip played above [runAbove] ground speed.
 * @property jump Clip played while airborne, or while rising when [fall] is supplied.
 * @property takeOff Clip played once when leaving the ground.
 * @property fall Clip played while descending in the air, falling back to [jump] when null.
 * @property land Clip played once upon touching down unless already walking away.
 * @property walkAbove Ground speed threshold in units per second above which [walk] plays.
 * @property runAbove Ground speed threshold in units per second above which [run] plays.
 * @property airborneAbove Vertical speed threshold that starts airborne motion without a [GroundContact].
 * @property crossFade Blend duration in seconds when changing clips.
 * @property walkReferenceSpeed Authored ground speed of the [walk] cycle for speed-matched playback, or null.
 * @property runReferenceSpeed Authored ground speed of the [run] cycle for speed-matched playback, or null.
 * @property free Optional 3D free-motion clip configuration used while [GroundContact.freeMotion] is true.
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
    val free: SceneFreeLocomotionAnimation? = null,
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
    ) : this(
        idle = idle,
        walk = walk,
        run = run,
        jump = jump,
        takeOff = takeOff,
        fall = fall,
        land = land,
        walkAbove = walkAbove,
        runAbove = runAbove,
        airborneAbove = airborneAbove,
        crossFade = crossFade,
        walkReferenceSpeed = null,
        runReferenceSpeed = null,
        free = null,
    )

    /** [SceneLocomotionAnimation] as it was before [free]: hidden, kept so a consumer compiled against it still links. */
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
        walkReferenceSpeed: Float? = null,
        runReferenceSpeed: Float? = null,
    ) : this(
        idle = idle,
        walk = walk,
        run = run,
        jump = jump,
        takeOff = takeOff,
        fall = fall,
        land = land,
        walkAbove = walkAbove,
        runAbove = runAbove,
        airborneAbove = airborneAbove,
        crossFade = crossFade,
        walkReferenceSpeed = walkReferenceSpeed,
        runReferenceSpeed = runReferenceSpeed,
        free = null,
    )

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
        idle = idle,
        walk = walk,
        run = run,
        jump = jump,
        takeOff = takeOff,
        fall = fall,
        land = land,
        walkAbove = walkAbove,
        runAbove = runAbove,
        airborneAbove = airborneAbove,
        crossFade = crossFade,
        walkReferenceSpeed = this.walkReferenceSpeed,
        runReferenceSpeed = this.runReferenceSpeed,
        free = this.free,
    )

    /** [copy] as it was before [free]: hidden, kept so a consumer compiled against it still links. */
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
        walkReferenceSpeed: Float? = this.walkReferenceSpeed,
        runReferenceSpeed: Float? = this.runReferenceSpeed,
    ): SceneLocomotionAnimation = copy(
        idle = idle,
        walk = walk,
        run = run,
        jump = jump,
        takeOff = takeOff,
        fall = fall,
        land = land,
        walkAbove = walkAbove,
        runAbove = runAbove,
        airborneAbove = airborneAbove,
        crossFade = crossFade,
        walkReferenceSpeed = walkReferenceSpeed,
        runReferenceSpeed = runReferenceSpeed,
        free = this.free,
    )

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!walkAbove.isFiniteNonNegative() || !runAbove.isFinite() || runAbove < walkAbove) {
            add(SceneValidationIssue(path, "locomotion_animation needs finite 0 <= walkAbove <= runAbove"))
        }
        if (!airborneAbove.isFinitePositive()) add(SceneValidationIssue(path, "locomotion_animation.airborneAbove must be finite and greater than 0"))
        if (!crossFade.isFiniteNonNegative()) add(SceneValidationIssue(path, "locomotion_animation.crossFade must be finite and non-negative"))
        if (walkReferenceSpeed != null && !walkReferenceSpeed.isFinitePositive()) {
            add(SceneValidationIssue(path, "locomotion_animation.walkReferenceSpeed must be finite and greater than 0"))
        }
        if (runReferenceSpeed != null && !runReferenceSpeed.isFinitePositive()) {
            add(SceneValidationIssue(path, "locomotion_animation.runReferenceSpeed must be finite and greater than 0"))
        }
        free?.validate("$path.free")?.let(::addAll)
    }
}

/**
 * Authored clip names, speed thresholds, crossfade override, and reference speeds for 3D free-motion
 * playback inside [SceneLocomotionAnimation.free] (such as swimming, hovering, gliding, or zero-gravity
 * flight).
 *
 * @property idle Clip played while hovering or stationary in 3D space.
 * @property move Clip played above [moveAbove] 3D travel speed when neither [fast], [ascend] nor [descend] takes precedence.
 * @property fast Clip played above [fastAbove] 3D travel speed.
 * @property ascend Optional clip played when climbing faster than [verticalAbove] and at least as fast vertically as horizontally.
 * @property descend Optional clip played when descending faster than [verticalAbove] downward and at least as fast vertically as horizontally.
 * @property moveAbove 3D speed threshold in units per second above which [move] is selected.
 * @property fastAbove 3D speed threshold in units per second above which [fast] is selected.
 * @property verticalAbove Vertical speed magnitude in units per second above which [ascend] or [descend] can apply.
 * @property crossFade Optional blend duration in seconds while in free motion; null falls back to [SceneLocomotionAnimation.crossFade].
 * @property moveReferenceSpeed Authored 3D travel speed of the [move] cycle for speed-matched playback, or null.
 * @property fastReferenceSpeed Authored 3D travel speed of the [fast] cycle for speed-matched playback, or null.
 */
@Serializable
data class SceneFreeLocomotionAnimation(
    val idle: String? = null,
    val move: String? = null,
    val fast: String? = null,
    val ascend: String? = null,
    val descend: String? = null,
    @PropertyRange(min = 0.0) val moveAbove: Float = DEFAULT_FREE_MOVE_ABOVE,
    @PropertyRange(min = 0.0) val fastAbove: Float = DEFAULT_FREE_FAST_ABOVE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val verticalAbove: Float = DEFAULT_FREE_VERTICAL_ABOVE,
    @PropertyRange(min = 0.0) val crossFade: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val moveReferenceSpeed: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val fastReferenceSpeed: Float? = null,
) {
    internal fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!moveAbove.isFiniteNonNegative() || !fastAbove.isFinite() || fastAbove < moveAbove) {
            add(SceneValidationIssue(path, "locomotion_animation.free needs finite 0 <= moveAbove <= fastAbove"))
        }
        if (!verticalAbove.isFinitePositive()) {
            add(SceneValidationIssue(path, "locomotion_animation.free.verticalAbove must be finite and greater than 0"))
        }
        if (crossFade != null && !crossFade.isFiniteNonNegative()) {
            add(SceneValidationIssue(path, "locomotion_animation.free.crossFade must be finite and non-negative"))
        }
        if (moveReferenceSpeed != null && !moveReferenceSpeed.isFinitePositive()) {
            add(SceneValidationIssue(path, "locomotion_animation.free.moveReferenceSpeed must be finite and greater than 0"))
        }
        if (fastReferenceSpeed != null && !fastReferenceSpeed.isFinitePositive()) {
            add(SceneValidationIssue(path, "locomotion_animation.free.fastReferenceSpeed must be finite and greater than 0"))
        }
    }
}

/**
 * Live locomotion animation component tracking position delta and driving appropriate movement clips.
 *
 * @property clips The locomotion animation clip configuration descriptor.
 */
class LocomotionAnimation(val clips: SceneLocomotionAnimation) {
    internal val runtime = LocomotionPlayer(clips.toLocomotionConfig())

    /** The clip last selected by the position-driven locomotion capability. */
    val playing: String? get() = runtime.playing

    /** Whether the selected clip plays once and holds its final frame. */
    internal val playingOnce: Boolean get() = runtime.playingOnce
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
        if (!delta.isFinitePositive()) return
        world.queryEach(TRANSFORM, LOCOMOTION) { entity, transform, locomotion ->
            val animator = world.get<Animator>(entity) ?: return@queryEach
            val placed = transform.worldMatrix
            val contact = world.groundContactOf(entity)
            locomotion.runtime.update(
                animator.player,
                placed.m03,
                placed.m13,
                placed.m23,
                delta,
                grounded = contact?.grounded,
                freeMotion = contact?.freeMotion == true,
            )
        }
    }

    private companion object {
        val TRANSFORM = Transform::class
        val LOCOMOTION = LocomotionAnimation::class
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
private const val DEFAULT_FREE_MOVE_ABOVE = 0.1f
private const val DEFAULT_FREE_FAST_ABOVE = 4f
private const val DEFAULT_FREE_VERTICAL_ABOVE = 0.5f

private fun Float.isFiniteNonNegative(): Boolean = isFinite() && this >= 0f

private fun Float.isFinitePositive(): Boolean = isFinite() && this > 0f
