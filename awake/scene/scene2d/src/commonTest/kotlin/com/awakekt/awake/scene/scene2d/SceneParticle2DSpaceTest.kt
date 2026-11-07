/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.particles.ParticleEmitter2D
import com.awakekt.awake.particles.ParticleSpace
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A `particle_emitter_2d`'s `space` takes the [ParticleSpace] a 3D `particle_emitter` takes, and a scene
 * file written with the old `localSpace` still plays as it did.
 */
class SceneParticle2DSpaceTest {
    init {
        SceneComponentRegistry.registerGlobal(ParticleEmitter2DBinding)
    }

    private fun decode(component: String) = SceneLoader.decode("""{"version": 1, "nodes": [{"components": [$component]}]}""")
        .nodes.single().components.single() as SceneParticleEmitter2D

    private fun encode(emitter: SceneParticleEmitter2D) =
        SceneLoader.encode(SceneDocument(nodes = listOf(SceneNode("fx", components = listOf(emitter)))))

    @Test
    fun spaceRoundTripsAndIsWrittenWithoutTheOldField() {
        val local = SceneParticleEmitter2D(space = ParticleSpace.Local)
        val encoded = encode(local)

        assertEquals(local, decode("""{"component": "particle_emitter_2d", "space": "Local"}"""))
        assertEquals(local, SceneLoader.decode(encoded).nodes.single().components.single())
        assertFalse("localSpace" in encoded, "the old field is not written: $encoded")
        assertTrue(Regex(""""space"\s*:\s*"World"""").containsMatchIn(encode(SceneParticleEmitter2D())), "written as the other defaults are")
    }

    @Test
    fun anOldFilesLocalSpaceStillPlaysInLocalSpace() {
        val old = decode("""{"component": "particle_emitter_2d", "localSpace": true}""")

        val live = ParticleEmitter2D(old)

        assertEquals(ParticleSpace.Local, live.space)
        assertEquals(ParticleSpace.Local, live.toScene().space, "exported in the new field")
        assertFalse("localSpace" in encode(live.toScene()), "and only in the new field")
        assertEquals(ParticleSpace.World, ParticleEmitter2D(decode("""{"component": "particle_emitter_2d", "localSpace": false}""")).space)
    }

    @Test
    @Suppress("DEPRECATION")
    fun theDeprecatedRuntimePropertyReadsAndWritesSpace() {
        val emitter = ParticleEmitter2D(maxParticles = 1)

        emitter.localSpace = true
        assertEquals(ParticleSpace.Local, emitter.space)
        emitter.space = ParticleSpace.World
        assertFalse(emitter.localSpace)
    }
}
