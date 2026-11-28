/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.math.damp
import com.awakekt.awake.core.math.halfLifeForRate
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2System
import com.awakekt.awake.ecs.FamilySpec
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.get
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
import kotlin.reflect.KClass

/**
 * Motion-driven 2D sprite animation and horizontal facing for an entity with [SceneSprite] and
 * [SceneSpriteClips].
 *
 * Measures world X/Y travel from the entity's [Transform], so it works whether the sprite sits on
 * the moving entity itself or on a child node under a `character_controller`, `movement_control`,
 * AI mover, or `keyframe_animation` patrol path. When the entity or an ancestor holds a
 * [GroundContact], [jump] and [fall] play exactly while off the ground; otherwise vertical speed
 * above [airborneAbove] enters airborne playback.
 *
 * When [flipOnMoveX] is true, horizontal motion faster than [walkAbove] updates [Sprite.flipX] to
 * face the direction of travel while preserving facing when standing still or jumping vertically.
 * Clips outside this component (for example an attack or emote triggered from a blueprint or
 * script) play uninterrupted until they finish, after which locomotion resumes.
 *
 * @property idle Clip played while standing still on the ground.
 * @property walk Clip played above [walkAbove] horizontal speed.
 * @property run Clip played above [runAbove] horizontal speed, falling back to [walk] when null.
 * @property jump Clip played while airborne, or while rising when [fall] is supplied.
 * @property fall Clip played while descending in the air, falling back to [jump] when null.
 * @property walkAbove Horizontal speed in scene units per second above which [walk] plays.
 * @property runAbove Horizontal speed in scene units per second above which [run] plays.
 * @property airborneAbove Vertical speed that starts airborne playback without a [GroundContact].
 * @property flipOnMoveX Whether horizontal movement mirrors [Sprite.flipX] automatically.
 * @property defaultFacingRight Whether unflipped (`flipX = false`) art faces right (+X).
 */
@Serializable
@SerialName("sprite_locomotion")
data class SceneSpriteLocomotion(
    val idle: String? = null,
    val walk: String? = null,
    val run: String? = null,
    val jump: String? = null,
    val fall: String? = null,
    @PropertyRange(min = 0.0) val walkAbove: Float = DEFAULT_WALK_ABOVE,
    @PropertyRange(min = 0.0) val runAbove: Float = DEFAULT_RUN_ABOVE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val airborneAbove: Float = DEFAULT_AIRBORNE_ABOVE,
    val flipOnMoveX: Boolean = true,
    val defaultFacingRight: Boolean = true,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (!walkAbove.isFiniteNonNegative() || !runAbove.isFinite() || runAbove < walkAbove) {
            add(SceneValidationIssue(path, "sprite_locomotion needs finite 0 <= walkAbove <= runAbove"))
        }
        if (!airborneAbove.isFinitePositive()) {
            add(SceneValidationIssue(path, "sprite_locomotion.airborneAbove must be finite and greater than 0"))
        }
    }

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> = buildList {
        addAll(validate(path))
        if (peers.none { it is SceneSprite }) {
            add(SceneValidationIssue(path, "sprite_locomotion requires a sprite on the same node"))
        }
        if (peers.none { it is SceneSpriteClips }) {
            add(SceneValidationIssue(path, "sprite_locomotion requires sprite_clips on the same node"))
        }
    }
}

/**
 * Live 2D locomotion driver attached to an entity with [Sprite] and [SpriteClips].
 *
 * @property config Authored clip names, thresholds, and facing flags retained for scene export.
 */
class SpriteLocomotion(val config: SceneSpriteLocomotion) {
    private var lastX = 0f
    private var lastY = 0f
    private var seen = false
    private var sinceMoved = 0f
    private var horizontalSpeed = 0f
    private var verticalSpeed = 0f
    private var airborne = false
    private var falling = false
    private var heldHeight = 0f

    private val warpSpeed = maxOf(config.runAbove * WARP_SPEED_MULTIPLE, MIN_WARP_SPEED)

    /** The clip last chosen by locomotion, or null before the first movement observation. */
    var playing: String? = null
        private set

    /** Smoothed horizontal speed in scene units per second. */
    val speedX: Float get() = horizontalSpeed

    internal fun update(
        sprite: Sprite,
        clips: SpriteClips,
        x: Float,
        y: Float,
        delta: Float,
        grounded: Boolean?,
    ) {
        if (!x.isFinite() || !y.isFinite() || !delta.isFinitePositive()) return
        if (!seen) {
            initializeAt(clips, x, y)
            return
        }
        stepMovement(sprite, clips, x, y, delta, grounded)
    }

    private fun initializeAt(clips: SpriteClips, x: Float, y: Float) {
        lastX = x
        lastY = y
        seen = true
        selectClip(clips, chooseClip(vertical = 0f))
    }

    private fun stepMovement(
        sprite: Sprite,
        clips: SpriteClips,
        x: Float,
        y: Float,
        delta: Float,
        grounded: Boolean?,
    ) {
        val elapsed = sinceMoved + delta
        val dx = x - lastX
        val dy = y - lastY
        val stayedPut = dx == 0f && dy == 0f
        if (stayedPut && elapsed < STILL_AFTER) {
            sinceMoved = elapsed
            return
        }

        lastX = x
        lastY = y
        sinceMoved = 0f
        updateSpeedsAndFacing(sprite, dx, dy, elapsed, stayedPut)

        val vertical = dy / elapsed
        updateAirborne(vertical, elapsed, grounded)

        val active = clips.activeClipId
        if (active == null || isManagedClip(active) || clips.isFinished) {
            selectClip(clips, chooseClip(vertical))
        }
    }

    private fun updateSpeedsAndFacing(sprite: Sprite, dx: Float, dy: Float, elapsed: Float, stayedPut: Boolean) {
        if (stayedPut) {
            horizontalSpeed = 0f
            verticalSpeed = 0f
            return
        }
        val maxStep = warpSpeed * elapsed
        val absDx = abs(dx)
        if (absDx <= maxStep) {
            val instantX = absDx / elapsed
            horizontalSpeed = damp(horizontalSpeed, instantX, SPEED_HALF_LIFE, elapsed)
            if (config.flipOnMoveX && instantX > config.walkAbove) {
                val movingLeft = dx < 0f
                sprite.flipX = if (config.defaultFacingRight) movingLeft else !movingLeft
            }
        }
        if (abs(dy) <= maxStep) {
            verticalSpeed = damp(verticalSpeed, dy / elapsed, SPEED_HALF_LIFE, elapsed)
        }
    }

    private fun updateAirborne(vertical: Float, elapsed: Float, grounded: Boolean?) {
        if (grounded != null) {
            airborne = !grounded
            falling = airborne && vertical < -STILL
            heldHeight = 0f
        } else {
            trackAirborne(vertical, elapsed)
        }
    }

    private fun selectClip(clips: SpriteClips, chosen: String?) {
        if (chosen != null && clips.hasClip(chosen)) {
            playing = chosen
            clips.play(chosen, restart = false)
        }
    }

    private fun isManagedClip(name: String): Boolean =
        name == config.idle ||
            name == config.walk ||
            name == config.run ||
            name == config.jump ||
            name == config.fall

    private fun chooseClip(vertical: Float): String? = when {
        airborne && vertical < -STILL -> config.fall ?: config.jump
        airborne -> config.jump ?: config.fall
        horizontalSpeed > config.runAbove -> config.run ?: config.walk
        horizontalSpeed > config.walkAbove -> config.walk ?: config.run
        else -> config.idle
    }

    private fun trackAirborne(vertical: Float, elapsed: Float) {
        if (abs(vertical) > config.airborneAbove) airborne = true
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

/** Attaches [SceneSpriteLocomotion] to live entities and exports its configuration unchanged. */
@RegisteredBinding
object SpriteLocomotionBinding : SceneComponentBinding<SpriteLocomotion, SceneSpriteLocomotion> {
    override val componentClass: KClass<SpriteLocomotion> = SpriteLocomotion::class
    override val schemaClass: KClass<SceneSpriteLocomotion> = SceneSpriteLocomotion::class
    override val serializer = SceneSpriteLocomotion.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneSpriteLocomotion,
        context: SceneResolutionContext,
    ) {
        world.add(entity, SpriteLocomotion(component))
    }

    override fun export(world: World, entity: Entity, component: SpriteLocomotion): SceneSpriteLocomotion =
        component.config
}

/**
 * Selects each [SpriteLocomotion] entity's 2D clip and horizontal facing from its world movement.
 * Runs before [SpriteClipSystem] so the selected clip advances on the same frame.
 *
 * @param isPlaying Whether the host advances simulation; false leaves edited sprites untouched.
 */
class SpriteLocomotionSystem(private val isPlaying: () -> Boolean = { true }) :
    Family2System<Transform, SpriteLocomotion>(
        Transform::class,
        SpriteLocomotion::class,
        WITH_SPRITE_AND_CLIPS,
    ) {
    override fun shouldProcess(world: World, delta: Float): Boolean = isPlaying() && delta.isFinitePositive()

    override fun process(
        world: World,
        delta: Float,
        entity: Entity,
        first: Transform,
        second: SpriteLocomotion,
    ) {
        val sprite = world.get(entity, SPRITE) ?: return
        val clips = world.get(entity, SPRITE_CLIPS) ?: return
        val placed = first.worldMatrix
        val contact = world.groundContactOf(entity)
        second.update(sprite, clips, placed.m03, placed.m13, delta, contact?.grounded)
    }

    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    private fun World.groundContactOf(entity: Entity): GroundContact? {
        var node = entity
        while (true) {
            get(node, GROUND_CONTACT)?.let { return it }
            node = get(node, TRANSFORM)?.parent ?: return null
        }
    }

    private companion object {
        val TRANSFORM = componentType<Transform>()
        val SPRITE = componentType<Sprite>()
        val SPRITE_CLIPS = componentType<SpriteClips>()
        val GROUND_CONTACT = componentType<GroundContact>()
        val WITH_SPRITE_AND_CLIPS = FamilySpec.all(Sprite::class, SpriteClips::class)
    }
}

private const val DEFAULT_WALK_ABOVE = 0.1f
private const val DEFAULT_RUN_ABOVE = 4f
private const val DEFAULT_AIRBORNE_ABOVE = 2f
private const val STILL_AFTER = 0.1f
private const val SPEED_SMOOTHING = 0.1f
private val SPEED_HALF_LIFE = halfLifeForRate(1f / SPEED_SMOOTHING)
private const val WARP_SPEED_MULTIPLE = 4f
private const val MIN_WARP_SPEED = 30f
private const val STILL = 0.1f
private const val LANDED_AFTER = 0.25f

private fun Float.isFiniteNonNegative(): Boolean = isFinite() && this >= 0f
private fun Float.isFinitePositive(): Boolean = isFinite() && this > 0f
