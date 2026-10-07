/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.render.passes.sprites.SpriteQuadGeometry
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.SceneSpriteClips
import com.awakekt.awake.showcase.examples.Sprites2dExampleDriver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        Sprites2dExampleDriver.preload()
        val authored = SceneLoader.loadFromResource(showcase.scenePath)
        assertTrue(authored.nodes.flatMap { it.components }.filterIsInstance<SceneSpriteClips>().none { "idle" in it.clips })
        val document = Sprites2dExampleDriver.importScene(authored)
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

        val sprites = document.nodes
            .filter { it.name?.startsWith("sprite-card-") == true }
            .flatMap { it.components }
            .filterIsInstance<SceneSprite>()
        assertEquals(3, sprites.size, "all 3 cards must use the sprite component")
        sprites.forEach { assertEquals("lantern-firefly", it.texture) }
        val assetPath = "assets/sprites/lantern-firefly/"
        val manifest = Json.parseToJsonElement(readResourceBytes(assetPath + "manifest.json").decodeToString()).jsonObject
        val layout = manifest.getValue("frame_layout").jsonObject
        val sheetWidth = layout.getValue("sheetWidth").jsonPrimitive.int
        val sheetHeight = layout.getValue("sheetHeight").jsonPrimitive.int
        val cellWidth = layout.getValue("cellWidth").jsonPrimitive.int
        val cellHeight = layout.getValue("cellHeight").jsonPrimitive.int
        val frames = layout.getValue("rows").jsonObject.getValue("idle").jsonArray
        val fps = manifest.getValue("animation").jsonObject.getValue("rows").jsonObject
            .getValue("idle").jsonObject.getValue("fps").jsonPrimitive.float
        sprites.forEach { sprite ->
            assertEquals(sheetWidth / cellWidth, sprite.columns)
            assertEquals(sheetHeight / cellHeight, sprite.rows)
            assertEquals(frames.size, sprite.cellCount)
            assertEquals(4f, fps)
        }
        val namedRuns = document.nodes.flatMap { it.components }.filterIsInstance<SceneSpriteClips>()
        assertEquals(sprites.size, namedRuns.size)
        namedRuns.forEach { animations ->
            assertEquals("idle", animations.clip)
            assertEquals(frames.size, animations.clips.getValue("idle").frameCount)
            assertEquals(fps, animations.clips.getValue("idle").framesPerSecond)
            assertEquals(2, animations.clips.getValue("blink").firstFrame)
        }
        frames.forEachIndexed { index, frame ->
            val rect = frame.jsonObject
            assertEquals(index * cellWidth, rect.getValue("x").jsonPrimitive.int)
            assertEquals(0, rect.getValue("y").jsonPrimitive.int)
            assertEquals(cellWidth, rect.getValue("w").jsonPrimitive.int)
            assertEquals(cellHeight, rect.getValue("h").jsonPrimitive.int)
        }
        val bitmap = createBitmap(readResourceBytes(assetPath + "sprite-sheet-alpha.png"))
        assertEquals(sheetWidth, bitmap.width)
        assertEquals(sheetHeight, bitmap.height)
    }

    @Test
    fun theShowcaseDefinesDriverAndLifecycleHooks() {
        assertNotNull(showcase.driver, "showcase defines frame driver")
        assertNotNull(showcase.onActivated, "showcase defines activation hook")
        assertNotNull(showcase.onDeactivated, "showcase defines deactivation hook")
    }

    @Test
    fun spriteQuadGeometryHasCorrectFormatAndTopology() {
        val geometry = SpriteQuadGeometry
        assertTrue(geometry.vertices.isNotEmpty(), "quad vertices must not be empty")
        assertEquals(6, geometry.indices.size, "quad indices define 2 triangles (6 indices)")
    }
}
