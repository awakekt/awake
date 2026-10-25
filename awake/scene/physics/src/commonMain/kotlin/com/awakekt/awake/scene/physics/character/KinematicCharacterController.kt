/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.PhysicsWorld
import kotlin.math.cos

/**
 * How a character is shaped and what it is willing to walk on.
 *
 * @property shape the capsule swept through the world. Its origin is the capsule's centre, so a
 *   character standing on the ground has its position half its height above the surface.
 * @property slopeLimitRadians the steepest ground still counted as standing on. Anything steeper
 *   is a wall: the character slides down it rather than walking up it.
 * @property skinWidth how far the character is held off a surface after a contact. Without it,
 *   the next sweep starts exactly touching, and a sweep that begins in contact reports a
 *   zero-distance hit forever.
 * @property maxSlideIterations how many times one move may be redirected. Each iteration is a
 *   sweep; four covers a corner (two walls) with room to spare, and the cap is what stops a
 *   wedge from spinning here indefinitely.
 * @property groundProbeDistance how far below the character to look for ground after moving.
 * @property stepHeight the tallest obstruction the character walks up instead of into. Kerbs and
 *   stairs are walls to a swept capsule, so without this it stops at every one of them. Keep it
 *   below the capsule's radius or the character climbs surfaces it should not.
 * @property stepDownDistance how far the character reaches down for ground it has walked off the
 *   edge of. This is what keeps it attached going downhill rather than airborne for a frame at a
 *   time, which reads as a stutter and interrupts anything gated on being grounded.
 * @property crouchHalfHeight the cylinder half-height to shrink to when crouching, or null for a
 *   character that cannot crouch. The capsule keeps its radius, so this is the only dimension that
 *   changes -- and it must be smaller than [shape]'s own half-height to be a crouch at all.
 * @property innerBody whether the character carries a kinematic body of its own, so sensors and
 *   queries can see it. A controller moves by sweeping shapes, which leaves Jolt with no body to
 *   report contacts about -- without this a trigger volume cannot detect the player at all.
 */
data class CharacterConfig(
    val shape: CapsuleShape,
    val slopeLimitRadians: Float = DEFAULT_SLOPE_LIMIT_RADIANS,
    val skinWidth: Float = DEFAULT_SKIN_WIDTH,
    val maxSlideIterations: Int = DEFAULT_MAX_SLIDE_ITERATIONS,
    val groundProbeDistance: Float = DEFAULT_GROUND_PROBE_DISTANCE,
    val stepHeight: Float = DEFAULT_STEP_HEIGHT,
    val stepDownDistance: Float = DEFAULT_STEP_HEIGHT,
    val crouchHalfHeight: Float? = null,
    /**
     * Whether the character carries a body of its own, so the rest of the simulation can see it.
     *
     * A controller moves by sweeping shapes, which means Jolt has no body to report contacts
     * *about* -- so without this a sensor cannot detect the player, and every trigger volume in a
     * game is deaf to the one thing it exists for. The body is kinematic and follows the character;
     * the controller's own sweeps ignore it, which is the whole reason this needed
     * `PhysicsWorld.shapeCast`'s `ignore` before it could exist.
     *
     * It is solid, so other bodies collide with it -- but **this does not give pushing for free**,
     * which is the obvious guess and is wrong. The controller sweeps and refuses to move into a
     * crate, so the kinematic body never drives through one and there is no contact to solve.
     * Shoving things is still the caller's job, through the impulse it applies on [onContact].
     *
     * Off by default: a controller in a scene with no triggers does not need the body, and one
     * body per character is not free.
     */
    val innerBody: Boolean = false,
) {
    init {
        require(skinWidth > 0f) { "skinWidth must be positive: $skinWidth" }
        require(maxSlideIterations > 0) { "maxSlideIterations must be positive: $maxSlideIterations" }
        require(groundProbeDistance > 0f) { "groundProbeDistance must be positive: $groundProbeDistance" }
        require(stepHeight >= 0f) { "stepHeight cannot be negative: $stepHeight" }
        require(stepDownDistance >= 0f) { "stepDownDistance cannot be negative: $stepDownDistance" }
        crouchHalfHeight?.let {
            require(it > 0f && it < shape.halfHeight) {
                "crouchHalfHeight must be positive and shorter than the standing half-height " +
                    "(${shape.halfHeight}): $it"
            }
        }
    }

    /** Ground is walkable when its normal is at least this upright -- compared once, not per hit. */
    internal val minWalkableNormalY: Float = cos(slopeLimitRadians)

    private companion object {
        const val DEFAULT_SLOPE_LIMIT_RADIANS = 0.7853982f // 45 degrees
        const val DEFAULT_SKIN_WIDTH = 0.02f
        const val DEFAULT_MAX_SLIDE_ITERATIONS = 4
        const val DEFAULT_GROUND_PROBE_DISTANCE = 0.1f
        const val DEFAULT_STEP_HEIGHT = 0.3f
    }
}

/**
 * Compatibility adapter for the scene-independent physics character capability.
 * Existing scene packages, constructors and properties are retained for callers.
 */
class KinematicCharacterController(
    world: PhysicsWorld,
    config: CharacterConfig,
    position: Vec3f = Vec3f(0f, 0f, 0f),
) {
    private val controller = com.awakekt.awake.physics.character.KinematicCharacterController(
        world,
        com.awakekt.awake.physics.character.CharacterConfig(
            shape = config.shape,
            slopeLimitRadians = config.slopeLimitRadians,
            skinWidth = config.skinWidth,
            maxSlideIterations = config.maxSlideIterations,
            groundProbeDistance = config.groundProbeDistance,
            stepHeight = config.stepHeight,
            stepDownDistance = config.stepDownDistance,
            crouchHalfHeight = config.crouchHalfHeight,
            innerBody = config.innerBody,
        ),
        position,
    )

    /** Current character position, shared with the underlying capability. */
    val position: Vec3f get() = controller.position

    /** The optional inner kinematic body owned by this character. */
    val body: BodyHandle? get() = controller.body

    /** Whether the most recent move found walkable ground. */
    val isGrounded: Boolean get() = controller.isGrounded

    /** Normal of the current ground contact. */
    val groundNormal: Vec3f get() = controller.groundNormal

    /** Body supporting this character, or null when airborne. */
    val groundBody: BodyHandle? get() = controller.groundBody

    /** Velocity of the body supporting this character. */
    val groundVelocity: Vec3f get() = controller.groundVelocity

    /** Whether the crouched capsule is currently in use. */
    val isCrouching: Boolean get() = controller.isCrouching

    /** Caller-owned response to valid contacts encountered while moving. */
    var onContact: ((body: BodyHandle, normal: Vec3f) -> Unit)?
        get() = controller.onContact
        set(value) { controller.onContact = value }

    /** Shrinks the capsule while keeping its feet in place. */
    fun crouch() = controller.crouch()

    /** Attempts to stand; false means headroom was blocked or the contact was invalid. */
    fun standUp(): Boolean = controller.standUp()

    /** Places the character immediately and clears its ground state. */
    fun teleport(to: Vec3f) = controller.teleport(to)

    /** Destroys the optional inner body. */
    fun dispose() = controller.dispose()

    /** Moves through physics; an invalid contact blocks this move and is logged. */
    fun move(motion: Vec3f, deltaTime: Float = DEFAULT_SYNC_DELTA) = controller.move(motion, deltaTime)

    private companion object {
        const val MIN_MOTION = 1e-5f
        const val DEFAULT_SYNC_DELTA = 1f / 60f
        const val LEAVE_GROUND_SPEED = 0.01f
    }
}
