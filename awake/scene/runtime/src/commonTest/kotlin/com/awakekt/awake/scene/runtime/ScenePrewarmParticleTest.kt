/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.io.AssetLoads
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.particles.ParticleEmitterSource
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A prewarm reads a tree's 3D particle images into the scene's, so an emitter draws on its first frame. */
class ScenePrewarmParticleTest : PrewarmFixture() {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val game = SceneAppLifecycleRuntime(spec().apply { sessionDispatcher = dispatcher }).apply { initialize(NoServices) }
    private val readable = CompletableDeferred<Unit>()
    private var imageReads = 0
    private val images = AssetLoads<GpuImageSource>(dispatcher) {
        imageReads++
        readable.await()
        TextureAsset(ByteArray(4), 1, 1).asGpuImageSource()
    }.also { library.particleImages = it }

    @Test
    fun aParticleEmittersTextureIsListedApartFromTheLibrarysTextures() {
        val references = SMOKE_NODE.assetReferences()

        assertEquals(setOf(SMOKE), references.particleTextures)
        assertEquals(emptySet(), references.textures)
    }

    @Test
    fun aPrewarmedParticleImageIsReadyBeforeTheEmittersFirstDraw() {
        val prewarm = game.prewarm(SMOKE_NODE)
        finishReading()
        assertTrue(prewarm.isReady)

        val world = World()
        val smoke = world.create().also { world.add(it, ParticleEmitterSource(SceneParticleEmitter(texture = SMOKE))) }
        ParticleContentSystem(NoopRenderer(), images).update(world, 0f)

        assertTrue(world.has(smoke, ParticleEmitter::class), "the emitter draws on its first frame")
        assertEquals(1, imageReads)
    }

    @Test
    fun theParticleSystemSharesTheLoadAPrewarmStarted() {
        val prewarm = game.prewarm(SMOKE_NODE)
        val world = World()
        val smoke = world.create().also { world.add(it, ParticleEmitterSource(SceneParticleEmitter(texture = SMOKE))) }
        val content = ParticleContentSystem(NoopRenderer(), images)
        content.update(world, 0f)
        assertFalse(world.has(smoke, ParticleEmitter::class), "it waits for the image")

        finishReading()
        content.update(world, 0f)

        assertTrue(prewarm.isReady)
        assertTrue(world.has(smoke, ParticleEmitter::class))
        assertEquals(1, imageReads)
    }

    private fun finishReading() {
        scheduler.runCurrent()
        readable.complete(Unit)
        scheduler.runCurrent()
        library.runPrewarms()
    }

    private companion object {
        const val SMOKE = "particles/smoke.png"
        val SMOKE_NODE = SceneNode(name = "Chimney", components = listOf(SceneParticleEmitter(texture = SMOKE)))
    }
}
