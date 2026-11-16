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
import com.awakekt.awake.engine.window.GlfwWindow
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shadow array grows while frames are really in flight (#1688): a lit scene presents through a
 * real window and swapchain with two frames in flight, then gains a shadowed point light mid-run.
 * The array must grow once, to ten layers, with presents going on and validation -- synchronization
 * validation included, under `desktopWindowedTest` -- clean when the device is destroyed. Freeing
 * the old array without first waiting for the frames in flight is what this catches.
 *
 * Then the point light's shadow is switched off and on again, frame after frame. The array never
 * shrinks, so none of those toggles reallocates or waits: one reallocation in all.
 */
class WindowedShadowGrowthTest {
    private val owned = mutableListOf<() -> Unit>()

    @Test
    fun aPointShadowAppearingMidRunGrowsTheArrayOnceWhileFramesAreInFlight() {
        check(GlfwWindow.glfwInit()) { "glfwInit failed: is a display (xvfb) available?" }
        GlfwWindow.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        val window = GlfwWindow.glfwCreateWindow(SIZE, SIZE, "awake-windowed-shadow-growth")
        check(window != 0L) { "glfwCreateWindow returned null" }
        try {
            val (renderer, release) = newWindowedShadowRenderer(window, ShadowBudget.DEFAULT_CASCADES, FRAMES_IN_FLIGHT)
            try {
                run(renderer)
            } finally {
                renderer.waitIdle()
                owned.forEach { it() }
                release()
            }
        } finally {
            GlfwWindow.glfwDestroyWindow(window)
            GlfwWindow.glfwTerminate()
        }
    }

    private fun run(renderer: Renderer) {
        val world = scene(renderer)
        val target = requireNotNull(renderer.depthTarget)
        present(renderer, world)
        assertEquals(ShadowBudget.DEFAULT_CASCADES, target.layers, "a sun-lit scene keeps its three cascades")
        assertEquals(0, target.reallocations)

        val point = world.create()
        world.add(point, Transform(position = Vec3f(0f, 3f, 0f)))
        world.add(point, pointLight(shadows = true))
        present(renderer, world)
        assertEquals(POINT_LAYERS, target.layers, "four cascade slots and one point light's six faces")
        assertEquals(1, target.reallocations, "the array grew once")

        repeat(TOGGLES) { toggle ->
            world.setPointShadow(point, shadows = toggle % 2 == 1)
            repeat(2) { renderer.frame(world) }
        }
        val presented = renderer.swapchainManager.presentResults[VkResult.VK_SUCCESS.ordinal]
        println("WindowedShadowGrowth: ${target.reallocations} reallocation over $TOGGLES toggles, ${target.layers} layers, $presented presents")
        assertEquals(1, target.reallocations, "a point shadow that comes and goes reallocates, and waits, once")
        assertTrue(presented >= FRAMES * 2 + TOGGLES * 2 - FRAMES_IN_FLIGHT, "presents went on: $presented")
    }

    private fun present(renderer: Renderer, world: World) = repeat(FRAMES) { renderer.frame(world) }

    private fun Renderer.frame(world: World) {
        GlfwWindow.glfwPollEvents()
        draw(RenderSystem3D(this).planCapture(world, camera, outputWidth, outputHeight))
    }

    private fun World.setPointShadow(entity: Entity, shadows: Boolean) {
        remove<Light>(entity)
        add(entity, pointLight(shadows))
    }

    private fun pointLight(shadows: Boolean) = Light(type = Light.Type.Point, range = 10f, shadowsEnabled = shadows)

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
        const val GLFW_CLIENT_API = 0x00022001
        const val GLFW_NO_API = 0
        const val SIZE = 128
        const val FRAMES = 10
        const val FRAMES_IN_FLIGHT = 2
        const val TOGGLES = 8
        const val POINT_LAYERS = 10
        val camera = Camera(Lens(eye = Vec3f(0f, 8f, 8f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))
    }
}
