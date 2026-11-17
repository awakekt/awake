/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.physics.PhysicsBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * A game server, which draws nothing, and a client that draws build the same terrain collider, at the
 * clipmap's level-0 spacing (#1768): a character the server stands on a slope stands where the client
 * draws it.
 */
class TerrainColliderParityTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun theServerAndTheClientBuildTheSameDrawnSurfaceHeightfield() = runTest {
        val server = collider(hasRenderer = false, backgroundScope)
        val client = collider(hasRenderer = true, backgroundScope)

        assertEquals(UPSAMPLED, server.sampleCount, "the server's collider is at level 0's 2 m spacing")
        assertEquals(server.sampleCount, client.sampleCount)
        assertEquals(server.scale, client.scale)
        assertEquals(server.origin, client.origin)
        assertContentEquals(server.heights, client.heights, "the same heights on both")
    }

    /** The terrain's collider shape once a project loaded for [hasRenderer] has stepped its scene once. */
    private suspend fun collider(hasRenderer: Boolean, scope: CoroutineScope): HeightFieldShape {
        val project = loadProject(files(), hasRenderer = hasRenderer, physicsWorld = { createJoltPhysicsWorld() })
        val instance = project.newSceneInstance()
        try {
            val systems = instance.sceneSystems(idle, renderer = if (hasRenderer) NoopRenderer() else null, coroutineScope = scope)
            systems.fixed.forEach { it.update(instance.world, STEP) }
            var shape: HeightFieldShape? = null
            instance.world.queryEach(PhysicsBody::class) { _, body -> (body.shape as? HeightFieldShape)?.let { shape = it } }
            return checkNotNull(shape) { "the terrain has no heightfield body" }
        } finally {
            instance.close()
            project.close()
        }
    }

    private fun files(): AssetSource {
        val random = Random(SEED)
        val samples = List(SIDE * SIDE) { random.nextFloat() * 3f - 1.5f }.joinToString()
        val scene = """
{ "version": 1, "name": "hills", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": $SIDE, "depth": $SIDE, "scaleX": 4.0, "scaleZ": 4.0, "samples": [$samples], "collider": true }
  ] }
] }
"""
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to scene)
        return AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }
    }

    private companion object {
        const val SEED = 1768
        const val SIDE = 17
        const val UPSAMPLED = (SIDE - 1) * 2 + 1
        const val STEP = 1f / 60f
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
    }
}
