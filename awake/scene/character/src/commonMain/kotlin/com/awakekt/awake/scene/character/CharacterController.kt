/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.character.CharacterContact
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.physics.character.CharacterConfig
import com.awakekt.awake.scene.physics.character.KinematicCharacterController
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.reflect.KClass

/**
 * A character that walks and falls through physics instead of through walls, as authored in a scene.
 *
 * Its body is an upright capsule of [radius] around a cylinder of `2 * halfHeight`. It climbs steps
 * up to [stepHeight] and walks up slopes to [slopeLimit] radians. It jumps at [jumpSpeed] units per
 * second, and a [jumpSpeed] of 0 means it can't jump. [gravity] is its downward acceleration.
 *
 * @property radius The radius of the character's upright capsule shape.
 * @property halfHeight Half the height of the cylindrical section of the capsule.
 * @property stepHeight Maximum vertical height the character can climb as a step.
 * @property slopeLimit Maximum slope angle in radians the character can walk up.
 * @property jumpSpeed Upward impulse speed applied when jumping.
 * @property gravity Downward gravitational acceleration acting on the character.
 */
@Serializable
@SerialName("character_controller")
data class SceneCharacterController(
    @PropertyRange(min = 0.0, exclusiveMin = true) val radius: Float = DEFAULT_RADIUS,
    @PropertyRange(min = 0.0, exclusiveMin = true) val halfHeight: Float = DEFAULT_HALF_HEIGHT,
    @PropertyRange(min = 0.0) val stepHeight: Float = DEFAULTS.stepHeight,
    val slopeLimit: Float = DEFAULTS.slopeLimitRadians,
    @PropertyRange(min = 0.0) val jumpSpeed: Float = 0f,
    val gravity: Float = DEFAULT_GRAVITY,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (radius <= 0f || halfHeight <= 0f) {
            add(SceneValidationIssue(path, "character_controller.radius and halfHeight must be greater than 0"))
        }
        if (stepHeight < 0f) add(SceneValidationIssue(path, "character_controller.stepHeight must not be negative"))
        if (jumpSpeed < 0f) add(SceneValidationIssue(path, "character_controller.jumpSpeed must not be negative"))
    }
}

/**
 * The live character. [CharacterControllerSystem] builds its [KinematicCharacterController] on the
 * first update, once a physics world exists.
 *
 * @property config Character kinematic configuration including collision shape and slope limits.
 * @property jumpSpeed Upward impulse velocity applied during jumps.
 * @property gravity Downward gravitational acceleration acting on this character.
 */
class CharacterController(
    val config: CharacterConfig,
    var jumpSpeed: Float,
    var gravity: Float,
) {
    internal var controller: KinematicCharacterController? = null
    internal var verticalVelocity: Float = 0f

    /**
     * How it moves: [CharacterMotionMode.Grounded], walking, or [CharacterMotionMode.Free], moved
     * by a world velocity with gravity and ground off and collision on. Switched at run time, not
     * saved with the scene.
     *
     * Switching either way drops the fall speed, so nothing carried from one mode moves the other.
     * Entering free motion leaves the ground at once; returning to walking finds the ground again on
     * the next step, falling onto it from wherever the character is.
     */
    var motionMode: CharacterMotionMode = CharacterMotionMode.Grounded
        set(value) {
            if (value != field) verticalVelocity = 0f
            field = value
        }

    /**
     * The lowest height, in world units, free motion moves the character's centre to. A step that
     * would end below it asks for no more than reaching it, and a character below it is moved up,
     * swept like any other move so the clamp never pushes it into something. Unbounded by default;
     * ignored while [motionMode] is [CharacterMotionMode.Grounded]. When above [freeMaxY], [freeMaxY] wins.
     */
    var freeMinY: Float = Float.NEGATIVE_INFINITY

    /** The highest height free motion moves the character's centre to; see [freeMinY]. Unbounded by default. */
    var freeMaxY: Float = Float.POSITIVE_INFINITY

    // The last two stepped poses, which CharacterControllerSystem blends between rendered frames.
    private val previousPosition = Vec3f()
    private val currentPosition = Vec3f()
    private var previousYaw = 0f
    private var currentYaw = 0f
    private var blendedYaw = Float.NaN
    private var stepped = false

    /** Whether it stood on walkable ground after its last move. Never while [motionMode] is free. */
    val isGrounded: Boolean
        get() = motionMode == CharacterMotionMode.Grounded && controller?.isGrounded ?: false

    /**
     * What its last step ran into: whether it was stopped, the contact normal as the sweep met it,
     * before any slide, and whether anything lay just below it. A free-moving character reads this
     * to bounce, damp or find the floor without becoming grounded. Null until the first step.
     */
    val lastContact: CharacterContact? get() = controller?.lastContact

    /**
     * The character's own body in the physics world, when its config gives it one
     * ([CharacterConfig.innerBody]) and the controller is built; null otherwise. A query that must
     * not find the character itself, such as a ray to the label over its head, leaves it out.
     */
    val body: BodyHandle? get() = controller?.body

    /**
     * Puts the character at [position] at once, without sweeping through what lies between, and stops
     * its fall: a respawn or a teleport, not movement. Writes [transform] too, so the scene shows it
     * there before the next step.
     */
    fun teleport(transform: Transform, position: Vec3f) {
        controller?.teleport(position)
        transform.position.set(position)
        verticalVelocity = 0f
        // No blend from where it was: a respawn shows at once.
        previousPosition.set(position)
        currentPosition.set(position)
        previousYaw = currentYaw
    }

    /** Puts back the yaw a frame's blend left in [transform], unless something else has turned it since. */
    internal fun restoreSteppedYaw(transform: Transform) {
        if (stepped && transform.rotation.y == blendedYaw) transform.rotation.y = currentYaw
    }

    /** Keeps the pose a step left in [transform] as the newest of the two the frames blend between. */
    internal fun record(transform: Transform) {
        if (stepped) {
            previousPosition.set(currentPosition)
            previousYaw = currentYaw
        } else {
            previousPosition.set(transform.position)
            previousYaw = transform.rotation.y
        }
        currentPosition.set(transform.position)
        currentYaw = transform.rotation.y
        stepped = true
    }

    /**
     * Shows the pose [alpha] of the way between steps, the yaw turning the short way.
     * Invalid blend fractions leave the pose alone; non-finite stepped yaw leaves rendered yaw
     * alone while position still blends. Finite yaw is reduced by whole turns before blending.
     */
    internal fun blendInto(transform: Transform, alpha: Float) {
        val validBlend = stepped && alpha.isFinite() && alpha in 0f..1f
        if (!validBlend) return
        transform.position.set(
            previousPosition.x + (currentPosition.x - previousPosition.x) * alpha,
            previousPosition.y + (currentPosition.y - previousPosition.y) * alpha,
            previousPosition.z + (currentPosition.z - previousPosition.z) * alpha,
        )
        if (!currentYaw.isFinite() || !previousYaw.isFinite()) {
            blendedYaw = Float.NaN
            return
        }
        val previous = previousYaw.toDouble() % TWO_PI
        val current = currentYaw.toDouble() % TWO_PI
        var turn = (current - previous) % TWO_PI
        if (turn > PI) turn -= TWO_PI
        if (turn < -PI) turn += TWO_PI
        blendedYaw = (previous + turn * alpha).toFloat()
        transform.rotation.y = blendedYaw
    }
}

/** Bi-directional binding connecting [CharacterController] with its serializable [SceneCharacterController] schema. */
@RegisteredBinding
object CharacterControllerBinding : SceneComponentBinding<CharacterController, SceneCharacterController> {
    override val componentClass: KClass<CharacterController> = CharacterController::class
    override val schemaClass: KClass<SceneCharacterController> = SceneCharacterController::class
    override val serializer = SceneCharacterController.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneCharacterController,
        context: SceneResolutionContext,
    ) {
        val config = CharacterConfig(
            shape = CapsuleShape(halfHeight = component.halfHeight, radius = component.radius),
            slopeLimitRadians = component.slopeLimit,
            stepHeight = component.stepHeight,
            stepDownDistance = component.stepHeight,
        )
        world.add(entity, CharacterController(config, component.jumpSpeed, component.gravity))
        world.add(entity, GroundContact())
    }

    override fun export(world: World, entity: Entity, component: CharacterController) = SceneCharacterController(
        radius = component.config.shape.radius,
        halfHeight = component.config.shape.halfHeight,
        stepHeight = component.config.stepHeight,
        slopeLimit = component.config.slopeLimitRadians,
        jumpSpeed = component.jumpSpeed,
        gravity = component.gravity,
    )
}

/**
 * Registers `character_controller` for loading and saving.
 *
 * @return This registry instance for chaining.
 */
fun SceneComponentRegistry.registerCharacter(): SceneComponentRegistry = register(CharacterSceneComponents)

private const val DEFAULT_RADIUS = 0.5f
private const val DEFAULT_HALF_HEIGHT = 0.5f
private const val DEFAULT_GRAVITY = -9.81f
private val DEFAULTS = CharacterConfig(CapsuleShape(DEFAULT_HALF_HEIGHT, DEFAULT_RADIUS))

private const val TWO_PI = 2 * PI
