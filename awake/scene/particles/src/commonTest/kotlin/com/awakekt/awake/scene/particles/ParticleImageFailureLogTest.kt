/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.io.AssetLoads
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.scene.core.transform.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An emitter whose image fails to load is not drawn, and says so once, naming the image (#1621). On
 * main it said nothing: a scene's effects went missing with no line in the log.
 */
class ParticleImageFailureLogTest {
    private val records = mutableListOf<LogRecord>()

    @BeforeTest
    fun listen() {
        Log.reset()
        Log.install(LogSink { records += it })
    }

    @AfterTest
    fun stopListening() = Log.reset()

    @Test
    fun aFailedImageIsLoggedOnceAndItsEmitterIsNotDrawn() = runTest {
        val loads = AssetLoads<GpuImageSource>(backgroundScope, Dispatchers.Unconfined) { path -> error("no file at $path") }
        val world = World()
        val entity = world.create()
        world.add(entity, Transform())
        world.add(entity, ParticleEmitterSource(SceneParticleEmitter(texture = MISSING)))
        val system = ParticleContentSystem(NoopRenderer(), loads)

        repeat(FRAMES) { system.update(world, 0f) }

        val errors = records.filter { it.level == LogLevel.Error }
        assertNull(world.get<ParticleEmitter>(entity), "an emitter with no image is not drawn")
        assertEquals(1, errors.size, "logged once, not every frame: ${errors.map { it.message }}")
        assertTrue(MISSING in errors.single().message, "the line names the image: ${errors.single().message}")
        system.close()
        loads.close()
    }

    private companion object {
        const val MISSING = "effects/missing.png"
        const val FRAMES = 5
    }
}
