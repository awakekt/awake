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
import com.awakekt.awake.render.renderer.ShadowDepthFormat
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.renderer.readPresentedPixels
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * No shadow array is resident while no light casts (#1698). The renderer starts, as `VulkanEngine`
 * now does, with a 1x1 stand-in; the first frame a light casts allocates the cascades in use at the
 * budget's map size, and a residency window with no casting light releases them again. The scene
 * renders in every state: a shadowed frame is darker than an unshadowed one, and the frame after
 * release matches the one before allocation. The device fails on any validation error when it is
 * destroyed, so an unbound or `UNDEFINED` shadow layer would fail here too.
 */
class RendererLazyShadowArrayTest {
    private val owned = mutableListOf<() -> Unit>()

    @Test
    fun theArrayIsAllocatedOnlyWhileALightCasts() = withRenderer(ShadowDepthFormat.Depth32Float) { renderer ->
        val (world, sun) = scene(renderer)
        repeat(FRAMES) { renderer.frame(world) }
        assertNotResident(renderer, "before any light casts")
        val unshadowed = renderer.pixels()

        world.setSunShadows(sun, true)
        renderer.frame(world)
        val target = requireNotNull(renderer.depthTarget)
        assertEquals(ShadowBudget.DEFAULT_CASCADES, target.layers, "the cascades in use, the first frame one casts")
        assertEquals(ShadowBudget.DEFAULT_MAP_SIZE, target.size)
        assertTrue(renderer.shadowMapBytes >= ShadowDepthFormat.Depth32Float.layerBytes(target.size, target.layers))
        repeat(FRAMES) { renderer.frame(world) }
        val shadowed = renderer.pixels()
        assertTrue(shadowed.brightness() < unshadowed.brightness(), "the cube shadows the floor")
        val allocated = "${target.layers} layers of ${target.size}, ${renderer.shadowMapBytes / MIB} MiB"

        world.setSunShadows(sun, false)
        repeat(RELEASE_FRAMES) { renderer.frame(world) }
        assertNotResident(renderer, "a residency window after the light stopped casting")
        assertContentEquals(unshadowed, renderer.pixels(), "the released frame draws as it did before allocation")
        println("RendererLazyShadowArray: off ${renderer.shadowMapBytes} B; on $allocated")
    }

    @Test
    fun aMobileTierHoldsSmallerSixteenBitLayers() = withRenderer(ShadowDepthFormat.Depth16Unorm) { renderer ->
        renderer.shadowBudget = ShadowBudget.Reduced
        val (world, sun) = scene(renderer)
        repeat(FRAMES) { renderer.frame(world) }
        assertNotResident(renderer, "before any light casts")
        val unshadowed = renderer.pixels()

        world.setSunShadows(sun, true)
        repeat(FRAMES) { renderer.frame(world) }
        val target = requireNotNull(renderer.depthTarget)
        assertEquals(ShadowDepthFormat.Depth16Unorm, renderer.shadowDepthFormat)
        assertEquals(ShadowBudget.Reduced.cascades, target.layers)
        assertEquals(ShadowBudget.Reduced.mapSize, target.size)
        val bytes = renderer.shadowMapBytes
        val expected = ShadowDepthFormat.Depth16Unorm.layerBytes(target.size, target.layers)
        println("RendererLazyShadowArray: mobile tier ${target.layers} layers of ${target.size}, D16: $bytes B")
        assertTrue(bytes >= expected && bytes < expected * 2, "two 1024-texel 16-bit layers, 4 MiB: $bytes")
        assertTrue(renderer.pixels().brightness() < unshadowed.brightness(), "the cube shadows the floor in 16-bit depth")
    }

    private fun assertNotResident(renderer: Renderer, whenNot: String) {
        val target = requireNotNull(renderer.depthTarget)
        assertFalse(target.resident, "no shadow array $whenNot")
        assertTrue(renderer.shadowMapBytes < STAND_IN_BYTES, "only the 1x1 stand-in $whenNot: ${renderer.shadowMapBytes} B")
    }

    private fun withRenderer(format: ShadowDepthFormat, test: (Renderer) -> Unit) {
        val (renderer, release) = newDormantShadowRenderer(SIZE, format)
        try {
            test(renderer)
        } finally {
            renderer.waitIdle()
            owned.forEach { it() }
            release()
        }
    }

    private fun Renderer.frame(world: World) = draw(RenderSystem3D(this).planCapture(world, camera, SIZE, SIZE))

    private fun Renderer.pixels(): ByteArray = runBlocking { readPresentedPixels() }.data

    /** The sum of every colour channel: lower when more of the floor lies in shadow. */
    private fun ByteArray.brightness(): Long {
        var sum = 0L
        for (index in indices) if (index % 4 != 3) sum += this[index].toInt() and 0xFF
        return sum
    }

    private fun World.setSunShadows(sun: Entity, shadows: Boolean) {
        remove<Light>(sun)
        add(sun, Light(type = Light.Type.Directional, direction = Vec3f(-1f, 1f, 0f), shadowsEnabled = shadows))
    }

    private fun scene(renderer: Renderer): Pair<World, Entity> {
        val world = World()
        world.add(world.create(), camera)
        val sun = world.create()
        world.add(sun, Light(type = Light.Type.Directional, direction = Vec3f(-1f, 1f, 0f), shadowsEnabled = false))
        val material = renderer.createMaterial(LitShadowUniformLayout).also { owned += it::destroy }
        val plane = renderer.createMesh(generate { plane(size = 20f, colored = false) }).also { owned += it::destroy }
        val cube = renderer.createMesh(generate { cube(size = 2f, colored = false) }).also { owned += it::destroy }
        world.create().also {
            world.add(it, Transform(position = Vec3f(0f, 0f, 0f)))
            world.add(it, MeshRenderer(plane, material))
            world.add(it, MeshBounds(Aabb(Vec3f(-10f, 0f, -10f), Vec3f(10f, 0f, 10f))))
        }
        world.create().also {
            world.add(it, Transform(position = Vec3f(2f, 1f, 0f)))
            world.add(it, MeshRenderer(cube, material))
            world.add(it, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        }
        return world to sun
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES = 3
        const val RELEASE_FRAMES = ShadowArrayResidency.DEFAULT_RELEASE_AFTER_FRAMES * 2
        const val MIB = 1024L * 1024L

        /** Far below one real layer: a 1x1 image, whatever alignment the driver adds. */
        const val STAND_IN_BYTES = 1024L * 1024L
        val camera = Camera(Lens(eye = Vec3f(0f, 8f, 8f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))
    }
}
