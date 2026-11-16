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
import com.awakekt.awake.ecs.World
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
 * The shadow depth array holds the layers a scene uses (#1688): the cascades in use, plus six faces
 * for each point light that casts. Before, both backends allocated all 28 layers -- four cascades and
 * four point lights' faces -- of 2048-texel 32-bit depth, about 448 MiB, whatever the scene drew.
 *
 * A sun-lit scene runs on an array that starts at three layers and stays there; then a shadowed point
 * light appears and the array grows once, to ten layers, and frames go on rendering. The device
 * fails on any validation error when it is destroyed.
 */
class RendererShadowLayersTest {
    @Test
    fun aSunLitSceneKeepsItsCascadesAndAPointShadowGrowsTheArrayOnce() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE, presentable = true, layers = ShadowBudget.DEFAULT_CASCADES)
        try {
            val world = scene(renderer)
            repeat(FRAMES) { renderer.frame(world) }
            val cascadesOnly = renderer.shadowMapBytes
            assertEquals(ShadowBudget.DEFAULT_CASCADES, renderer.depthTarget?.layers, "a sun-lit scene keeps its three cascades")
            assertTrue(cascadesOnly >= LAYER_BYTES * 3, "the bytes are the three layers': $cascadesOnly")

            world.create().also {
                world.add(it, Transform(position = Vec3f(0f, 3f, 0f)))
                world.add(it, Light(type = Light.Type.Point, range = 10f, shadowsEnabled = true))
            }
            repeat(FRAMES) { renderer.frame(world) }
            val withPoint = renderer.shadowMapBytes
            println("RendererShadowLayers: cascades only ${cascadesOnly / MIB} MiB; with a point shadow ${withPoint / MIB} MiB; all 28 layers ${LAYER_BYTES * 28 / MIB} MiB")
            assertEquals(POINT_LAYERS, renderer.depthTarget?.layers, "four cascade slots and one point light's six faces")
            assertTrue(withPoint >= LAYER_BYTES * POINT_LAYERS && withPoint < LAYER_BYTES * 28, "the grown array's bytes: $withPoint")
        } finally {
            renderer.waitIdle()
            owned.forEach { it() }
            release()
        }
    }

    /** What [scene] created, destroyed before the device is. */
    private val owned = mutableListOf<() -> Unit>()

    /**
     * Presented frames, as an app draws them, so the growth runs inside `draw` between frames.
     * `desktopSyncValidationTest` runs this class too: the reallocation and the rewritten
     * descriptor raise no hazard. With one frame in flight nothing overlaps here, so the wait for
     * frames in flight before the old array is freed is gated by `WindowedShadowGrowthTest`.
     */
    private fun Renderer.frame(world: World) = draw(RenderSystem3D(this).planCapture(world, camera, SIZE, SIZE))

    private fun scene(renderer: Renderer): World {
        val world = World()
        world.add(world.create(), camera)
        world.add(world.create(), Light(type = Light.Type.Directional, direction = Vec3f(-1f, 1f, 0f), shadowsEnabled = true))
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
        return world
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES = 3
        const val POINT_LAYERS = 10
        const val MIB = 1024L * 1024L
        const val LAYER_BYTES = 2048L * 2048L * 4L
        val camera = Camera(Lens(eye = Vec3f(0f, 8f, 8f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))
    }
}
