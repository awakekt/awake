/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.canvas.canvasOcclusion
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Followers that ask for occlusion are answered by physics rays in a played scene: a wall hides the
 * labels behind it, and the body of the node a label follows does not hide its own label.
 */
class CanvasOcclusionTest {
    private val input = Input()
    private val eye = Vec3f(0f, 1f, 0f)

    private fun services(physics: PhysicsWorld?, headless: Boolean = false): SceneHostServices {
        val gameplay = { GameplayInput(input.currentSnapshot, InputOwnership()) }
        return if (headless) SceneHostServices.headless(gameplay, physics) else SceneHostServices(gameplay, NoopRenderer(), physics)
    }

    /** [scene] instantiated with its systems, run for a few frames; [check] sees the world. */
    private suspend fun play(scene: String, check: (World, PhysicsCanvasOcclusion) -> Unit) {
        installProjectComponents()
        val physics = createJoltPhysicsWorld()
        try {
            val world = World()
            val document = SceneLoader.decode(scene)
            document.instantiate(world = world)
            val systems = sceneSystemsFor(document, services(physics))
            val occlusion = assertNotNull(systems.frame.filterIsInstance<PhysicsCanvasOcclusion>().singleOrNull(), "the scene's followers ask")
            repeat(3) {
                systems.fixed.forEach { it.update(world, STEP) }
                systems.frame.forEach { it.update(world, STEP) }
            }
            check(world, occlusion)
        } finally {
            physics.destroy()
        }
    }

    private fun World.node(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, node -> if (node.value == name) found = entity }
        return assertNotNull(found, name)
    }

    @Test
    fun aWallHidesWhatIsBehindItAndNotWhatIsBeside() = runTest {
        play(SCENE) { world, _ ->
            val occlusion = assertNotNull(world.canvasOcclusion())
            assertTrue(occlusion.occluded(world.node("Hidden"), eye, 10f, 1f, 0f), "the wall stands between")
            assertFalse(occlusion.occluded(world.node("Beside"), eye, 10f, 1f, 20f), "the ray passes the wall's end")
        }
    }

    @Test
    fun aCameraInsideAWallHidesNothing() = runTest {
        play(SCENE) { world, _ ->
            val occlusion = assertNotNull(world.canvasOcclusion())
            val inWall = Vec3f(5f, 1f, 0f)
            assertFalse(occlusion.occluded(world.node("Hidden"), inWall, 10f, 1f, 0f), "a ray from inside the wall cannot tell, so the label shows")
        }
    }

    @Test
    fun theFollowedNodesOwnBodyHidesNothing() = runTest {
        play(SCENE) { world, system ->
            val occlusion = assertNotNull(world.canvasOcclusion())
            val crate = world.node("Crate")
            assertFalse(occlusion.occluded(crate, eye, 3f, 1f, -6f), "the crate's own box does not hide its label")
            assertFalse(occlusion.occluded(world.node("Lid"), eye, 3f, 1.4f, -6f), "nor a label on a node under it")
            // The scene allows two rays a frame.
            system.update(world, STEP)
            assertTrue(occlusion.occluded(world.node("Beside"), eye, 6f, 1f, -12f), "but it hides someone else's point behind it")
        }
    }

    @Test
    fun theScenesBudgetCapsTheRaysAFrame() = runTest {
        play(SCENE) { world, occlusion ->
            val hidden = world.node("Hidden")
            occlusion.update(world, STEP)
            assertTrue(occlusion.occluded(hidden, eye, 10f, 1f, 0f))
            // A fresh frame, then more askers than the scene's two rays.
            occlusion.update(world, STEP)
            val targets = (0 until 10).map { world.create() }
            targets.forEach { occlusion.occluded(it, eye, 10f, 1f, 0f) }
            assertEquals(2, occlusion.raysCast, "the scene's canvas_follow_occlusion allows two a frame")
            assertTrue(occlusion.occluded(hidden, eye, 10f, 1f, 0f), "an answer already cast still stands")
        }
    }

    @Test
    fun aSceneWhoseFollowersDoNotAskCastsNoRays() = runTest {
        installProjectComponents()
        val physics = createJoltPhysicsWorld()
        try {
            val quiet = SCENE.replace("\"occlusion\": true", "\"occlusion\": false").replace(OCCLUSION_NODE, "")
            assertTrue(sceneSystemsFor(SceneLoader.decode(quiet), services(physics)).frame.none { it is PhysicsCanvasOcclusion })
            assertTrue(
                sceneSystemsFor(SceneLoader.decode(SCENE), services(null)).frame.none { it is PhysicsCanvasOcclusion },
                "nor one with no physics world",
            )
            assertTrue(
                sceneSystemsFor(SceneLoader.decode(SCENE), services(physics, headless = true)).frame.none { it is PhysicsCanvasOcclusion },
                "nor a host that draws no canvas",
            )
        } finally {
            physics.destroy()
        }
    }

    @Test
    fun aWorldWithoutTheSystemHasNoAnswer() {
        assertNull(World().canvasOcclusion())
    }

    private companion object {
        const val STEP = 1f / 60f

        const val OCCLUSION_NODE = """,
  { "name": "Budget", "components": [ { "component": "canvas_follow_occlusion", "raysPerFrame": 2, "reuseFrames": 4 } ] }"""

        /**
         * A wall across x 5, from z -5 to 5, between the eye at (0, 1, 0) and Hidden; Beside past its
         * end; a crate with a lid, each with a label that asks.
         */
        val SCENE = """
{ "version": 1, "name": "occlusion", "nodes": [
  { "name": "Wall", "transform": { "position": { "x": 5.0, "y": 1.0, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 5.0, "z": 5.0 } }, "motion": "STATIC" } ] },
  { "name": "Hidden", "transform": { "position": { "x": 10.0, "y": 1.0, "z": 0.0 } } },
  { "name": "Beside", "transform": { "position": { "x": 10.0, "y": 1.0, "z": 20.0 } } },
  { "name": "Crate", "transform": { "position": { "x": 3.0, "y": 1.0, "z": -6.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } }, "motion": "STATIC" } ],
    "children": [ { "name": "Lid", "transform": { "position": { "x": 0.0, "y": 0.4, "z": 0.0 } } } ] },
  { "name": "Labels", "components": [
    { "component": "canvas_element", "text": "hidden", "follow": "Hidden", "followRules": { "occlusion": true } }
  ] }$OCCLUSION_NODE
] }
"""
    }
}
