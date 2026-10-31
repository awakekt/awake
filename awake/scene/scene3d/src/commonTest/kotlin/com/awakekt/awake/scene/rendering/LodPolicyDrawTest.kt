/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.lod.LodPolicy
import com.awakekt.awake.core.lod.LodQuality
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.LodFollow
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneLod
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An [LodGroup] driving a [MeshRenderer] by screen coverage, and what a game sets at run time: a
 * policy, a follower, a coarser shadow level and the global quality. A box 1 wide (bounding radius
 * 0.87) on a 1-radian lens covers about `1.59 / distance` of the screen, so with thresholds of 25%
 * and 10% it draws level 0 nearer than 6.3, level 1 to 15.9 and level 2 beyond.
 */
class LodPolicyDrawTest {
    private val levelMeshes = List(3) { FakeMesh() }
    private val material = FakeMaterial()
    private val world = World()
    private val renderer = RenderSystemTest.RecordingRenderer()
    private val system = RenderSystem3D(renderer)

    @AfterTest
    fun resetQuality() {
        SceneLod.quality = LodQuality.Default
    }

    @Test
    fun aGroupPicksWhichMeshItsRendererDrawsByScreenCoverage() {
        camera()
        val model = model(z = -3f)
        assertEquals(listOf(0), drawnLevels(), "near")
        assertSame(material, renderer.lastDrawCalls.single().material, "the renderer's material, not the level's")
        move(model, -10f)
        assertEquals(listOf(1), drawnLevels())
        move(model, -100f)
        assertEquals(listOf(2), drawnLevels(), "far: the coarsest, never culled")
    }

    @Test
    fun aPolicyAndTheGlobalQualityChangeTheLevelOnTheNextFrame() {
        camera()
        val model = model(z = -100f)
        val group = requireNotNull(world.get<LodGroup>(model))
        group.policy = LodPolicy(forcedLevel = 0)
        assertEquals(listOf(0), drawnLevels(), "the player's own character stays at full detail")
        group.policy = LodPolicy(maxLevel = 1)
        assertEquals(listOf(1), drawnLevels(), "a party member never drops below level 1")
        group.policy = LodPolicy.Default
        move(model, -3f)
        group.policy = LodPolicy(bias = 1)
        assertEquals(listOf(1), drawnLevels(), "a crowd drawn a level coarser")
        group.policy = LodPolicy.Default
        move(model, -10f)
        SceneLod.quality = LodQuality(bias = -1)
        assertEquals(listOf(0), drawnLevels(), "a high quality setting")
        SceneLod.quality = LodQuality(coverageScale = 0.25f)
        assertEquals(listOf(2), drawnLevels(), "a low quality setting switches four times as early")
    }

    @Test
    fun aFollowingPartSwitchesOnTheSameFrameAsItsBody() {
        camera()
        val body = model(z = -5f)
        // The head is far smaller, so on its own thresholds it would drop a level far sooner.
        val head = model(z = -5f, half = 0.1f)
        requireNotNull(world.get<LodGroup>(head)).follow = LodFollow(body, levelOffset = 0)
        var z = -5f
        while (z > -60f) {
            move(body, z)
            move(head, z)
            system.update(world, DELTA)
            assertEquals(world.get<LodGroup>(body)!!.activeLevel, world.get<LodGroup>(head)!!.activeLevel, "at distance ${-z}")
            z -= 0.5f
        }
        requireNotNull(world.get<LodGroup>(head)).follow = LodFollow(body, levelOffset = -1)
        system.update(world, DELTA)
        assertEquals(1, world.get<LodGroup>(head)!!.activeLevel, "a head one level finer than its body")
    }

    @Test
    fun shadowsCastWithACoarserLevelThanTheOneDrawn() {
        camera()
        world.add(world.create(), Light(direction = Vec3f(0.2f, 1f, 0.3f)))
        val model = model(z = -3f)
        requireNotNull(world.get<LodGroup>(model)).policy = LodPolicy(shadowLevelOffset = 1)
        system.update(world, DELTA)
        val draws = renderer.lastDrawCalls
        val drawn = draws.single { !it.shadowsOnly }
        val shadow = draws.single { it.shadowsOnly }
        assertSame(levelMeshes[0], drawn.mesh)
        assertEquals(false, drawn.castsShadows, "the drawn level leaves casting to its twin")
        assertSame(levelMeshes[1], shadow.mesh)
        assertTrue(shadow.castsShadows)
    }

    @Test
    fun copiesAtOneLevelShareTheirMeshAndMaterialSoTheyFold() {
        camera()
        repeat(5) { model(z = -10f, x = it * 2f - 4f) }
        system.update(world, DELTA)
        val draws = renderer.lastDrawCalls
        assertEquals(5, draws.size)
        assertTrue(draws.all { it.mesh === levelMeshes[1] && it.material === material }, "one mesh and material: the instancing fold key")
    }

    @Test
    fun aFlyThroughSwitchesEachLevelOnceEachWayWithoutPopping() {
        val camera = camera()
        val model = model(z = 0f)
        var level = -1
        val switches = ArrayList<Int>()
        // Out to 60 and back, in steps of 0.05 with a jitter of 0.2 either way, as a hand-held camera moves.
        val path = (0..1_200).map { it * 0.05f } + (1_200 downTo 0).map { it * 0.05f }
        path.forEachIndexed { frame, distance ->
            val jitter = if (frame % 2 == 0) 0.2f else -0.2f
            camera.eye = Vec3f(0f, 0f, 5f + distance + jitter)
            system.update(world, DELTA)
            val now = requireNotNull(world.get<LodGroup>(model)).activeLevel
            if (level != -1 && now != level) switches += now
            level = now
        }
        assertEquals(listOf(1, 2, 1, 0), switches, "one switch per threshold each way, none back and forth")
    }

    private fun camera(): Lens {
        val lens = Lens(eye = Vec3f(0f, 0f, 0f), center = Vec3f(0f, 0f, -1f), fovYRadians = 1f, near = 0.1f, far = 500f)
        world.add(world.create(), Camera(lens))
        return lens
    }

    private fun model(z: Float, x: Float = 0f, half: Float = 0.5f): Entity = world.create().also { entity ->
        world.add(entity, Transform(worldMatrix = Mat4().translate(x, 0f, z)))
        world.add(entity, MeshRenderer(levelMeshes[0], material))
        world.add(entity, MeshBounds(Aabb(Vec3f(-half, -half, -half), Vec3f(half, half, half))))
        world.add(
            entity,
            LodGroup(levelMeshes.map { LodLevel(it, FakeMaterial(), maxDistance = 0f) }).also {
                it.minScreenCoverage = floatArrayOf(0.25f, 0.1f, 0f)
            },
        )
    }

    private fun move(entity: Entity, z: Float) {
        requireNotNull(world.get<Transform>(entity)).worldMatrix = Mat4().translate(0f, 0f, z)
    }

    private fun drawnLevels(): List<Int> {
        system.update(world, DELTA)
        return renderer.lastDrawCalls.filter { !it.shadowsOnly }.map { draw -> levelMeshes.indexOfFirst { it === draw.mesh } }
    }

    private class FakeMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private class FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val DELTA = 1f / 60f
    }
}
