/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.ViewportScaling
import com.awakekt.awake.core.math.VirtualViewport
import com.awakekt.awake.scene.rendering.camera.CameraBinding.toComponent
import com.awakekt.awake.scene.rendering.camera.CameraBinding.toSceneComponent
import com.awakekt.awake.scene.rendering.camera.CameraViewport
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.camera.SceneViewport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CameraViewportTest {
    @Test
    fun policyRoundTripsThroughJsonAndBindingWithoutSavingResolvedDimensions() {
        SceneViewport.Scaling.entries.forEach { strategy ->
            val authored = SceneCamera(projection = SceneCamera.Projection.Orthographic, viewport = SceneViewport(16f, 9f, strategy))
            val decoded = Json.decodeFromString<SceneCamera>(Json.encodeToString(SceneCamera.serializer(), authored))
            val camera = decoded.toComponent()
            val view = CameraViewport()
            assertTrue(view.update(camera, 800f, 1200f))
            assertEquals(authored, camera.toSceneComponent())
            assertTrue(view.update(camera, 1800f, 900f))
            assertEquals(authored, camera.toSceneComponent())
        }
        val legacy = Json.decodeFromString<SceneCamera>("""{"projection":"orthographic","orthoHalfHeight":4} """)
        assertNull(legacy.viewport)
        assertEquals(4f, legacy.toComponent().lens.orthoHalfHeight)
    }

    @Test
    fun projectedWorldPointAndPickingRayAgreeAcrossClipConventionsAndStrategies() {
        val camera = SceneCamera(projection = SceneCamera.Projection.Orthographic).toComponent()
        val view = CameraViewport()
        val world = Vec3f(1f, -0.5f, 0f)
        ViewportScaling.entries.forEach { strategy ->
            camera.viewport = VirtualViewport(16f, 9f, strategy)
            assertTrue(view.update(camera, 900f, 1600f, 25f, 70f))
            listOf(ClipSpace.Vulkan, ClipSpace.WebGpu).forEach { clip ->
                val pixel = assertNotNull(view.projectToSurface(world, clip))
                val ray = assertNotNull(view.rayThroughSurface(pixel.x, pixel.y, clip))
                assertEquals(world.x, ray.origin.x, 1e-4f)
                assertEquals(world.y, ray.origin.y, 1e-4f)
                assertEquals(0f, ray.direction.x, 1e-4f)
                assertEquals(0f, ray.direction.y, 1e-4f)
                assertEquals(-1f, ray.direction.z, 1e-4f)
            }
        }
    }

    @Test
    fun letterboxCannotBePickedAndStableBoundsReuseTheGpuRectangle() {
        val camera = SceneCamera(projection = SceneCamera.Projection.Orthographic, viewport = SceneViewport(16f, 9f)).toComponent()
        val view = CameraViewport()
        view.update(camera, 900f, 1600f, 20f, 30f)
        val first = view.viewport
        assertNull(view.rayThroughSurface(470f, 30f, ClipSpace.WebGpu))
        assertNotNull(view.rayThroughSurface(470f, 830f, ClipSpace.WebGpu))
        view.update(camera, 900f, 1600f, 20f, 30f)
        assertSame(first, view.viewport)
        assertFalse(view.update(camera, 0f, 1600f))
        assertNull(view.rayThroughSurface(470f, 830f, ClipSpace.WebGpu))
    }

    @Test
    fun sceneRejectsInvalidDimensionsAndPerspectivePolicies() {
        assertTrue(SceneCamera(viewport = SceneViewport(16f, 9f)).validate("camera").any { "orthographic" in it.message })
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { bad ->
            val camera = SceneCamera(projection = SceneCamera.Projection.Orthographic, viewport = SceneViewport(bad, bad))
            assertTrue(camera.validate("camera").any { "width" in it.message })
            assertTrue(camera.validate("camera").any { "height" in it.message })
        }
    }
}
