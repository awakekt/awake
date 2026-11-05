/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.registerPhysics
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneFreeLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CharacterControllerTest {
    private val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()

    @Test
    fun theCharacterFallsOntoTheFloorAndStands() = runTest {
        val arena = arena(jumpSpeed = 5f)

        arena.run(STEPS)

        // Floor top at 0, capsule centre half its height (0.5 + 0.5) above it.
        assertEquals(1f, arena.position.y, GROUND_TOLERANCE, "the character must stand on the floor")
        assertTrue(arena.character.isGrounded)
    }

    @Test
    fun walkingForwardStopsAtTheWall() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)

        arena.intent.moveZ = 1f
        arena.run(STEPS * 2)

        // Wall face at z = -2.5; the capsule stops a radius short of it.
        assertTrue(arena.position.z < -1f, "forward (-Z) must walk the character; z = ${arena.position.z}")
        assertTrue(arena.position.z > -2.5f + 0.4f, "the wall must stop the character; z = ${arena.position.z}")
    }

    @Test
    fun anAgentWalksInWorldSpaceAndTheWallStopsIt() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent

        // With no camera a player's forward is -Z, so an agent's world +Z is the other way.
        arena.intent.moveZ = 1f
        arena.run(STEPS / 4)
        assertTrue(arena.position.z > 0.5f, "an agent's +Z is world +Z; z = ${arena.position.z}")

        arena.intent.moveZ = -1f
        arena.run(STEPS * 3)
        assertTrue(arena.position.z > -2.5f + 0.4f, "the wall must stop the agent; z = ${arena.position.z}")
    }

    /** The positive control: the same agent moved without physics walks through the wall. */
    @Test
    fun anAgentMovedWithoutTheControllerPassesThroughTheWall() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        arena.intent.moveZ = -1f

        repeat(STEPS * 3) { MatrixRelativeMovementSystem().update(arena.world, DELTA) }

        assertTrue(arena.position.z < -3.5f, "without the controller nothing stops it; z = ${arena.position.z}")
    }

    @Test
    fun teleportingPutsTheCharacterBehindTheWallThatWalkingStopsAt() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)

        // The wall's far face is at z = -3.5; walking there stops at its near face (see above).
        arena.character.teleport(arena.world.get<Transform>(arena.player)!!, Vec3f(0f, 1f, -5f))
        assertEquals(-5f, arena.position.z, "the scene shows the character there at once")
        arena.run(STEPS)

        assertEquals(-5f, arena.position.z, GROUND_TOLERANCE, "it stays behind the wall, not swept back")
        assertEquals(1f, arena.position.y, GROUND_TOLERANCE, "and stands on the floor there")
    }

    @Test
    fun jumpingRisesAndLandsBackOnTheFloor() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        val ground = arena.position.y

        assertTrue(arena.ground.grounded, "standing must be on the ground")

        arena.intent.jump = true
        arena.run(1)
        arena.intent.jump = false
        assertFalse(arena.ground.grounded, "taking off must leave the ground")
        var peak = ground
        var airborneSteps = 0
        repeat(STEPS) {
            arena.run(1)
            peak = maxOf(peak, arena.position.y)
            if (!arena.ground.grounded) airborneSteps++
        }

        assertTrue(peak > ground + 0.8f, "a 5 m/s jump must rise about 1.3 m; peak ${peak - ground}")
        assertEquals(ground, arena.position.y, GROUND_TOLERANCE, "it must land back on the floor")
        assertTrue(arena.ground.grounded, "landing must be back on the ground")
        assertTrue(airborneSteps > STEPS / 4, "the whole flight is off the ground, even its top: $airborneSteps steps")
    }

    @Test
    fun aCharacterWithoutJumpSpeedStaysDown() = runTest {
        val arena = arena(jumpSpeed = 0f)
        arena.run(STEPS)
        val ground = arena.position.y

        arena.intent.jump = true
        arena.run(STEPS / 2)

        assertEquals(ground, arena.position.y, GROUND_TOLERANCE)
    }

    @Test
    fun theCharacterRoundTrips() {
        val world = World()
        SceneLoader.decode(scene(jumpSpeed = 5f)).instantiate(world = world, componentRegistry = registry)

        val saved = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneCharacterController>().single()

        assertEquals(SceneCharacterController(jumpSpeed = 5f), saved)
    }

    /** At more frames than steps, a walking character still moves on every frame, and evenly. */
    @Test
    fun aWalkingCharacterMovesOnEveryFrameWhenFramesOutrunSteps() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        arena.intent.moveX = 1f

        val shown = arena.frames(FRAMES, frameSeconds = 1f / 120f).map { it.x }

        val moves = shown.zipWithNext { a, b -> b - a }.drop(FRAMES / 4)
        assertTrue(moves.all { it > 0f }, "a frame without a step must still move the character: ${moves.take(8)}")
        assertTrue(moves.max() / moves.min() < EVEN_FRAMES, "each frame must move it about as far: ${moves.take(8)}")
    }

    /** A respawn puts the character there at once; it never slides across from where it was. */
    @Test
    fun aTeleportShowsAtOnceInsteadOfBlending() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        arena.intent.moveX = 1f
        arena.frames(FRAMES / 2, frameSeconds = 1f / 120f)

        val target = Vec3f(4f, 1f, 4f)
        arena.character.teleport(arena.world.get<Transform>(arena.player)!!, target)
        (arena.characters as? InterpolatedSystem)?.interpolate(arena.world, 0.5f)

        assertEquals(target.x, arena.position.x, GROUND_TOLERANCE)
        assertEquals(target.z, arena.position.z, GROUND_TOLERANCE)
    }

    /** The body a character carries for sensors to see is destroyed with the character, not left standing. */
    @Test
    fun aDestroyedCharactersOwnBodyGoesWithIt() = runTest {
        val arena = arena(jumpSpeed = 0f).withOwnBody()
        arena.run(1)
        val body = assertNotNull(arena.character.controller?.body, "the character carries a body of its own")
        assertTrue(arena.simulates(body))

        arena.world.destroy(arena.player)
        arena.run(1)

        assertFalse(arena.simulates(body), "the body outlived its character")
    }

    @Test
    fun aReplacedCharacterLeavesNoBodyBehind() = runTest {
        val arena = arena(jumpSpeed = 0f).withOwnBody()
        arena.run(1)
        val first = assertNotNull(arena.character.controller?.body)

        arena.withOwnBody().run(1)

        assertFalse(arena.simulates(first), "the replaced character's body stayed")
        assertTrue(arena.simulates(assertNotNull(arena.character.controller?.body)), "and the new one has its own")
    }

    @Test
    fun groundContactTracksFreeMotionTransitions() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        assertTrue(arena.ground.grounded)
        assertFalse(arena.ground.freeMotion)

        // Switching from standing on the floor to Free sets freeMotion = true and grounded = false.
        arena.character.motionMode = CharacterMotionMode.Free
        arena.run(1)
        assertTrue(arena.ground.freeMotion)
        assertFalse(arena.ground.grounded)

        // Flying upward, horizontally, and descending back onto the floor keeps freeMotion = true and grounded = false.
        arena.intent.moveY = 2f
        arena.run(STEPS / 4)
        assertTrue(arena.ground.freeMotion)
        assertFalse(arena.ground.grounded)

        arena.intent.moveX = 3f
        arena.intent.moveY = 0f
        arena.intent.moveZ = 1f
        arena.run(STEPS / 4)
        assertTrue(arena.ground.freeMotion)
        assertFalse(arena.ground.grounded)

        arena.intent.moveX = 0f
        arena.intent.moveY = -3f
        arena.intent.moveZ = 0f
        arena.run(STEPS / 2)
        arena.intent.moveY = 0f
        arena.run(1)
        assertEquals(1f, arena.position.y, GROUND_TOLERANCE)
        assertTrue(arena.ground.freeMotion)
        assertFalse(arena.ground.grounded)

        // Returning to Grounded on the floor restores freeMotion = false and grounded = true.
        arena.character.motionMode = CharacterMotionMode.Grounded
        arena.run(1)
        assertFalse(arena.ground.freeMotion)
        assertTrue(arena.ground.grounded)
    }

    @Test
    fun aChildRigFollowsGroundedAndFreeMotionWithoutMisfiringTakeOffOrLand() = runTest {
        val arena = arena(jumpSpeed = 5f)
        arena.run(STEPS)
        arena.intent.driver = MovementDriver.Agent
        val rig = RigHarness(arena)

        // 1. Standing and walking in Grounded mode play "stand" and "walk".
        rig.step(30)
        assertEquals("stand", rig.playing)
        assertEquals("stand", rig.activeClipId)

        rig.move(x = 0.5f, steps = 25)
        assertEquals("walk", rig.playing)
        assertEquals("walk", rig.activeClipId)

        rig.move(steps = 30)
        assertEquals("stand", rig.playing)

        // 2. Switching to Free updates GroundContact and drives "hover", "glide", and "boost" across 3D intent without "takeoff" or "land".
        arena.character.motionMode = CharacterMotionMode.Free
        val enterFree = rig.step(1)
        assertTrue(arena.ground.freeMotion)
        assertFalse(arena.ground.grounded)
        assertEquals(listOf("hover"), enterFree)

        val freeClimb = rig.move(y = 2f, steps = 25)
        assertEquals("glide", rig.playing)
        assertFalse("takeoff" in freeClimb || "land" in freeClimb, "free climb must never fire takeoff or land: $freeClimb")

        val freeBoost = rig.move(x = 3f, y = 3f, z = 2f, steps = 25)
        assertEquals("boost", rig.playing)
        assertFalse("takeoff" in freeBoost || "land" in freeBoost, "free boost must never fire takeoff or land: $freeBoost")

        // 3. Returning to the floor in Free and switching back to Grounded transitions cleanly without "takeoff" or "land".
        rig.move(y = -4f, steps = 60)
        rig.move(steps = 30)
        assertEquals("hover", rig.playing)
        assertEquals(1f, arena.position.y, GROUND_TOLERANCE)

        arena.character.motionMode = CharacterMotionMode.Grounded
        val exitFreeOnFloor = rig.step(5)
        assertFalse(arena.ground.freeMotion)
        assertTrue(arena.ground.grounded)
        assertEquals("stand", rig.playing)
        assertFalse("takeoff" in exitFreeOnFloor || "land" in exitFreeOnFloor, "mode switch on floor must not fire takeoff or land: $exitFreeOnFloor")

        // 4. Hovering above the floor in Free and switching back to Grounded updates freeMotion = false without firing "takeoff" or "land" on the toggle.
        arena.character.motionMode = CharacterMotionMode.Free
        rig.move(y = 3f, steps = 30)
        rig.move(steps = 30)
        assertEquals("hover", rig.playing)
        assertTrue(arena.position.y > 2f)

        arena.character.motionMode = CharacterMotionMode.Grounded
        val exitFreeMidAir = rig.step(1).single()
        assertFalse(arena.ground.freeMotion)
        assertFalse(arena.ground.grounded)
        assertEquals("fall", exitFreeMidAir, "leaving free motion in mid-air enters fall without firing takeoff or land")
    }

    private suspend fun arena(jumpSpeed: Float): Arena {
        val world = World()
        SceneLoader.decode(scene(jumpSpeed)).instantiate(world = world, componentRegistry = registry)
        val physicsWorld = createJoltPhysicsWorld()
        return Arena(world, physicsWorld, PhysicsSystem(physicsWorld), CharacterControllerSystem(physicsWorld), world.named("Player"))
    }

    private class Arena(
        val world: World,
        val physicsWorld: PhysicsWorld,
        val physics: PhysicsSystem,
        val characters: CharacterControllerSystem,
        val player: Entity,
    ) {
        /** Gives the player a fresh character that carries a body of its own, as a scene with a sensor does. */
        fun withOwnBody(): Arena = apply {
            world.add(player, CharacterController(character.config.copy(innerBody = true), character.jumpSpeed, character.gravity))
        }

        fun simulates(handle: BodyHandle): Boolean {
            var found = false
            physicsWorld.forEachBodyTransform { visited, _, _ -> if (visited == handle) found = true }
            return found
        }

        val position get() = world.get<Transform>(player)!!.position
        val intent get() = world.get<MovementControl>(player)!!
        val character get() = world.get<CharacterController>(player)!!
        val ground get() = world.get<GroundContact>(player)!!

        fun run(steps: Int) = repeat(steps) {
            physics.update(world, DELTA)
            characters.update(world, DELTA)
        }

        private var unsimulated = 0f

        /**
         * [count] rendered frames of [frameSeconds], stepping at [DELTA] as a host's fixed-step loop
         * does and blending between steps where the systems can; the position each frame shows.
         */
        fun frames(count: Int, frameSeconds: Float): List<Vec3f> = List(count) {
            unsimulated += frameSeconds
            while (unsimulated >= DELTA) {
                run(1)
                unsimulated -= DELTA
            }
            (characters as? InterpolatedSystem)?.interpolate(world, unsimulated / DELTA)
            position.copy()
        }
    }

    private class RigHarness(private val arena: Arena) {
        private val transforms = TransformSystem()
        private val locomotionSystem = LocomotionAnimationSystem()
        private val animationSystem = AnimationSystem()
        private val player = AnimationPlayer(
            AnimationLibrary(
                skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat(), Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0)),
                clips = RIG_CLIPS.mapValues { (name, seconds) ->
                    AnimationClip(name, listOf(AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, seconds), FloatArray(6), 3))))
                },
            ),
        )
        private val locomotion = LocomotionAnimation(
            SceneLocomotionAnimation(
                idle = "stand",
                walk = "walk",
                run = "run",
                jump = "rise",
                takeOff = "takeoff",
                fall = "fall",
                land = "land",
                free = SceneFreeLocomotionAnimation(idle = "hover", move = "glide", fast = "boost"),
            ),
        )

        init {
            val rig = arena.world.create()
            arena.world.add(rig, Transform(parent = arena.player))
            arena.world.add(rig, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
            arena.world.add(rig, SkinnedPose(FloatArray(16)))
            arena.world.add(rig, locomotion)
        }

        val playing: String? get() = locomotion.playing
        val activeClipId: String? get() = player.activeClipId

        fun step(count: Int): List<String?> = List(count) {
            arena.run(1)
            transforms.update(arena.world, DELTA)
            locomotionSystem.update(arena.world, DELTA)
            animationSystem.update(arena.world, DELTA)
            locomotion.playing
        }

        fun move(x: Float = 0f, y: Float = 0f, z: Float = 0f, steps: Int): List<String?> {
            arena.intent.moveX = x
            arena.intent.moveY = y
            arena.intent.moveZ = z
            return step(steps)
        }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private fun scene(jumpSpeed: Float) = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.1, "z": 10.0 } } }
  ] },
  { "name": "Wall", "transform": { "position": { "x": 0.0, "y": 1.0, "z": -3.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 1.0, "z": 0.5 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 3.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 4.0 },
    { "component": "character_controller", "jumpSpeed": $jumpSpeed }
  ] }
] }
"""

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS = 120
        const val GROUND_TOLERANCE = 0.05f
        const val FRAMES = 120

        /** How much farther the longest frame's move may be than the shortest's. */
        const val EVEN_FRAMES = 1.5f

        val RIG_CLIPS = mapOf(
            "stand" to 1f,
            "walk" to 1f,
            "run" to 1f,
            "rise" to 0.3f,
            "takeoff" to 0.25f,
            "fall" to 0.4f,
            "land" to 0.4f,
            "hover" to 1f,
            "glide" to 1f,
            "boost" to 1f,
        )
    }
}
