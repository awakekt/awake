/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The attaches a View that keeps its picture watches are kept only for it, bounded, and for one frame (#1621). */
class CanvasViewAttachmentsTest {
    private val world = World()
    private val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
    private val attachments = CanvasViewAttachments()

    private fun inView() = world.create().also {
        world.add(it, Transform().apply { worldMatrix = Mat4() })
        world.add(it, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
    }

    @Test
    fun nothingIsKeptWhileNoViewKeepsItsPicture() {
        attachments.record(world, inView())
        assertFalse(attachments.meets(world, lens, 1f))
    }

    @Test
    fun anAttachIsMetForOneFrame() {
        attachments.recording = true
        attachments.record(world, inView())
        assertTrue(attachments.meets(world, lens, 1f))
        assertFalse(attachments.meets(World(), lens, 1f), "another world's View is not met")
        attachments.clear()
        assertFalse(attachments.meets(world, lens, 1f), "cleared every frame")
    }

    @Test
    fun pastItsCapacityEveryViewIsMet() {
        attachments.recording = true
        val outside = world.create().also {
            world.add(it, Transform().apply { worldMatrix = Mat4().translate(1_000f, 0f, 0f) })
            world.add(it, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        }
        repeat(CAPACITY) { attachments.record(world, outside) }
        assertFalse(attachments.meets(world, lens, 1f), "a full list of attaches outside the view")
        attachments.record(world, outside)
        assertTrue(attachments.meets(world, lens, 1f), "one more than it keeps redraws, rather than missing one")
    }

    private companion object {
        const val CAPACITY = 256
    }
}
