/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A static prop's draw command and cull verdict are reused until its transform, bounds, material or the camera change. */
class StaticDrawReuseTest {
    private val world = World()
    private val transforms = TransformSystem()
    private val culling = SceneCullingCompiler(ClipSpace.WebGpu)
    private val collector = SceneDrawCollector(culling)
    private val prop = spawn(Vec3f(0f, 0f, 0f))

    private fun collect(camera: Camera = CAMERA, shadows: ShadowCasterVolume? = null): List<RenderDrawCommand> {
        transforms.update(world, 0f)
        return collector.collectBeforeParticles(world, culling.prepare(world, camera, shadows = shadows), elapsedTimeSeconds = 0f)
            .toList()
    }

    @Test
    fun aStaticPropKeepsItsCommandWhileNothingChanges() {
        collect()
        assertEquals(0, collector.lastReusedCount, "the first frame collects it")
        collect()
        assertEquals(1, collector.lastReusedCount, "an unchanged prop must not be collected again")
    }

    @Test
    fun movingThePropRecomputesItsCommandAndBounds() {
        collect()
        world.get<Transform>(prop)!!.position = Vec3f(2f, 0f, 0f)
        val after = collect().single()
        assertEquals(0, collector.lastReusedCount, "a moved prop is collected again")
        assertEquals(1f, after.worldBounds!!.min.x, "bounds follow the new position")
    }

    @Test
    fun movingTheCameraRecomputesTheVerdict() {
        assertEquals(1, collect().size)
        val away = Camera(Lens(eye = Vec3f(0f, 0f, -5f), center = Vec3f(0f, 0f, -10f), fovYRadians = 1f, near = 0.1f, far = 100f))
        assertTrue(collect(away).isEmpty(), "a prop behind the camera is culled, not served from the last frame")
        assertEquals(1, collect().size, "and drawn again when the camera comes back")
    }

    @Test
    fun movingTheCameraKeepsAStillPropsCommand() {
        collect()
        val nudged = Camera(Lens(eye = Vec3f(0f, 0.5f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        collect(nudged)
        assertEquals(1, collector.lastReusedCount, "the command does not depend on the camera while the verdict holds")
    }

    @Test
    fun aChangedMaterialRebuildsTheCommand() {
        world.add(prop, PbrMaterial(metallic = 0.1f))
        val before = collect().single().extraUniformFloats.copyOf()
        world.get<PbrMaterial>(prop)!!.metallic = 0.9f
        val after = collect().single().extraUniformFloats
        assertTrue(!before.contentEquals(after), "a material edit must reach the draw")
    }

    @Test
    fun theShadowLightMovingReconsidersACullCasterAsShadowOnly() {
        val away = Camera(Lens(eye = Vec3f(0f, 0f, -5f), center = Vec3f(0f, 0f, -10f), fovYRadians = 1f, near = 0.1f, far = 100f))
        val volume = ShadowCasterVolume()
        assertTrue(collect(away, volume).isEmpty())
        volume.addPointShadow(Vec3f(0f, 0f, 0f), 3f)
        val draws = collect(away, volume)
        assertEquals(1, draws.size, "a caster the new light reaches is drawn for shadows")
        assertTrue(draws.single().shadowsOnly)
    }

    private fun spawn(at: Vec3f): Entity {
        val entity = world.create()
        world.add(entity, Transform(position = at))
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        return entity
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        val CAMERA = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
    }
}
