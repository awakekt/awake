/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.defaultLayerFor
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `mesh` collision shape collides with its model's real triangles under its node's rotation and
 * scale. The model is a ramp, so its bounding box answers differently almost everywhere.
 */
class MeshColliderTest {

    @Test
    fun raysHitTheRampWhereItsTrianglesAreUnderTheNodesTransform() = runTest {
        installProjectComponents()
        val scene = SceneLoader.decode(SCENE)
        val world = World()
        scene.instantiate(world = world)
        val physics = createJoltPhysicsWorld()
        try {
            val services = SceneHostServices(
                input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
                renderer = NoopRenderer(),
                physics = physics,
                collisionMeshes = loadCollisionMeshes(scene, files()),
            )
            sceneSystemsFor(scene, services).fixed.forEach { it.update(world, STEP) }

            assertRampUnder(physics)
        } finally {
            physics.destroy()
        }
    }

    @Test
    fun aConvexHullBodyFallsAndSettlesOnTheGround() = runTest {
        installProjectComponents()
        val scene = SceneLoader.decode(HULL_FALL_SCENE)
        val world = World()
        scene.instantiate(world = world)
        val physics = createJoltPhysicsWorld()
        try {
            val services = SceneHostServices(
                input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
                renderer = NoopRenderer(),
                physics = physics,
                collisionMeshes = loadCollisionMeshes(scene, files()),
            )
            val systems = sceneSystemsFor(scene, services).fixed
            repeat(180) { systems.forEach { it.update(world, STEP) } }

            var hullY: Float? = null
            world.queryEach(com.awakekt.awake.scene.core.transform.Transform::class) { entity, transform ->
                if (world.get<com.awakekt.awake.scene.core.Name>(entity)?.value == "FallingWedge") {
                    hullY = transform.position.y
                }
            }
            val settledY = assertNotNull(hullY)
            // Floor top is at y = 0.
            // Lowest local vertex of the ramp is y = -0.5.
            // So when resting flat on the floor, the body origin settles near y = 0.5.
            assertTrue(settledY in 0.45f..0.55f, "convex hull must settle at its lowest point on the floor: y=$settledY")

            // Positive control: a bounding box enclosing the model's bounds (-1..1 in y, i.e. halfHeight 0.75 centred at 0.25)
            // would settle with its bottom at y = 0, placing its origin at y = 0.75, which is distinct from 0.5.
            val boxRestingY = 0.75f
            assertTrue(kotlin.math.abs(settledY - boxRestingY) > 0.15f, "hull resting height ($settledY) must differ from box bounds ($boxRestingY)")
        } finally {
            physics.destroy()
        }
    }

    /** The positive control: the same checks fail against a box the size of the ramp's bounds. */
    @Test
    fun theChecksTellTheRampFromItsBoundingBox() = runTest {
        val physics = createJoltPhysicsWorld()
        try {
            // The model's bounds are 2 x 1 x 2 around (0, 0.5, 0); scaled (2, 3, 1) and turned like the node.
            physics.createBody(
                BoxShape(Vec3f(2f, 1.5f, 1f)),
                Vec3f(10f, 1.5f, 5f),
                Quat.fromEuler(Vec3f(0f, QUARTER_TURN, 0f)),
                MotionType.STATIC,
                defaultLayerFor(MotionType.STATIC),
                false,
            )
            physics.step(STEP)

            assertFailsWith<AssertionError> { assertRampUnder(physics) }
        } finally {
            physics.destroy()
        }
    }

    @Test
    fun aMissingModelNamesItsPathAndNode() = runTest {
        val sources = mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to SCENE.replace("props/ramp.glb", "props/missing.glb"))
        val files = AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }

        val error = assertFailsWith<IllegalArgumentException> { loadProject(files, ::createJoltPhysicsWorld) }

        assertTrue("props/missing.glb" in error.message.orEmpty() && "Bridge" in error.message.orEmpty(), error.message)
    }

    /**
     * The node turns the ramp a quarter turn about y and scales it (2, 3, 1) at (10, 0, 5): it covers
     * x 9..11 and z 3..7, rising from 0 at z = 7 to 3 at z = 3, so its height is 0.75 * (7 - z).
     */
    private fun assertRampUnder(physics: PhysicsWorld) {
        assertEquals(0.75f, assertNotNull(physics.groundAt(10f, 6f)), TOLERANCE, "low end")
        assertEquals(2.25f, assertNotNull(physics.groundAt(10f, 4f)), TOLERANCE, "high end")
        assertEquals(2.625f, assertNotNull(physics.groundAt(9.5f, 3.5f)), TOLERANCE, "reached only with the z scale of 2")
        assertNull(physics.groundAt(11.5f, 5f), "beside the ramp, where it would be without the turn")
        assertNull(physics.groundAt(10f, 7.5f), "past the low end")
    }

    private fun PhysicsWorld.groundAt(x: Float, z: Float): Float? =
        raycast(Vec3f(x, RAY_HEIGHT, z), Vec3f(0f, -1f, 0f), RAY_HEIGHT * 2f)?.point?.y

    private fun files(): AssetSource {
        val sources = mapOf("props/ramp.glb" to rampGlb())
        return AssetSource { path -> runCatching { sources.getValue(path.value) } }
    }

    /**
     * A ramp over x and z in -1..1 rising from y = 0 at x = -1 to y = 1 at x = 1, wound to face up:
     * float positions then uint indices, in one GLB.
     */
    private fun rampGlb(): ByteArray {
        // A 3D triangular prism (ramp) over x, z in -1..1, rising from y = 0 at x = -1 to y = 1 at x = 1,
        // with bottom face at y = -0.5 so it encloses volume and can form a valid 3D convex hull.
        val positions = floatArrayOf(
            -1f, 0f, -1f,
            1f, 1f, -1f,
            1f, 1f, 1f,
            -1f, 0f, 1f,
            -1f, -0.5f, -1f,
            1f, -0.5f, -1f,
            1f, -0.5f, 1f,
            -1f, -0.5f, 1f,
        )
        val indices = intArrayOf(
            3, 2, 1, 3, 1, 0, // top ramp
            4, 5, 6, 4, 6, 7, // bottom
            0, 1, 5, 0, 5, 4, // back
            1, 2, 6, 1, 6, 5, // high side
            2, 3, 7, 2, 7, 6, // front
            3, 0, 4, 3, 4, 7, // low side
        )
        val bin = ByteArray(positions.size * 4 + indices.size * 4)
        positions.forEachIndexed { i, value -> bin.putInt(i * 4, value.toRawBits()) }
        indices.forEachIndexed { i, value -> bin.putInt(positions.size * 4 + i * 4, value) }
        val json = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],
             "buffers":[{"byteLength":${bin.size}}],
             "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":${positions.size * 4}},
                            {"buffer":0,"byteOffset":${positions.size * 4},"byteLength":${indices.size * 4}}],
             "accessors":[{"bufferView":0,"componentType":5126,"count":${positions.size / 3},"type":"VEC3"},
                          {"bufferView":1,"componentType":5125,"count":${indices.size},"type":"SCALAR"}]}
        """.trimIndent().encodeToByteArray()
        val paddedJson = json + ByteArray((4 - json.size % 4) % 4) { ' '.code.toByte() }
        val glb = ByteArray(GLB_HEADER + CHUNK_HEADER + paddedJson.size + CHUNK_HEADER + bin.size)
        glb.putInt(0, GLB_MAGIC)
        glb.putInt(4, 2)
        glb.putInt(8, glb.size)
        glb.putInt(12, paddedJson.size)
        glb.putInt(16, JSON_CHUNK)
        paddedJson.copyInto(glb, GLB_HEADER + CHUNK_HEADER)
        val binAt = GLB_HEADER + CHUNK_HEADER + paddedJson.size
        glb.putInt(binAt, bin.size)
        glb.putInt(binAt + 4, BIN_CHUNK)
        bin.copyInto(glb, binAt + CHUNK_HEADER)
        return glb
    }

    private fun ByteArray.putInt(at: Int, value: Int) {
        for (byte in 0 until 4) this[at + byte] = (value ushr (byte * 8)).toByte()
    }

    private companion object {
        const val STEP = 1f / 60f
        const val TOLERANCE = 0.01f
        const val RAY_HEIGHT = 10f
        val QUARTER_TURN = (PI / 2).toFloat()
        const val GLB_MAGIC = 0x46546C67
        const val JSON_CHUNK = 0x4E4F534A
        const val BIN_CHUNK = 0x004E4942
        const val GLB_HEADER = 12
        const val CHUNK_HEADER = 8
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Bridge", "transform": {
      "position": { "x": 10.0, "y": 0.0, "z": 5.0 },
      "rotation": { "x": 0.0, "y": 1.5707964, "z": 0.0 },
      "scale": { "x": 2.0, "y": 3.0, "z": 1.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "mesh", "mesh": "props/ramp.glb" } } ] }
] }
"""

        const val HULL_FALL_SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Floor", "transform": {
      "position": { "x": 0.0, "y": -0.5, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.5, "z": 10.0 } }, "motion": "STATIC" } ] },
  { "name": "FallingWedge", "transform": {
      "position": { "x": 0.0, "y": 3.0, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "convex_hull", "mesh": "props/ramp.glb" }, "motion": "DYNAMIC" } ] }
] }
"""
    }
}
