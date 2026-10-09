/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.particles.ParticleEmitterSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A host that holds the content itself passes it to Core's capabilities under [CoreSceneContent]'s keys. */
class CoreSceneContentTest {

    @Test
    fun aHostsOwnSpritesGiveItsEmittersTheirParticles() {
        assertTrue(emitterRuns(SceneContent.build { this[CoreSceneContent.ParticleSprites] = mapOf("dust.png" to DUST) }))
    }

    /** The control: with nothing under the key, the emitter waits for its sprite. */
    @Test
    fun withoutItsSpriteAnEmitterWaits() {
        assertFalse(emitterRuns(SceneContent.Empty))
    }

    private fun emitterRuns(content: SceneContent): Boolean {
        installProjectComponents()
        val scene = SceneLoader.decode(PARTICLE_SCENE)
        val world = World()
        scene.instantiate(world = world)
        val services = SceneHostServices(
            input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
            renderer = NoopRenderer(),
            content = content,
        )
        sceneSystemsFor(scene, services).use { systems -> systems.frame.forEach { it.update(world, STEP) } }
        var emitters = 0
        world.queryEach(ParticleEmitterSource::class) { entity: Entity, _ ->
            if (world.has(entity, ParticleEmitter::class)) emitters++
        }
        return emitters == 1
    }

    private companion object {
        const val STEP = 1f / 60f
        val DUST = TextureAsset(ByteArray(4), 1, 1)
        const val PARTICLE_SCENE = """
{ "version": 1, "name": "sparks", "nodes": [
  { "name": "Fountain", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "particle_emitter", "texture": "dust.png" } ] }
] }
"""
    }
}
