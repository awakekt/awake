/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.physics.jolt.createJoltPhysicsWorld
import io.github.awakelab.awake.scene.document.SceneLoader
import io.github.awakelab.awake.scene.runtime.destroy
import io.github.awakelab.awake.showcase.examples.RagdollExampleDriver
import io.github.awakelab.awake.showcase.examples.ShowcasePhysics
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the ragdoll showcase draws the figure the simulation is actually moving.
 *
 * The failure this is really for is a silent one: the driver hands its matrix list to
 * [io.github.awakelab.awake.scene.rendering.mesh.InstancedMeshRenderer] once, at activation, and
 * then rewrites those matrices in place every frame. Replacing the list rather than rewriting it --
 * the obvious thing to do when respawning -- leaves the renderer holding the old one, and the
 * figure freezes mid-air while the simulation carries on beneath it.
 */
class RagdollShowcaseTest {

    @AfterTest
    fun tearDown() {
        RagdollExampleDriver.detach()
        ShowcasePhysics.world?.destroy()
        ShowcasePhysics.world = null
    }

    @Test
    fun theDrawnMatricesFollowTheFallingFigure() = runTest {
        ShowcasePhysics.world = createJoltPhysicsWorld()
        RagdollExampleDriver.spawnForTest()
        val drawn = RagdollExampleDriver.drawnTransforms()
        assertEquals(HUMANOID_LIMBS, drawn.size, "the humanoid should draw one box per limb")

        RagdollExampleDriver.advance(0f)
        val startHeight = drawn.map { it.m13 }
        repeat(60) {
            ShowcasePhysics.world?.step(STEP)
        }
        RagdollExampleDriver.advance(STEP)

        // A second of falling. Every limb lower than it started is what says the matrices are the
        // live ones rather than a snapshot taken at activation.
        val ended = drawn.map { it.m13 }
        assertTrue(
            ended.indices.all { ended[it] < startHeight[it] },
            "the drawn boxes did not follow the fall: started $startHeight, ended $ended",
        )
    }

    @Test
    fun aRespawnDrivesTheSameMatricesTheRendererIsHolding() = runTest {
        ShowcasePhysics.world = createJoltPhysicsWorld()
        RagdollExampleDriver.spawnForTest()
        val drawn = RagdollExampleDriver.drawnTransforms()
        repeat(90) { ShowcasePhysics.world?.step(STEP) }
        RagdollExampleDriver.advance(STEP)
        val fallen = drawn.map { it.m13 }

        RagdollExampleDriver.spawnForTest()
        RagdollExampleDriver.advance(0f)

        // The same matrix objects, now describing the fresh figure. A respawn that built a new list
        // instead would leave the renderer drawing the collapsed one forever.
        val respawned = drawn.map { it.m13 }
        assertTrue(
            respawned.indices.all { respawned[it] > fallen[it] },
            "the drawn boxes did not follow the respawn: fallen $fallen, respawned $respawned",
        )
    }

    @Test
    fun theSceneCarriesTheNodeTheDriverAttachesTo() = runTest {
        val showcase = EngineShowcases.single { it.id == "ragdoll" }
        val scene = SceneLoader.instantiate(SceneLoader.loadFromResource(showcase.scenePath))
        try {
            // attach() looks the node up by name and returns silently if it is missing, so a
            // renamed node does not fail anywhere -- the showcase just draws an empty floor.
            assertTrue(
                scene.roots.any { it.name == "ragdoll" },
                "the ragdoll scene has no node for the driver to attach to",
            )
        } finally {
            scene.destroy()
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val HUMANOID_LIMBS = 11
    }
}
