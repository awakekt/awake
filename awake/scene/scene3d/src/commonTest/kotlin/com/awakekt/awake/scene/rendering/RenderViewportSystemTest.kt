/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystemTest.RecordingRenderer
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RenderViewportSystemTest {
    @Test
    fun virtualViewportResizesReplansAndKeepsAuthoredLensUnchanged() {
        val world = worldWithPrimaryCamera()
        val camera = assertNotNull(primaryCamera(world))
        camera.lens.projection = Lens.Projection.Orthographic
        camera.lens.orthoHalfHeight = 4f
        camera.viewport = com.awakekt.awake.core.math.VirtualViewport(10f, 10f)
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer, isRealtimeProvider = { false })
        system.update(world, 0f)
        assertEquals(RenderViewport(400f, 0f, 800f, 800f), renderer.lastViewport)
        assertEquals(4f, camera.lens.orthoHalfHeight)
        assertEquals(5f, system.cameraViewport.lens.orthoHalfHeight)
        system.update(world, 0f)
        assertFalse(system.lastFramePlanned)
        // Same aspect, different pixel extent: Screen and Fit both need a fresh plan.
        renderer.surfaceWidth = 800
        renderer.surfaceHeight = 400
        system.update(world, 0f)
        assertTrue(system.lastFramePlanned)
        assertEquals(RenderViewport(200f, 0f, 400f, 400f), renderer.lastViewport)
        camera.viewport = camera.viewport?.copy(scaling = com.awakekt.awake.core.math.ViewportScaling.Screen)
        system.update(world, 0f)
        assertTrue(system.lastFramePlanned)
        assertEquals(200f, system.cameraViewport.lens.orthoHalfHeight)
        renderer.surfaceWidth = 0
        system.update(world, 0f)
        assertFalse(system.lastFramePlanned)
        assertFalse(system.cameraViewport.layout.isValid)
    }

    @Test
    fun virtualViewportUsesPanelBoundsAndCaptureHasItsOwnDimensions() {
        val world = worldWithPrimaryCamera()
        val camera = assertNotNull(primaryCamera(world))
        camera.lens.projection = Lens.Projection.Orthographic
        camera.viewport = com.awakekt.awake.core.math.VirtualViewport(10f, 10f)
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer, viewportProvider = { RenderViewport(100f, 50f, 600f, 400f) })
        system.update(world, 0f)
        assertEquals(RenderViewport(200f, 50f, 400f, 400f), renderer.lastViewport)
        val capture = system.planCapture(world, camera, 300, 600)
        assertEquals(RenderViewport(0f, 150f, 300f, 300f), capture.viewport)
        assertEquals(RenderViewport(200f, 50f, 400f, 400f), system.cameraViewport.viewport)
    }

    @Test
    fun extendedViewDoesNotCullGeometryBeyondTheLegacyConservativeAspect() {
        val world = worldWithPrimaryCamera()
        val camera = assertNotNull(primaryCamera(world))
        camera.lens.projection = Lens.Projection.Orthographic
        camera.viewport = com.awakekt.awake.core.math.VirtualViewport(10f, 10f, com.awakekt.awake.core.math.ViewportScaling.Extend)
        val renderer = RecordingRenderer().also {
            it.surfaceWidth = 8000
            it.surfaceHeight = 800
        }
        val entity = world.create()
        world.add(entity, Transform(position = Vec3f(35f, 0f, 0f)))
        world.add(entity, MeshRenderer(fakeMesh(), fakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
        com.awakekt.awake.scene.core.transform.TransformSystem().update(world, 0f)
        RenderSystem3D(renderer).update(world, 0f)
        assertEquals(1, renderer.lastDrawCalls.size)
    }

    @Test
    fun clippedPanelUsesTheSameBoundsForProjectionAndPicking() {
        val world = worldWithPrimaryCamera()
        val camera = assertNotNull(primaryCamera(world))
        camera.lens.projection = Lens.Projection.Orthographic
        camera.viewport = com.awakekt.awake.core.math.VirtualViewport(10f, 10f)
        val renderer = RecordingRenderer()
        val system = RenderSystem3D(renderer, viewportProvider = { RenderViewport(-100f, 0f, 600f, 400f) })
        system.update(world, 0f)
        assertEquals(RenderViewport(50f, 0f, 400f, 400f), renderer.lastViewport)
        assertEquals(renderer.lastViewport, system.cameraViewport.viewport)
        world.clear()
        system.update(world, 0f)
        assertFalse(system.cameraViewport.layout.isValid)
    }

    private fun worldWithPrimaryCamera(): World = World().also { world ->
        world.add(world.create(), Camera(Lens.perspective()))
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }
    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
