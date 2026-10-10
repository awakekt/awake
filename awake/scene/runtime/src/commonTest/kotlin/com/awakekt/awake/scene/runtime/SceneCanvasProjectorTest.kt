/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.camera.CameraViewport
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneCanvasProjectorTest {
    private val world = World()
    private val viewport = CameraViewport().also {
        it.update(Camera(Lens.perspective(eye = Vec3f(0f, 0f, 10f))), 800f, 600f)
    }
    private val projector = SceneCanvasProjector(world, ClipSpace.Vulkan) { viewport }

    @Test
    fun aPointLandsWhereTheCameraDrawsIt() {
        val centre = assertNotNull(projector.project(0f, 0f, 0f))
        val right = assertNotNull(projector.project(1f, 0f, 0f))
        val up = assertNotNull(projector.project(0f, 1f, 0f))

        assertEquals(400f, centre.x, 0.5f)
        assertEquals(300f, centre.y, 0.5f)
        assertTrue(right.x > centre.x, "+x is to the right")
        assertTrue(up.y < centre.y, "+y is up the screen")
        assertNull(projector.project(0f, 0f, 20f), "behind the camera")
    }

    @Test
    fun boundsCoverTheMeshesOfANodeAndItsChildren() {
        val node = world.create().also { world.add(it, Transform()) }
        val left = world.create().also { mesh(it, parent = node, at = -2f) }
        mesh(world.create(), parent = left, at = 2f)
        mesh(world.create(), parent = null, at = 50f)

        val box = assertNotNull(projector.bounds(node))
        val leftEdge = assertNotNull(projector.project(-2.5f, 0f, 0.5f)).x
        val rightEdge = assertNotNull(projector.project(2.5f, 0f, 0.5f)).x

        assertEquals(leftEdge, box.x, 0.5f, "from the left mesh's near edge")
        assertEquals(rightEdge, box.x + box.width, 0.5f, "to the right one's, the unrelated mesh left out")
    }

    @Test
    fun aNodeWithNoMeshesHasNoBounds() {
        assertNull(projector.bounds(world.create().also { world.add(it, Transform()) }))
    }

    /** A unit cube at x [at], under [parent]. */
    private fun mesh(entity: com.awakekt.awake.ecs.Entity, parent: com.awakekt.awake.ecs.Entity?, at: Float) {
        world.add(entity, Transform(position = Vec3f(at, 0f, 0f), parent = parent).apply { worldMatrix = Mat4().translate(at, 0f, 0f) })
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
    }
}
