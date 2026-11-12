/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.game.AnchorPoint
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [entityAnchor] projects an entity as a canvas follower does, and hides what a follower would cull. */
class EntityAnchorTest {
    private val world = World()

    /** The world x and y land on the screen as they are; a negative z is behind the camera. */
    private val projector = object : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2? = if (z < 0f) null else Vec2(x, y)

        override fun bounds(entity: Entity): Rectangle? = null

        override fun projectFollow(x: Float, y: Float, z: Float): CanvasProjection =
            CanvasProjection(x, y, depth = z, distance = z, behind = z < 0f)
    }

    private fun entityAt(x: Float, y: Float, z: Float = 1f): Entity = world.create().also {
        world.add(it, Transform().apply { worldMatrix = Mat4().translate(x, y, z) })
    }

    private fun read(entity: Entity, offset: Vec3f = Vec3f(0f, 0f, 0f)): AnchorPoint =
        AnchorPoint(surfaceWidth = 800, surfaceHeight = 600).also { world.entityAnchor(entity, projector, offset).anchor(it) }

    @Test
    fun anEntityOnScreenAnchorsAtItsProjectedPoint() {
        val point = read(entityAt(300f, 200f))

        assertTrue(point.visible)
        assertEquals(300f to 200f, point.x to point.y)
    }

    @Test
    fun theOffsetMovesThePointInWorldSpace() {
        val point = read(entityAt(300f, 200f), Vec3f(0f, -50f, 0f))

        assertEquals(300f to 150f, point.x to point.y)
    }

    @Test
    fun anEntityBehindTheCameraIsHidden() {
        assertFalse(read(entityAt(300f, 200f, z = -5f)).visible)
    }

    @Test
    fun anEntityOffTheSurfaceIsHidden() {
        assertFalse(read(entityAt(900f, 200f)).visible, "right of 800")
        assertFalse(read(entityAt(300f, -1f)).visible, "above the top")
    }

    @Test
    fun anEntityWithNoTransformIsHidden() {
        assertFalse(read(world.create()).visible)
    }
}
