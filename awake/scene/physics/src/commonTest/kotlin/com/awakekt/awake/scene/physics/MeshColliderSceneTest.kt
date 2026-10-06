/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MeshColliderSceneTest {
    private val registry = SceneComponentRegistry().registerPhysics()

    @Test
    fun aMeshShapeMustBeAStaticSolidNamingAModel() {
        val moving = ScenePhysicsBody(SceneMeshShape("props/ramp.glb"), motion = MotionType.DYNAMIC).validate("Ramp")
        val sensor = ScenePhysicsBody(SceneMeshShape("props/ramp.glb"), sensor = true).validate("Ramp")
        val blank = ScenePhysicsBody(SceneMeshShape(" ")).validate("Ramp")

        assertTrue(moving.any { "STATIC" in it.message }, "$moving")
        assertTrue(sensor.any { "sensor" in it.message }, "$sensor")
        assertTrue(blank.any { "mesh" in it.message }, "$blank")
        assertEquals(emptyList(), ScenePhysicsBody(SceneMeshShape("props/ramp.glb", primitive = 0)).validate("Ramp"))
    }

    @Test
    fun theReferenceIsSavedBeforeAndAfterTheBodyIsBuilt() {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)
        val expected = ScenePhysicsBody(SceneMeshShape("props/ramp.glb", primitive = 1), layer = 3)

        val loaded = SceneLoader.fromWorld(world, componentRegistry = registry)
        MeshColliderSystem { _, _ -> RAMP }.update(world, STEP)
        val built = SceneLoader.fromWorld(world, componentRegistry = registry)

        assertEquals(listOf(expected), loaded.nodes.single().components.filterIsInstance<ScenePhysicsBody>())
        assertEquals(
            listOf(expected),
            built.nodes.single().components.filterIsInstance<ScenePhysicsBody>(),
            "the built body must save its reference once, not its triangles",
        )
        assertEquals(built, SceneLoader.decode(SceneLoader.encode(built)))
    }

    @Test
    fun aColliderWithNoLoadedTrianglesNamesTheModelAndTheNode() {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)

        val error = assertFailsWith<IllegalStateException> { MeshColliderSystem { _, _ -> null }.update(world, STEP) }

        assertTrue("props/ramp.glb" in error.message.orEmpty() && "Ramp" in error.message.orEmpty(), error.message)
    }

    @Test
    fun theNodesScaleIsBakedInAndAMirrorKeepsTheSolidSide() {
        val world = World()
        SceneLoader.decode(SCENE.replace("\"x\": 2.0", "\"x\": -2.0")).instantiate(world = world, componentRegistry = registry)
        MeshColliderSystem { _, _ -> RAMP }.update(world, STEP)

        var shape: MeshShape? = null
        world.queryEach(PhysicsBody::class) { _, body -> shape = body.shape as MeshShape }
        val checkedShape = checkNotNull(shape)

        assertContentEquals(floatArrayOf(-2f, 0f, 0f, -4f, 3f, 0f, -2f, 3f, 4f), checkedShape.vertices)
        assertContentEquals(intArrayOf(0, 2, 1), checkedShape.indices, "a mirror must swap two corners")
    }

    @Test
    fun anUnscaledPlacementSharesTheLoadedShape() {
        val world = World()
        SceneLoader.decode(SCENE.replace(SCALE, "")).instantiate(world = world, componentRegistry = registry)
        MeshColliderSystem { _, _ -> RAMP }.update(world, STEP)

        world.queryEach(PhysicsBody::class) { _, body -> assertSame(RAMP, body.shape) }
    }

    @Test
    fun aConvexHullShapeCanBeAnyMotionTypeAndASensor() {
        val blank = ScenePhysicsBody(SceneConvexHullShape(" ")).validate("Crate")
        val negativePrimitive = ScenePhysicsBody(SceneConvexHullShape("props/crate.glb", primitive = -1)).validate("Crate")

        assertTrue(blank.any { "mesh" in it.message }, "$blank")
        assertTrue(negativePrimitive.any { "primitive" in it.message }, "$negativePrimitive")

        assertEquals(emptyList(), ScenePhysicsBody(SceneConvexHullShape("props/crate.glb"), motion = MotionType.DYNAMIC).validate("Crate"))
        assertEquals(emptyList(), ScenePhysicsBody(SceneConvexHullShape("props/crate.glb"), motion = MotionType.STATIC).validate("Crate"))
        assertEquals(emptyList(), ScenePhysicsBody(SceneConvexHullShape("props/crate.glb"), motion = MotionType.KINEMATIC).validate("Crate"))
        assertEquals(emptyList(), ScenePhysicsBody(SceneConvexHullShape("props/crate.glb"), sensor = true).validate("Crate"))
    }

    @Test
    fun aConvexHullBodyIsBuiltWithBakedScaleAndExportedAsReference() {
        val world = World()
        SceneLoader.decode(HULL_SCENE).instantiate(world = world, componentRegistry = registry)
        val expected = ScenePhysicsBody(
            shape = SceneConvexHullShape("props/crate.glb", primitive = 0),
            motion = MotionType.DYNAMIC,
            layer = 2,
            sensor = true,
        )

        val loaded = SceneLoader.fromWorld(world, componentRegistry = registry)
        assertEquals(listOf(expected), loaded.nodes.single().components.filterIsInstance<ScenePhysicsBody>())

        MeshColliderSystem { _, _ -> CRATE }.update(world, STEP)

        val built = SceneLoader.fromWorld(world, componentRegistry = registry)
        assertEquals(
            listOf(expected),
            built.nodes.single().components.filterIsInstance<ScenePhysicsBody>(),
            "built convex hull body must export its reference back, not its raw points",
        )

        var hullShape: com.awakekt.awake.physics.ConvexHullShape? = null
        var bodyComponent: PhysicsBody? = null
        world.queryEach(PhysicsBody::class) { _, body ->
            bodyComponent = body
            hullShape = body.shape as com.awakekt.awake.physics.ConvexHullShape
        }

        val checkedBody = checkNotNull(bodyComponent)
        val checkedShape = checkNotNull(hullShape)
        assertEquals(MotionType.DYNAMIC, checkedBody.motionType)
        assertEquals(2, checkedBody.layer.index)
        assertTrue(checkedBody.sensor)
        assertContentEquals(floatArrayOf(2f, 0f, 0f, 4f, 3f, 0f, 2f, 3f, 4f, 0f, 3f, 4f), checkedShape.points)
    }

    private companion object {
        const val STEP = 1f / 60f
        val RAMP = MeshShape(floatArrayOf(1f, 0f, 0f, 2f, 1f, 0f, 1f, 1f, 1f), intArrayOf(0, 1, 2))
        val CRATE = MeshShape(
            floatArrayOf(1f, 0f, 0f, 2f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 1f),
            intArrayOf(0, 1, 2, 0, 2, 3),
        )
        const val SCALE = """, "scale": { "x": 2.0, "y": 3.0, "z": 4.0 }"""
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Ramp", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 0.0 }$SCALE }, "components": [
    { "component": "physics_body", "shape": { "type": "mesh", "mesh": "props/ramp.glb", "primitive": 1 }, "layer": 3 }
  ] }
] }
"""
        const val HULL_SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Crate", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 0.0 }$SCALE }, "components": [
    { "component": "physics_body", "shape": { "type": "convex_hull", "mesh": "props/crate.glb", "primitive": 0 }, "motion": "DYNAMIC", "layer": 2, "sensor": true }
  ] }
] }
"""
    }
}
