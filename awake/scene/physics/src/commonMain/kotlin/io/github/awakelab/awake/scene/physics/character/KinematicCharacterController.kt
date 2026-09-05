/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.character

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

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
 * Moves a character through the world by sweeping and sliding, without handing it to the solver.
 *
 * This is Awake's own controller rather than a binding to Jolt's `CharacterVirtual`
 * ([D29](../../../../../../../../../docs/decisions/D29-physics-own-engine-exit-criteria.md)):
 * collide-and-slide is sweep, project, repeat, and it needs exactly one thing from a backend --
 * [PhysicsWorld.shapeCast]. Written once here, it behaves identically on all four backends and
 * is testable against a stub world, where a binding would have been three implementations with
 * three sets of quirks.
 *
 * Kinematic means the simulation never pushes this character: it goes where [move] puts it. That
 * is what makes character motion predictable, and it is why gravity is the caller's business --
 * this class integrates nothing. A caller accumulates its own vertical velocity, hands the whole
 * displacement to [move], and reads [isGrounded] to decide whether to keep accumulating.
 *
 * Kerbs and stairs are handled by stepping rather than by sliding: a swept capsule meets a riser
 * as a vertical wall, so [CharacterConfig.stepHeight] and [CharacterConfig.stepDownDistance] are
 * what separate walking up a staircase from standing at the bottom of one.
 *
 * A moving platform is carried through [groundVelocity], which a caller adds to the displacement
 * it asks for. Crouching is the remaining gap.
 */
@Suppress("TooManyFunctions") // Move, crouch, stand, teleport: the surface a character needs.
class KinematicCharacterController(
    private val world: PhysicsWorld,
    private val config: CharacterConfig,
    position: Vec3f = Vec3f(0f, 0f, 0f),
) {
    /** Where the character is now. Mutated in place by [move] rather than replaced. */
    val position: Vec3f = Vec3f(position.x, position.y, position.z)

    /**
     * The character's own body, when [CharacterConfig.innerBody] asked for one.
     *
     * Kinematic, so it pushes and is not pushed, and excluded from every sweep this controller
     * makes -- a body at the character's own position is nearer than anything else, so a sweep
     * that could see it would stop dead on the first frame.
     *
     * **Whoever builds this controller owns destroying it**, through [dispose]. Nothing else will:
     * the body outlives the controller in the `PhysicsWorld`, invisible and unreachable.
     */
    var body: BodyHandle? = if (config.innerBody) {
        world.createBody(
            config.shape,
            this.position,
            Quat.IDENTITY,
            MotionType.KINEMATIC,
        )
    } else {
        null
    }
        private set

    /** Whether the last [move] ended on ground flat enough to stand on. */
    var isGrounded: Boolean = false
        private set

    /** The surface [isGrounded] refers to; meaningless when not grounded. */
    val groundNormal: Vec3f = Vec3f(0f, 1f, 0f)

    /**
     * The body underfoot, or `null`. A caller reads its velocity through
     * [PhysicsWorld.getLinearVelocity] to ride a moving platform.
     */
    var groundBody: BodyHandle? = null
        private set

    /**
     * How fast the ground underfoot is moving, in world units per second. Zero when airborne or
     * standing on something static.
     *
     * Reported rather than applied, for the same reason gravity is: [move] only ever removes
     * motion from what it was handed, and a controller that quietly added some would break the
     * one guarantee that makes it predictable. A caller carries a character along by adding this
     * to the displacement it asks for -- which is also where it decides whether a lift should drag
     * someone who is walking against it.
     *
     * ponytail: `getLinearVelocity` allocates a vector per call, so this costs one per grounded
     * character per step. Removing it needs an in-place read on four backends, which is worth
     * doing when a profile says so and not before.
     */
    val groundVelocity: Vec3f = Vec3f(0f, 0f, 0f)

    /**
     * Called for each body the character is stopped by during a [move], with the contact normal.
     *
     * Reported rather than acted on. Whether walking into a crate should shove it, how hard, and
     * whether that crate is even the kind of thing that moves are all game rules, and a kinematic
     * controller that guessed at them would be applying impulses to terrain. The caller knows
     * which bodies are dynamic; this only knows what it hit.
     *
     * The normal points out of the surface, back toward the character, so a caller pushing a body
     * away from itself pushes along its negation.
     */
    var onContact: ((body: BodyHandle, normal: Vec3f) -> Unit)? = null

    /** Whether the character is currently the shorter shape. */
    var isCrouching: Boolean = false
        private set

    /** The capsule being swept right now -- the standing one, or the crouched one. */
    private var activeShape: CapsuleShape = config.shape

    /** The crouched capsule, built once: same radius, shorter cylinder. */
    private val crouchedShape: CapsuleShape? = config.crouchHalfHeight?.let {
        CapsuleShape(halfHeight = it, radius = config.shape.radius)
    }

    /**
     * How much lower the crouched capsule's centre sits, with its feet in the same place.
     *
     * A capsule's origin is its centre, so shrinking it without moving it leaves the character
     * hovering by half the difference. Feet stay planted instead, which is what a crouch looks
     * like.
     */
    private val crouchDrop: Float =
        config.crouchHalfHeight?.let { config.shape.halfHeight - it } ?: 0f

    // Scratch, reused every call: move() runs per character per fixed step forever, and these
    // would otherwise be garbage at exactly the rate the game is played.
    private val remaining = Vec3f()
    private val target = Vec3f()
    private val scratch = Vec3f()

    // Where a step-up probe started, so a failed one can be undone rather than half-applied.
    private val savedPosition = Vec3f()
    private val savedRemaining = Vec3f()

    /** The horizontal heading of a step probe, so its extra reach can be taken back after. */
    private val stepDirection = Vec3f()

    /**
     * Shrinks to the crouched capsule, keeping the feet where they are.
     *
     * Always allowed: there is no such thing as being too cramped to crouch. Does nothing if the
     * character is already crouching, or if [CharacterConfig.crouchHalfHeight] was never set.
     */
    fun crouch() {
        val crouched = crouchedShape ?: return
        if (isCrouching) return
        activeShape = crouched
        position.y -= crouchDrop
        isCrouching = true
    }

    /**
     * Stands back up, if there is room, and reports whether it happened.
     *
     * The headroom test is a sweep of the crouched capsule straight up by twice the height it
     * would gain: that puts the swept shape's top exactly where the standing capsule's top would
     * be, so anything in the way is something the character's head would occupy. A caller that
     * ignores the answer and stands anyway puts the capsule inside the ceiling, which is the one
     * state a swept character cannot recover from.
     *
     * Holding crouch is therefore the caller's job: try this every frame while the key is up, and
     * the character stands the moment it walks out from under something.
     */
    fun standUp(): Boolean {
        if (!isCrouching) return true
        target.set(position)
        target.y += crouchDrop * 2f
        val blocked = world.shapeCast(activeShape, position, target, ignore = body) != null
        if (!blocked) {
            activeShape = config.shape
            position.y += crouchDrop
            isCrouching = false
        }
        return !blocked
    }

    /** Places the character without sweeping. For spawning, not for movement. */
    fun teleport(to: Vec3f) {
        position.set(to)
        isGrounded = false
        groundBody = null
        groundVelocity.set(0f, 0f, 0f)
        // Rebuilt rather than driven there. `moveKinematic` derives a velocity from the distance
        // and the step, so carrying a body across a respawn would send it through the level at
        // whatever speed that implies, shoving everything on the way.
        body?.let { existing ->
            world.destroyBody(existing)
            body = world.createBody(config.shape, position, Quat.IDENTITY, MotionType.KINEMATIC)
        }
    }

    /**
     * Destroys the character's own body, if it has one.
     *
     * Nothing else does. A controller dropped without this leaves a kinematic body standing where
     * the character was, still solid and still tripping every trigger it overlaps.
     */
    fun dispose() {
        body?.let(world::destroyBody)
    }

    /**
     * Carries the inner body along to where the character now is.
     *
     * `moveKinematic` rather than a position write, so Jolt derives the velocity needed to arrive
     * and solves contacts with the body genuinely moving -- which is what lets it push a crate
     * instead of teleporting through it. A zero delta is a teleport, and is what [teleport] wants.
     */
    private fun syncBody(deltaTime: Float) {
        val handle = body ?: return
        world.moveKinematic(handle, position, Quat.IDENTITY, deltaTime)
    }

    /**
     * Sweeps [motion] and slides along whatever stops it, then refreshes the ground state.
     *
     * Each iteration sweeps what is left of the motion, advances to the first contact, and turns
     * the rest of the motion along the surface instead of into it -- so a character walking into
     * a wall at an angle keeps moving along the wall rather than stopping dead. Motion is only
     * ever removed, never added, so sliding cannot accelerate the character.
     */
    fun move(motion: Vec3f, deltaTime: Float = DEFAULT_SYNC_DELTA) {
        val wasGrounded = isGrounded
        remaining.set(motion)
        var iterations = 0
        var blocked = true
        while (blocked && iterations < config.maxSlideIterations && remaining.length3() > MIN_MOTION) {
            target.set(position).add(remaining)

            val hit = world.shapeCast(activeShape, position, target, ignore = body)
            if (hit == null) {
                position.set(target)
                blocked = false
            } else {
                // Try to walk over it before deciding to slide along it. Only from the ground:
                // stepping in mid-air is climbing, and only over walls, since a walkable slope is
                // something to walk up rather than step onto.
                val stepped = wasGrounded &&
                    hit.normal.y < config.minWalkableNormalY &&
                    tryStepUp()
                if (!stepped) {
                    slideAlong(hit.normal, hit.fraction)
                    onContact?.invoke(hit.handle, hit.normal)
                }
                iterations++
            }
        }
        updateGroundState(ascending = motion.y > MIN_MOTION)
        if (wasGrounded && !isGrounded) snapDownToGround()
        syncBody(deltaTime)
    }

    /**
     * Attempts the up-forward-down probe that turns a kerb into a step.
     *
     * A swept capsule meets a stair riser as a vertical wall and slides along it, which is why a
     * character without this catches on every kerb in the world. The probe lifts by at most
     * [CharacterConfig.stepHeight], moves what is left of the motion at that height, and looks
     * back down for something walkable to land on.
     *
     * Returns `false` and leaves [position] and the remaining motion untouched if any part of
     * that fails, so the caller can fall back to sliding. Restoring rather than half-applying is
     * the point: a partial step leaves the character floating at riser height.
     */
    private fun tryStepUp(): Boolean {
        if (config.stepHeight <= 0f) return false
        savedPosition.set(position)
        savedRemaining.set(remaining)

        val rise = sweepBy(0f, config.stepHeight, 0f)
        val stepped = rise > MIN_MOTION && stepForwardAndDown()
        if (!stepped) {
            position.set(savedPosition)
            remaining.set(savedRemaining)
        }
        return stepped
    }

    /** The horizontal half of [tryStepUp], then the search for a surface to land on. */
    private fun stepForwardAndDown(): Boolean {
        // Horizontal only: the vertical part of the motion is what the rise just did, and
        // carrying it along would drive the character back into the step it is clearing.
        scratch.set(remaining.x, 0f, remaining.z)
        val requested = scratch.length3()
        if (requested <= MIN_MOTION) return false
        return probeStepLanding(requested)
    }

    /** The forward-and-down half of a step, once there is a horizontal distance worth probing. */
    private fun probeStepLanding(requested: Float): Boolean {
        stepDirection.set(scratch).normalize()

        // Probe further than asked when the request is small. A capsule that has only just passed
        // a step's edge is resting on its own rounded shoulder, and the downward sweep there
        // reports the edge's diagonal normal rather than the step's top face -- so the landing is
        // rejected and a character walking slowly stalls against a step it can plainly climb. A
        // radius forward is where the underside is flat enough to land on.
        val probeDistance = max(requested, activeShape.radius)
        val advanced = sweepBy(
            stepDirection.x * probeDistance,
            0f,
            stepDirection.z * probeDistance,
        )
        if (advanced <= MIN_MOTION || !landOnStep()) return false

        // The extra was for looking, not for moving: give it back, or a slow walk turns into a
        // lurch every time it meets a step.
        val overshoot = advanced - requested
        if (overshoot > 0f) {
            position.x -= stepDirection.x * overshoot
            position.z -= stepDirection.z * overshoot
        }
        return true
    }

    /**
     * Looks below a raised character for something walkable and settles onto it.
     *
     * The reach is the climb plus the step-down distance, so landing lower than the rise counts:
     * that is the far side of a stair, not a failed step.
     */
    private fun landOnStep(): Boolean {
        val reach = config.stepHeight + config.stepDownDistance
        target.set(position)
        target.y -= reach
        val landing = world.shapeCast(activeShape, position, target, ignore = body)
        if (landing == null || landing.normal.y < config.minWalkableNormalY) return false

        position.y -= reach * landing.fraction
        position.y += config.skinWidth
        remaining.set(0f, 0f, 0f)
        return true
    }

    /**
     * Follows ground the character has just walked off the edge of.
     *
     * Only when it was grounded before the move and is not after: without this, every downhill
     * step is a frame of falling, which stutters the camera and breaks anything that asks whether
     * the character is on the ground. A character that jumped was not grounded to begin with, so
     * this cannot pull it back down.
     */
    private fun snapDownToGround() {
        if (config.stepDownDistance <= 0f) return
        target.set(position)
        target.y -= config.stepDownDistance
        val hit = world.shapeCast(activeShape, position, target, ignore = body)
        if (hit != null && hit.normal.y >= config.minWalkableNormalY) {
            position.y -= config.stepDownDistance * hit.fraction
            position.y += config.skinWidth
            isGrounded = true
            groundNormal.set(hit.normal)
            groundBody = hit.handle
            val velocity = world.getLinearVelocity(hit.handle)
            groundVelocity.set(velocity.x, velocity.y, velocity.z)
        }
    }

    /**
     * Sweeps [position] by the given offset, stopping at the first contact, and returns how far it
     * actually got.
     */
    private fun sweepBy(x: Float, y: Float, z: Float): Float {
        target.set(position.x + x, position.y + y, position.z + z)
        val hit = world.shapeCast(activeShape, position, target, ignore = body)
        val distance = sqrt(x * x + y * y + z * z)
        if (hit == null) {
            position.set(target)
            return distance
        }
        val travelled = (distance * hit.fraction - config.skinWidth).coerceAtLeast(0f)
        if (travelled > 0f && distance > 0f) {
            val scale = travelled / distance
            position.set(position.x + x * scale, position.y + y * scale, position.z + z * scale)
        }
        return travelled
    }

    /** Advances to a contact at [fraction] and turns what is left of the motion along [normal]. */
    private fun slideAlong(normal: Vec3f, fraction: Float) {
        // Advance to the contact, then hold off the surface by the skin width: a sweep that
        // starts exactly touching reports a zero-distance hit and the character never moves
        // again.
        scratch.set(remaining).scale(fraction)
        position.add(scratch)
        scratch.set(normal).scale(config.skinWidth)
        position.add(scratch)

        remaining.scale(1f - fraction)
        // Project what is left onto the contact plane. Only the component going INTO the surface
        // is removed; a normal the motion is already moving away from must not add anything back.
        val into = remaining.dot(normal)
        if (into < 0f) {
            scratch.set(normal).scale(into)
            remaining.sub(scratch)
        }
    }

    /**
     * Looks straight down for something to stand on.
     *
     * A separate probe rather than a flag set during [move], because standing still on a slope is
     * as much a ground state as walking into it, and a character that only learns about the floor
     * by colliding with it does not know it is grounded until it falls.
     *
     * Finding ground also settles onto it, unless [ascending]. The probe reaches further than a
     * character falls in one step, so it reports ground from up to its own length above the
     * surface -- and since being grounded is exactly what stops a caller accumulating gravity, a
     * character that is never pulled the rest of the way down hovers there for good. Ascending is
     * the exception: a jump is still within the probe's reach for its first frames, and settling
     * onto ground it has not left yet would cancel it.
     */
    private fun updateGroundState(ascending: Boolean) {
        target.set(position)
        target.y -= config.groundProbeDistance
        val hit = world.shapeCast(activeShape, position, target, ignore = body)
        val walkable = hit != null && hit.normal.y >= config.minWalkableNormalY
        isGrounded = walkable
        if (hit != null && walkable) {
            groundNormal.set(hit.normal)
            groundBody = hit.handle
            val velocity = world.getLinearVelocity(hit.handle)
            groundVelocity.set(velocity.x, velocity.y, velocity.z)
            if (!ascending) {
                position.y -= config.groundProbeDistance * hit.fraction
                position.y += config.skinWidth
            }
        } else {
            groundNormal.set(0f, 1f, 0f)
            groundBody = null
            groundVelocity.set(0f, 0f, 0f)
        }
    }

    private companion object {
        /** Below this a move is not worth a sweep; it is also what stops the loop on a wedge. */
        const val MIN_MOTION = 1e-5f

        /**
         * The step [move] assumes when it is not told one.
         *
         * Only the inner body reads it -- a controller without one ignores `deltaTime` entirely,
         * which is why this has a default rather than forcing every existing caller to pass a
         * value it would not use. A caller on a fixed step should pass its own.
         */
        const val DEFAULT_SYNC_DELTA = 1f / 60f
    }
}
