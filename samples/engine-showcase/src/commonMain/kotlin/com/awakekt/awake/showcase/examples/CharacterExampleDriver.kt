/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.character.CharacterConfig
import com.awakekt.awake.scene.physics.character.KinematicCharacterController
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlin.math.sin
import kotlin.math.sqrt

private const val WALK_SPEED = 4f
private const val GRAVITY = -9.81f

/**
 * Upward speed a jump starts with. `v = sqrt(2 * g * h)`, so this clears a little over a metre --
 * high enough to feel like a jump, low enough to still land on the terrain it started from.
 */
private const val JUMP_SPEED = 5f

/** How far behind the character the third-person camera sits before anything blocks it. */
private const val CAMERA_DISTANCE = 6f

/**
 * How far above the character's own origin the camera looks.
 *
 * The origin is the capsule's centre, so this puts the pivot around the head rather than the
 * chest. It is an offset from the target, not a world point -- see [CharacterExampleDriver
 * .followWithCamera].
 */
private const val CAMERA_TARGET_HEIGHT = 0.5f

/** The camera's own collision radius, so it stops short of a wall rather than touching it. */
private const val CAMERA_RADIUS = 0.35f

/**
 * How close the camera may ever be pulled to the character.
 *
 * Without a floor on it, anything touching the pivot collapses the eye onto the character and the
 * near plane clips the entire scene -- the camera appears to hide the render rather than to move.
 * The boxes in this showcase land around the character's own spawn, so the sweep genuinely does
 * start in contact, and a collapse is what that produced.
 */
private const val MIN_CAMERA_DISTANCE = 1.5f

/**
 * How hard walking into a loose body shoves it, in newton-seconds.
 *
 * An impulse rather than a velocity, so the same shove moves a crate further than a boulder --
 * which is the difference between a character with weight and one that teleports things.
 */
private const val PUSH_IMPULSE = 2.5f

/**
 * Below this the character has left the world and is not coming back.
 *
 * Walking off the edge of a 12-metre terrain is easy and falling forever afterwards makes the
 * demonstration look dead rather than finished, so it respawns instead. The terrain's lowest
 * point is around zero, so anything well under it is unambiguous.
 */
private const val FALL_LIMIT = -25f

/** How far the platform travels either side of where the scene authored it. */
private const val PLATFORM_TRAVEL = 4f

/** How fast it makes that trip, in world units per second. */
private const val PLATFORM_SPEED = 1.2f

/**
 * How fast the character swims up or down, in world units per second.
 *
 * Slower than the walk, because water resists -- and slower than the jump, because a swimmer rises
 * steadily rather than leaping.
 */
private const val SWIM_SPEED = 2f

/**
 * How much of gravity still applies while swimming.
 *
 * Not zero: a swimmer that hangs motionless in the water reads as a bug, and a slow sink is what
 * makes holding Space feel like swimming rather than like flying. `applyBuoyancy` cannot do this
 * job -- the character's body is kinematic, and buoyancy only moves dynamic ones.
 */
private const val SWIM_GRAVITY_SCALE = 0.15f

/** How much slower the character moves horizontally in water. */
private const val SWIM_DRAG = 0.55f

/** Below this the camera is looking straight down and has no usable horizontal heading. */
private const val MIN_DIRECTION_LENGTH = 1e-4f

/**
 * Walks a capsule around the terrain under WASD, colliding with it.
 *
 * This is what `KinematicCharacterController` is for, and the first thing in the engine to move
 * by sweeping rather than by assignment. It lives in the sample rather than in `:awake:scene`
 * deliberately: there is one consumer, and the framework boundary admits a capability on two
 * consumers or on a limitation a consumer cannot work around -- neither applies yet. When a
 * second game wants a walking character, this is the shape to lift.
 *
 * `MatrixRelativeMovementSystem` is not replaced by any of this and should not be. It writes
 * `Transform.position` directly, which is exactly right for a spectator camera, an editor
 * viewport, or any scene with no `PhysicsWorld` installed. The camera-relative basis below is
 * the same idea as its own and is duplicated rather than extracted, for the same
 * one-consumer reason -- extracting it is the right move the moment something else needs it.
 */
internal object CharacterExampleDriver {
    private val config = CharacterConfig(
        // Matches the player node's authored scale: 0.6 across, 2.4 tall.
        shape = CapsuleShape(halfHeight = 0.9f, radius = 0.3f),
        stepHeight = 0.3f,
        // Held on Ctrl: 1.2 tall crouched, half the standing height.
        crouchHalfHeight = 0.3f,
        // So the goal zone can see the player at all. A controller moves by sweeping shapes, which
        // leaves Jolt with no body to report contacts about -- every trigger in the showcase was
        // deaf to the one thing it exists for until this.
        innerBody = true,
    )

    private var controller: KinematicCharacterController? = null

    /** The character's own body, so a trigger can tell the player from a crate. */
    val characterBody: BodyHandle? get() = controller?.body
    private var entity: Entity? = null
    private var verticalVelocity = 0f

    /** Where the scene authored the character, so falling out of the world can be undone. */
    private val spawn = Vec3f(0f, 0f, 0f)

    /** The lift, and where the scene put it, so its travel is measured from there. */
    private var platform: Entity? = null
    private val platformOrigin = Vec3f(0f, 0f, 0f)
    private var platformTime = 0f

    // Scratch: this runs every fixed step, and a per-step basis would be garbage at exactly the
    // rate the game is played.
    private val forward = Vec3f(0f, 0f, -1f)
    private val right = Vec3f(1f, 0f, 0f)

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val physicsWorld = ShowcasePhysics.world
        val node = instance.roots.find { it.name == "player" }
        val transform = node?.let { runtime.world.get<Transform>(it.entity) }
        if (physicsWorld == null || node == null || transform == null) return

        runtime.world.add(node.entity, MovementControl())
        entity = node.entity
        verticalVelocity = 0f
        spawn.set(transform.position)
        controller = KinematicCharacterController(physicsWorld, config, transform.position).apply {
            onContact = { body, normal -> pushIfDynamic(runtime.world, physicsWorld, body, normal) }
        }
        followWithCamera(runtime.world, node.entity)
        attachPlatform(instance, runtime)
    }

    /**
     * Makes the authored platform node a kinematic body that slides back and forth.
     *
     * Kinematic rather than dynamic: a lift pushes the world around without being pushed back, and
     * `moveKinematic` derives the velocity to arrive so whatever is standing on it is carried
     * rather than left behind.
     */
    private fun attachPlatform(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val node = instance.roots.find { it.name == "platform" } ?: return
        val transform = runtime.world.get<Transform>(node.entity) ?: return
        platform = node.entity
        platformOrigin.set(transform.position)
        platformTime = 0f
        runtime.world.add(
            node.entity,
            PhysicsBody(
                // Half the authored scale: the node is 3 x 0.3 x 3 units across.
                shape = BoxShape(Vec3f(1.5f, 0.15f, 1.5f)),
                motionType = MotionType.KINEMATIC,
            ),
        )
    }

    /**
     * Points the showcase's orbit rig at the character.
     *
     * `EngineShowcaseLoader` gives every showcase a rig around the authored viewpoint, which is
     * right for looking at a static demonstration and wrong for one that walks away. Setting the
     * target is all it takes -- `ThirdPerson` already orbits an entity when it has one.
     */
    internal fun followWithCamera(world: World, player: Entity) {
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
            rig.targetEntity = player
            // `offsetPosition` changes meaning the moment a target is set: CameraSystem orbits
            // `target.position + offsetPosition`, so the world point the showcase loader put there
            // for an untargeted rig becomes an offset from the character and throws the pivot
            // metres away from them. It has to be re-stated as a local offset here.
            // A fresh vector rather than mutating whatever is there: the rig's may be shared with
            // the lens, and writing through it would feed the pivot back into itself.
            rig.offsetPosition = Vec3f(0f, CAMERA_TARGET_HEIGHT, 0f)
            rig.distance = CAMERA_DISTANCE
            rig.needsReset = false
        }
    }

    /**
     * Shoves a body the character walked into, if it is the kind of body that moves.
     *
     * The controller reports every contact and decides nothing, which is right: it cannot tell
     * terrain from a crate, and an impulse applied to the heightfield is at best ignored. The
     * scene can tell -- a `PhysicsBody` carries its own motion type -- so the rule lives here.
     *
     * The normal points back at the character, so the shove is along its negation.
     */
    private fun pushIfDynamic(
        world: World,
        physicsWorld: PhysicsWorld,
        body: BodyHandle,
        normal: Vec3f,
    ) {
        var dynamic = false
        world.queryEach(PhysicsBody::class) { _, physicsBody ->
            if (physicsBody.handle == body && physicsBody.motionType == MotionType.DYNAMIC) {
                dynamic = true
            }
        }
        if (!dynamic) return

        // Horizontal only: a character walking into a crate should not press it into the floor,
        // and standing on one should not fire it downward.
        physicsWorld.addImpulse(body, Vec3f(-normal.x * PUSH_IMPULSE, 0f, -normal.z * PUSH_IMPULSE))
    }

    /**
     * Drops the controller before the scene closes.
     *
     * The entity belongs to the scene and goes with it; what would survive is this object's
     * reference to a controller pointing at a world the next showcase does not use.
     */
    fun detach(world: World) {
        platform = null
        // Nothing else destroys the character's own body, and one left behind stands where the
        // player was, still solid and still tripping whatever it overlaps.
        controller?.dispose()
        // Clearing the target both drops a reference to an entity that is about to be destroyed
        // and takes this camera back out of scope for [cameraCollisionSystem].
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
            if (rig.targetEntity == entity) rig.targetEntity = null
        }
        controller = null
        entity = null
    }

    /**
     * Runs on the fixed step, alongside physics rather than with the frame.
     *
     * A character that integrates gravity on a variable delta falls at a different speed on a
     * 144Hz machine than on a 30Hz one, which is the whole argument for the fixed timestep and
     * would be undone by registering this with `frameSystem`.
     */
    fun system(input: () -> GameplayInput): System = object : System {
        override fun update(world: World, delta: Float) {
            val character = controller
            val player = entity
            val control = player?.let { world.get<MovementControl>(it) }
            val transform = player?.let { world.get<Transform>(it) }
            if (character == null || control == null || transform == null) return

            updateCameraBasis(world)
            // Gravity is the controller's caller's job, by design: it integrates nothing.
            // Tried every step rather than toggled: standing can be refused, so a character that
            // crouched under something stands the moment it walks back into the open.
            if (input().isDown(Key.Ctrl)) character.crouch() else character.standUp()

            // The player's own body is what the pool's sensor detects, which is the whole reason
            // the controller carries one. Without it the water is scenery the character wades
            // through without noticing.
            val swimming = TerrainPhysicsExampleDriver.isSubmerged(character.body)
            val jumping = character.isGrounded && input().isDown(Key.Space)
            verticalVelocity = when {
                swimming -> SwimMotion.velocity(
                    up = input().isDown(Key.Space),
                    down = input().isDown(Key.Ctrl),
                )
                jumping -> JUMP_SPEED
                character.isGrounded -> 0f
                else -> verticalVelocity + GRAVITY * delta
            }
            val speed = if (swimming) WALK_SPEED * SWIM_DRAG else WALK_SPEED

            drivePlatform(world, delta)

            if (character.position.y < FALL_LIMIT) {
                character.teleport(spawn)
                verticalVelocity = 0f
            }

            // The ground's own motion is added to what the player asked for, rather than applied
            // by the controller: standing still on a lift still travels, and walking against it
            // still works, because both are just displacement.
            val ground = character.groundVelocity
            character.move(
                Vec3f(
                    (right.x * control.moveX + forward.x * control.moveZ) * speed * delta +
                        ground.x * delta,
                    verticalVelocity * delta + ground.y * delta,
                    (right.z * control.moveX + forward.z * control.moveZ) * speed * delta +
                        ground.z * delta,
                ),
                deltaTime = delta,
            )
            transform.position.set(character.position)
        }
    }

    /**
     * Pulls the camera in when the world gets between it and the character.
     *
     * A frame system, and deliberately after `cameraSystem`: the rig recomputes the eye from yaw,
     * pitch and distance every frame, so this corrects the result rather than the inputs, and the
     * rig keeps the distance the player chose for when the view opens up again.
     *
     * This is the third-person camera use for [com.awakekt.awake.physics.PhysicsWorld
     * .shapeCast] that the plan named -- there is no other correct way to do it. A ray would slip
     * through the gap beside a pillar and leave the near plane inside it.
     */
    fun cameraCollisionSystem(): System = object : System {
        override fun update(world: World, delta: Float) {
            val physicsWorld = ShowcasePhysics.world ?: return
            // Only cameras that follow something. This system is installed for every showcase and
            // the physics world outlives a switch away from the terrain, so keying off a rig with
            // a target is what keeps it from quietly pulling in the free orbit camera of a
            // demonstration that never asked for a third-person view.
            world.queryEach(Camera::class, ActiveCamera::class) { entity, camera, _ ->
                if (world.get<CameraRig>(entity)?.targetEntity == null) return@queryEach
                val lens = camera.lens
                // Static world only. Before layers, the camera collided with the crates rolling
                // around the character's feet and was shoved about by them; a camera should be
                // blocked by the level, not by the props in it.
                val hit = physicsWorld.shapeCast(
                    SphereShape(CAMERA_RADIUS),
                    lens.center,
                    lens.eye,
                    onlyLayer = CollisionLayers.World,
                ) ?: return@queryEach

                val toEyeX = lens.eye.x - lens.center.x
                val toEyeY = lens.eye.y - lens.center.y
                val toEyeZ = lens.eye.z - lens.center.z
                val distance = sqrt(toEyeX * toEyeX + toEyeY * toEyeY + toEyeZ * toEyeZ)
                if (distance <= MIN_CAMERA_DISTANCE) return@queryEach

                // Never closer than the minimum, whatever the sweep says. A sweep that starts in
                // contact reports a fraction of zero, and honouring that literally puts the eye
                // inside the character's own head.
                val blocked = distance * hit.fraction
                val allowed = blocked.coerceAtLeast(MIN_CAMERA_DISTANCE)
                val scale = allowed / distance
                lens.eye.set(
                    lens.center.x + toEyeX * scale,
                    lens.center.y + toEyeY * scale,
                    lens.center.z + toEyeZ * scale,
                )
            }
        }
    }

    /**
     * Slides the platform along z, as a pose rather than a position.
     *
     * `moveKinematic` gives Jolt the target and lets it derive the velocity needed to arrive, which
     * is what makes the body report a velocity for a rider to read. Writing the transform directly
     * would teleport it: the platform would arrive, and whatever stood on it would not.
     */
    private fun drivePlatform(world: World, delta: Float) {
        val physicsWorld = ShowcasePhysics.world
        val handle = platform?.let { world.get<PhysicsBody>(it) }?.handle
        if (physicsWorld == null || handle == null) return

        platformTime += delta
        val offset = sin(platformTime * PLATFORM_SPEED) * PLATFORM_TRAVEL
        physicsWorld.moveKinematic(
            handle,
            Vec3f(platformOrigin.x, platformOrigin.y, platformOrigin.z + offset),
            Quat.IDENTITY,
            delta,
        )
    }

    /** W is whichever way the camera is looking, flattened onto the ground plane. */
    private fun updateCameraBasis(world: World) {
        forward.set(0f, 0f, -1f)
        right.set(1f, 0f, 0f)
        world.queryEach(Camera::class, ActiveCamera::class) { _, camera, _ ->
            val lens = camera.lens
            val dirX = lens.center.x - lens.eye.x
            val dirZ = lens.center.z - lens.eye.z
            val length = sqrt(dirX * dirX + dirZ * dirZ)
            if (length > MIN_DIRECTION_LENGTH) {
                forward.set(dirX / length, 0f, dirZ / length)
                // right = forward x up, which for up = (0, 1, 0) reduces to (-z, 0, x) and is
                // already unit length because forward is.
                right.set(-forward.z, 0f, forward.x)
            }
        }
    }
}

/**
 * How fast a swimmer moves vertically for a given pair of held keys.
 *
 * Separated from the driver because it is the part with a decision in it, and the driver's own
 * `update` needs a loaded scene and an input device to reach. The three cases are each a way of
 * getting swimming wrong: no drift is a swimmer hanging motionless, full gravity is a swimmer
 * falling, and a drift stronger than the swim speed is water that cannot be climbed out of.
 */
internal object SwimMotion {
    /**
     * A velocity rather than an acceleration, unlike the fall it replaces. Water is thick enough
     * that a swimmer reaches their speed at once and stops when they stop pushing; integrating
     * would give the character momentum underwater that it should not have.
     */
    fun velocity(up: Boolean, down: Boolean): Float = when {
        // Up wins a tie rather than cancelling: Ctrl is crouch on land and dive in water, so both
        // held at once is reachable by accident, and cancelling to zero is the motionless hang.
        up -> SWIM_SPEED
        down -> -SWIM_SPEED
        else -> GRAVITY * SWIM_GRAVITY_SCALE
    }
}
