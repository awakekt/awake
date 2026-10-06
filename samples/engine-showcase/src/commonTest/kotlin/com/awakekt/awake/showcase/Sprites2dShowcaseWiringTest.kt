/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.showcase.examples.Sprites2dExampleDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural wiring test for the 2D sprites showcase: verifies scene document contents,
 * orthographic projection camera configuration, and driver lifecycle hooks.
 */
class Sprites2dShowcaseWiringTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    private val showcase = EngineShowcases.single { it.id == "sprites-2d" }

    @Test
    fun theSceneDocumentContainsOrthographicCameraAndSpriteNodes() = runTest {
        val document = SceneLoader.loadFromResource(showcase.scenePath)
        val names = document.nodes.mapNotNull { it.name }

        assertTrue("camera" in names, "scene contains camera node")
        assertTrue("background-plane" in names, "scene contains background plane")
        assertTrue("sprite-card-back" in names, "scene contains back card node")
        assertTrue("sprite-card-mid" in names, "scene contains mid card node")
        assertTrue("sprite-card-front" in names, "scene contains front card node")

        val cameraNode = document.nodes.single { it.name == "camera" }
        val camera = cameraNode.components.filterIsInstance<SceneCamera>().single()
        assertEquals(SceneCamera.Projection.Orthographic, camera.projection, "camera must use orthographic projection")
        assertTrue(camera.orthoHalfHeight > 0f, "orthographic half height must be positive")

        val renderers = document.nodes
            .filter { it.name?.startsWith("sprite-card-") == true }
            .flatMap { it.components }
            .filterIsInstance<SceneMeshRenderer>()
        assertEquals(3, renderers.size, "all 3 sprite card nodes must have mesh renderers")
        renderers.forEach { renderer ->
            assertEquals("sprite-quad", renderer.mesh)
            assertEquals("lit-shadow", renderer.material)
        }
    }

    @Test
    fun theShowcaseDefinesDriverAndLifecycleHooks() {
        assertNotNull(showcase.driver, "showcase defines frame driver")
        assertNotNull(showcase.onActivated, "showcase defines activation hook")
        assertNotNull(showcase.onDeactivated, "showcase defines deactivation hook")
    }

    @Test
    fun spriteQuadGeometryHasCorrectFormatAndTopology() {
        val geometry = Sprites2dExampleDriver.spriteQuadGeometry
        assertTrue(geometry.vertices.isNotEmpty(), "quad vertices must not be empty")
        assertEquals(6, geometry.indices.size, "quad indices define 2 triangles (6 indices)")
    }
}
