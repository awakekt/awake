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
import com.awakekt.awake.scene.canvas.setCanvasOcclusion
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.camera.CameraViewport
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun aFollowedPointCarriesItsDepthAndDistance() {
        val point = assertNotNull(projector.projectFollow(3f, 0f, 6f))
        val plain = assertNotNull(projector.project(3f, 0f, 6f))

        assertFalse(point.behind)
        assertEquals(plain.x, point.x, 0.01f)
        assertEquals(plain.y, point.y, 0.01f)
        assertEquals(4f, point.depth, 0.001f, "along the view, from z 10 to z 6")
        assertEquals(5f, point.distance, 0.001f, "straight from the eye: 3 across, 4 deep")
    }

    @Test
    fun vulkanAndWebGpuPutAFollowedPointInTheSamePlace() {
        val webGpu = SceneCanvasProjector(world, ClipSpace.WebGpu) { viewport }
        for ((x, y) in listOf(0f to 0f, 1f to 2f, -3f to -1f)) {
            val vulkan = assertNotNull(projector.projectFollow(x, y, 0f))
            val other = assertNotNull(webGpu.projectFollow(x, y, 0f))

            assertEquals(other.x, vulkan.x, 0.01f)
            assertEquals(other.y, vulkan.y, 0.01f, "Vulkan's flipped Y still puts +y up the screen")
        }
        assertTrue(assertNotNull(projector.projectFollow(0f, 2f, 0f)).y < 300f, "+y is up")
    }

    @Test
    fun aPointBehindTheCameraIsBehindByItsViewDepthAndLiesTheWayItIs() {
        for (clip in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu)) {
            val behind = SceneCanvasProjector(world, clip) { viewport }
            val right = assertNotNull(behind.projectFollow(2f, 0f, 15f))
            val above = assertNotNull(behind.projectFollow(0f, 2f, 15f))

            assertTrue(right.behind && above.behind, "$clip: z 15 is behind an eye at z 10 looking down -z")
            assertTrue(right.depth < 0f, "$clip: by view depth")
            assertTrue(right.x > 800f && kotlin.math.abs(right.y - 300f) < 1f, "$clip: to the camera's right lies off the right of the view")
            assertTrue(above.y < 0f && kotlin.math.abs(above.x - 400f) < 1f, "$clip: above the camera lies off the top")
        }
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
    fun occlusionIsTheWorldsAnswerFromTheCamerasEye() {
        val target = world.create()
        assertFalse(projector.occluded(target, 1f, 2f, 3f), "a world with no answer occludes nothing")

        var asked: List<Any>? = null
        world.setCanvasOcclusion { node, eye, x, y, z ->
            asked = listOf(node, eye.x, eye.y, eye.z, x, y, z)
            true
        }
        assertTrue(projector.occluded(target, 1f, 2f, 3f))
        assertEquals(listOf<Any>(target, 0f, 0f, 10f, 1f, 2f, 3f), asked, "the followed node, the eye it is drawn from, and the point")

        world.setCanvasOcclusion(null)
        assertFalse(projector.occluded(target, 1f, 2f, 3f), "taken away, nothing is occluded again")
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
