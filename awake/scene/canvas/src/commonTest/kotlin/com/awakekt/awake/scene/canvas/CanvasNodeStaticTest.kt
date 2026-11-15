/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.StaticTransform
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Canvas nodes are marked [StaticTransform], since nothing reads their world matrix; a marked node
 * is still placed as an unmarked one would be.
 */
class CanvasNodeStaticTest {
    private val world = World()
    private val marks = CanvasNodeStaticSystem()
    private val transforms = TransformSystem()

    private fun step() {
        marks.update(world, 0f)
        transforms.update(world, 0f)
    }

    private fun canvasNode(element: CanvasElement = CanvasElement(), parent: Entity? = null, name: String? = null): Entity =
        world.create().also {
            world.add(it, element)
            world.add(it, Transform(parent = parent))
            if (name != null) world.add(it, Name(name))
        }

    @Test
    fun canvasNodesAreMarkedAndWorldEntitiesAreNot() {
        val node = canvasNode()
        val prop = world.create().also { world.add(it, Transform()) }

        step()

        assertTrue(world.has<StaticTransform>(node))
        assertFalse(world.has<StaticTransform>(prop))
    }

    @Test
    fun aCanvasNodeAnotherElementFollowsIsLeftUnmarked() {
        val target = canvasNode(name = "Banner")
        val follower = canvasNode(CanvasElement().apply { follow = "Banner" })

        step()

        assertFalse(world.has<StaticTransform>(target), "a follower is placed by its target's world matrix")
        assertTrue(world.has<StaticTransform>(follower))
    }

    @Test
    fun aMarkedFollowerStillFollowsAMovingEntity() {
        val hero = world.create().also {
            world.add(it, Name("Hero"))
            world.add(it, Transform(position = Vec3f(10f, 0f, 0f)))
        }
        val follower = canvasNode(CanvasElement().apply { follow = "Hero" })
        val resolver = SceneCanvasResolver(world, Projector, views = null, bindings = null)
        step()
        assertTrue(world.has<StaticTransform>(follower))
        assertEquals(10f, resolver.point("Hero", Vec3f(0f, 0f, 0f))?.x)

        world.get<Transform>(hero)!!.position.x = 40f
        step()

        assertEquals(40f, resolver.point("Hero", Vec3f(0f, 0f, 0f))?.x)
    }

    @Test
    fun aMarkedNodeReparentedAtRuntimeIsPlacedUnderItsNewParent() {
        val left = CanvasElement()
        val right = CanvasElement()
        val leftPanel = canvasNode(left)
        val rightPanel = canvasNode(right)
        val child = canvasNode(parent = leftPanel)
        val order = CanvasItemOrder(world)
        step()
        assertTrue(world.has<StaticTransform>(child))
        assertSame(left, order.items().single { it.id == child.id }.parent)

        world.get<Transform>(child)!!.parent = rightPanel
        step()

        assertSame(right, order.items().single { it.id == child.id }.parent)
        assertFalse(world.has<StaticTransform>(leftPanel) && !world.has<StaticTransform>(rightPanel))
    }

    private object Projector : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x, y)

        override fun bounds(entity: Entity): Rectangle? = null

        override fun projectFollow(x: Float, y: Float, z: Float): CanvasProjection = CanvasProjection(x, y)
    }
}
