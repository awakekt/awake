/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.camera.OccluderFade
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.DitherFade
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A target at the origin, a third-person eye 8 behind it along +z, a wall between them and a tree off to the side. */
class PhysicsOccluderFadingTest {
    private val frame = 1f / 60f

    private class Scene(val world: World, val camera: Camera, val wallMesh: Entity, val tree: Entity)

    private fun scene(physics: PhysicsWorld, bodies: PhysicsSystem): Scene {
        val world = World()
        val target = world.create()
        world.add(target, Transform())
        val camera = Camera(Lens(eye = Vec3f(0f, 1.5f, 8f), center = Vec3f(0f, 1.5f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))
        val rig = world.create()
        world.add(rig, camera)
        world.add(rig, ActiveCamera())
        world.add(
            rig,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                needsReset = false
                targetEntity = target
                offsetPosition = Vec3f(0f, 1.8f, 0f)
                occluderFade = OccluderFade(opacity = 0.35f, fadeSeconds = 0.2f)
            },
        )
        val wall = world.create()
        world.add(wall, Transform(position = Vec3f(0f, 1f, 4f)))
        world.add(wall, PhysicsBody(shape = BoxShape(Vec3f(2f, 2f, 0.2f)), motionType = MotionType.STATIC))
        val wallMesh = world.create()
        world.add(wallMesh, Transform(parent = wall))
        world.add(wallMesh, MeshRenderer(FakeMesh, FakeMaterial))
        val tree = world.create()
        world.add(tree, Transform(position = Vec3f(10f, 1f, 4f)))
        world.add(tree, PhysicsBody(shape = BoxShape(Vec3f(0.5f, 2f, 0.5f)), motionType = MotionType.STATIC))
        world.add(tree, MeshRenderer(FakeMesh, FakeMaterial))
        bodies.update(world, frame)
        physics.step(frame)
        return Scene(world, camera, wallMesh, tree)
    }

    @Test
    fun aWallBetweenTheEyeAndTheTargetFadesAndReturnsOnceItStopsBlocking() = runTest {
        val physics = createJoltPhysicsWorld()
        try {
            val bodies = PhysicsSystem(physics)
            val scene = scene(physics, bodies)
            val fading = PhysicsOccluderFading(physics, bodies)

            fading.update(scene.world, frame)
            val first = scene.world.get<DitherFade>(scene.wallMesh)?.opacity
            assertTrue(first != null && first < 1f && first > 0.35f, "hit: the wall's mesh starts fading, $first")
            assertNull(scene.world.get<DitherFade>(scene.tree), "what is off the line is left alone")

            repeat(120) { fading.update(scene.world, frame) }
            assertEquals(0.35f, scene.world.get<DitherFade>(scene.wallMesh)!!.opacity, 1e-3f, "ease: it rests at the configured opacity")

            // The eye swings round to the other side: the wall no longer blocks.
            scene.camera.lens.eye.set(0f, 1.5f, -8f)
            fading.update(scene.world, frame)
            val returning = scene.world.get<DitherFade>(scene.wallMesh)!!.opacity
            assertTrue(returning > 0.35f && returning < 1f, "release: it eases back, not snaps, $returning")
            repeat(180) { fading.update(scene.world, frame) }
            assertNull(scene.world.get<DitherFade>(scene.wallMesh), "once opaque again its fade is taken off")
            assertEquals(0, fading.occluders)
        } finally {
            physics.destroy()
        }
    }

    private object FakeMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
