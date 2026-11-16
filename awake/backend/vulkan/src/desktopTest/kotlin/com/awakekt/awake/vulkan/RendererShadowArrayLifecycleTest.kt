/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ShadowArrayResidency
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shadow array's life on Vulkan (#1698), from an engine-built renderer's dormant stand-in, beyond
 * what [RendererLazyShadowArrayTest] covers: a budget change reallocating at once, and the array kept
 * for at least one residency window after shadows go off, then freed within two
 * ([ShadowArrayResidency.DEFAULT_RELEASE_AFTER_FRAMES] frames each). The device fails on any
 * validation error when it is destroyed.
 */
class RendererShadowArrayLifecycleTest {
    private val owned = mutableListOf<() -> Unit>()

    @Test
    fun theArrayIsHeldOnlyWhileAShadowIsDrawn() {
        val (renderer, release) = newDormantShadowRenderer(SIZE)
        try {
            val world = scene(renderer)
            repeat(FRAMES) { renderer.frame(world) }
            val unlit = renderer.shadowMapBytes
            assertTrue(unlit < PLACEHOLDER_MAX_BYTES, "no casting light, no shadow array: $unlit B")

            val sun = world.create().also { world.add(it, sunLight()) }
            repeat(FRAMES) { renderer.frame(world) }
            val lit = renderer.shadowMapBytes
            assertEquals(ShadowBudget.DEFAULT_CASCADES, renderer.depthTarget?.layers)
            assertTrue(lit >= LAYER_BYTES_2048 * ShadowBudget.DEFAULT_CASCADES, "three 2048-texel cascades: $lit B")

            renderer.shadowBudget = ShadowBudget(cascades = ShadowBudget.DEFAULT_CASCADES, mapSize = REDUCED_SIZE)
            repeat(FRAMES) { renderer.frame(world) }
            val reduced = renderer.shadowMapBytes
            assertEquals(REDUCED_SIZE, renderer.depthTarget?.size, "a map-size change reallocates at once")

            world.remove<Light>(sun)
            val window = ShadowArrayResidency.DEFAULT_RELEASE_AFTER_FRAMES
            repeat(window - 1) { renderer.frame(world) }
            assertEquals(reduced, renderer.shadowMapBytes, "kept while a scene may switch its shadows back on")
            repeat(window + 1) { renderer.frame(world) }
            val released = renderer.shadowMapBytes
            println("ShadowArrayLifecycle: unlit $unlit B; lit ${lit / MIB} MiB; at $REDUCED_SIZE ${reduced / MIB} MiB; released $released B")
            assertTrue(released < PLACEHOLDER_MAX_BYTES, "freed within two windows: $released B")
        } finally {
            renderer.waitIdle()
            owned.forEach { it() }
            release()
        }
    }

    private fun Renderer.frame(world: World) = draw(RenderSystem3D(this).planCapture(world, camera, SIZE, SIZE))

    private fun sunLight() = Light(type = Light.Type.Directional, direction = Vec3f(-1f, 1f, 0f), shadowsEnabled = true)

    private fun scene(renderer: Renderer): World {
        val world = World()
        world.add(world.create(), camera)
        val material = renderer.createMaterial(LitShadowUniformLayout).also { owned += it::destroy }
        val plane = renderer.createMesh(generate { plane(size = 20f, colored = false) }).also { owned += it::destroy }
        val cube = renderer.createMesh(generate { cube(size = 2f, colored = false) }).also { owned += it::destroy }
        place(world, plane, material, Vec3f(0f, 0f, 0f), Aabb(Vec3f(-10f, 0f, -10f), Vec3f(10f, 0f, 10f)))
        place(world, cube, material, Vec3f(2f, 1f, 0f), Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f)))
        return world
    }

    private fun place(world: World, mesh: com.awakekt.awake.render.mesh.Mesh, material: com.awakekt.awake.render.material.Material, at: Vec3f, box: Aabb): Entity =
        world.create().also {
            world.add(it, Transform(position = at))
            world.add(it, MeshRenderer(mesh, material))
            world.add(it, MeshBounds(box))
        }

    private companion object {
        const val SIZE = 64
        const val FRAMES = 3
        const val REDUCED_SIZE = 1024
        const val MIB = 1024L * 1024L
        const val LAYER_BYTES_2048 = 2048L * 2048L * 4L

        /** A one-texel image rounds up to the driver's smallest allocation, kilobytes at most. */
        const val PLACEHOLDER_MAX_BYTES = 256L * 1024L
        val camera = Camera(Lens(eye = Vec3f(0f, 8f, 8f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))
    }
}
