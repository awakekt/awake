/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A `mesh_renderer`'s glTF is only for drawing: a game server reads none of them. A collision shape's
 * model and a canvas image are read either way, as [MeshColliderTest] and [CanvasImagesTest] cover for
 * their own hosts; this is the one scene that mixes all three, so a model preloaded for one reason is
 * never mistaken for a model preloaded for another.
 */
class HeadlessModelsTest {
    @Test
    fun aHeadlessLoadReadsNoRenderModelButStillReadsTheCollisionMeshAndCanvasImage() = runTest {
        val reads = mutableListOf<String>()

        val headless = loadProject(recording(reads), physicsWorld = ::createJoltPhysicsWorld, hasRenderer = false)

        assertFalse(STATUE in reads, "the renderer-only model must not be read")
        assertFalse(headless.models.isPreloaded(STATUE), "and not preloaded either")
        assertTrue(RAMP in reads, "the collision mesh loads through its capability, not this guard")
        assertTrue(FRAME in reads, "a canvas image is still checked to resolve")
        headless.close()
    }

    /** The positive control: with a renderer, the same project reads the same models as today. */
    @Test
    fun aDrawnLoadStillReadsTheRenderModel() = runTest {
        val reads = mutableListOf<String>()

        val drawn = loadProject(recording(reads), physicsWorld = ::createJoltPhysicsWorld)

        assertTrue(STATUE in reads, "a host that draws still preloads the model its mesh_renderer names")
        assertTrue(drawn.models.isPreloaded(STATUE))
        assertTrue(RAMP in reads)
        assertTrue(FRAME in reads)
        drawn.close()
    }

    private fun recording(reads: MutableList<String>) = AssetSource { path: AssetPath ->
        reads += path.value
        PROJECT.read(path)
    }

    private companion object {
        const val STATUE = "assets/models/statue.gltf"
        const val RAMP = "props/ramp.gltf"
        const val FRAME = "ui/frame.png"

        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        val PROJECT = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                            .encodeToByteArray()
                    "scenes/main.scene.json" -> """
                        { "version": 1, "name": "server", "nodes": [
                          { "name": "Statue", "components": [
                            { "component": "meshRenderer", "mesh": "$STATUE", "material": "lit-shadow" } ] },
                          { "name": "Ramp", "components": [
                            { "component": "physics_body", "shape": { "type": "mesh", "mesh": "$RAMP" } } ] },
                          { "name": "Frame", "components": [ { "component": "canvas_element", "kind": "Panel",
                            "style": { "image": { "path": "$FRAME", "sliceLeft": 1, "sliceRight": 1 } } } ] }
                        ] }
                    """.trimIndent().encodeToByteArray()
                    STATUE -> triangleGltf().encodeToByteArray()
                    RAMP -> triangleGltf().encodeToByteArray()
                    FRAME -> PNG
                    else -> error("no ${path.value}")
                }
            }
        }
    }
}
