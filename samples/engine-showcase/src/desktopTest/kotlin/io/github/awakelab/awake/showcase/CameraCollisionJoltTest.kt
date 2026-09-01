/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.jolt.JoltPhysicsWorld
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.scene.rendering.Camera
import io.github.awakelab.awake.showcase.examples.CharacterExampleDriver
import io.github.awakelab.awake.showcase.examples.ShowcasePhysics
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the third-person camera stops at the world instead of passing through it.
 *
 * A camera inside a hillside renders its interior, which looks like a rendering bug and is not
 * one. This is the sphere-cast use the plan called out: a ray would slip past the edge of an
 * obstruction and leave the near plane buried in it, so the assertion below is specifically that
 * the eye ends up outside the wall rather than merely somewhere nearer.
 */
class CameraCollisionJoltTest {

    private var world: JoltPhysicsWorld? = null

    @AfterTest
    fun tearDown() {
        ShowcasePhysics.world = null
        world?.destroy()
        world = null
    }

    private fun ecsWorldLookingThrough(wallAt: Float?, following: Boolean = true): Pair<World, Camera> {
        val physics = JoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        world = physics
        ShowcasePhysics.world = physics
        if (wallAt != null) {
            // A wall 1 unit thick, so its near face -- the side the camera is on -- is at
            // wallAt + 0.5.
            physics.createBody(
                BoxShape(Vec3f(0.5f, 5f, 5f)),
                Vec3f(wallAt, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
        }

        val ecs = World()
        val entity = ecs.create()
        // Character at the origin, eye 6 units out along +x, which is straight through the wall.
        val camera = Camera(
            lens = Lens(
                eye = Vec3f(6f, 0f, 0f),
                center = Vec3f(0f, 0f, 0f),
                fovYRadians = 1f,
                near = 0.1f,
                far = 100f,
            ),
        )
        ecs.add(entity, camera)
        ecs.add(entity, ActiveCamera())
        ecs.add(
            entity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                // A rig with a target is a follow camera; that is what puts it in scope.
                targetEntity = if (following) ecs.create() else null
                needsReset = false
            },
        )
        return ecs to camera
    }

    @Test
    fun anEyeBehindAWallIsPulledInFrontOfIt() {
        val (ecs, camera) = ecsWorldLookingThrough(wallAt = 3f)

        CharacterExampleDriver.cameraCollisionSystem().update(ecs, 1f / 60f)

        // The wall's near face is at 2.5, and the camera's own sphere holds it a radius clear.
        assertTrue(
            camera.lens.eye.x < 2.5f,
            "expected the eye in front of the wall at 2.5, got ${camera.lens.eye.x}",
        )
        assertTrue(
            camera.lens.eye.x > 1f,
            "expected the eye to stop at the wall, not collapse onto the character: ${camera.lens.eye.x}",
        )
    }

    @Test
    fun aCameraThatFollowsNothingIsLeftAlone() {
        // This system is installed for every showcase, and the terrain world outlives a switch
        // away from it. Pulling in the free orbit camera of a demonstration that never asked for
        // a third-person view would read as a bug in that demonstration.
        val (ecs, camera) = ecsWorldLookingThrough(wallAt = 3f, following = false)

        CharacterExampleDriver.cameraCollisionSystem().update(ecs, 1f / 60f)

        assertTrue(
            camera.lens.eye.x > 5.99f,
            "no character is being followed, so the camera must not be touched: ${camera.lens.eye.x}",
        )
    }

    @Test
    fun aClearViewLeavesTheCameraWhereTheRigPutIt() {
        val (ecs, camera) = ecsWorldLookingThrough(wallAt = null)

        CharacterExampleDriver.cameraCollisionSystem().update(ecs, 1f / 60f)

        assertTrue(
            camera.lens.eye.x > 5.99f,
            "nothing is in the way, so the rig's distance must survive: ${camera.lens.eye.x}",
        )
    }

    @Test
    fun theCameraIsBlockedByTheLevelAndNotByThePropsInIt() {
        // The showcase drops crates around the character's feet. Before collision layers the
        // camera treated them as walls and was shoved about by whatever the player happened to be
        // standing next to.
        val (ecs, camera) = ecsWorldLookingThrough(wallAt = null)
        val physics = requireNotNull(world)
        physics.createBody(
            BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
            Vec3f(2f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )

        CharacterExampleDriver.cameraCollisionSystem().update(ecs, 1f / 60f)

        assertTrue(
            camera.lens.eye.x > 5.99f,
            "a crate is not a wall, so the view must not be pulled in: ${camera.lens.eye.x}",
        )
    }

    @Test
    fun aBodyTouchingTheCharacterDoesNotCollapseTheCameraOntoIt() {
        // The showcase's own geometry: the four boxes are authored around the character's spawn,
        // so they land against it and the camera sweep genuinely starts in contact. Honouring
        // that fraction literally puts the eye inside the character's head, the near plane clips
        // everything, and the camera appears to hide the render rather than to move.
        val (ecs, camera) = ecsWorldLookingThrough(wallAt = null)
        val physics = requireNotNull(world)
        physics.createBody(
            BoxShape(Vec3f(0.5f, 0.5f, 0.5f)),
            // Straight on top of the pivot at the origin, and static so the camera can see it.
            Vec3f(0f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )

        CharacterExampleDriver.cameraCollisionSystem().update(ecs, 1f / 60f)

        val distance = camera.lens.eye.length3()
        assertTrue(
            distance > 1f,
            "the camera must keep its distance from the character even when something is touching " +
                "it, got $distance",
        )
    }
}
