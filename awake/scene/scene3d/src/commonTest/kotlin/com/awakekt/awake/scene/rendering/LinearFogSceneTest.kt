/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.fog.FogBinding.toComponent
import com.awakekt.awake.scene.rendering.fog.FogBinding.toSceneComponent
import com.awakekt.awake.scene.rendering.fog.FogMode
import com.awakekt.awake.scene.rendering.fog.SceneFog
import com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A scene's linear fog reaches the frame as a range, from a document or written at runtime (#1807). */
class LinearFogSceneTest {

    private val compiler = SceneLightingCompiler(ClipSpace.WebGpu)

    private fun World.withFog(fog: Fog): World = apply { add(create(), fog) }

    /** What the frame is told: a linear fog's start and end, or `0 to 0` for exponential fog. */
    private val EnvironmentUniforms.range get() = fogStart to fogEnd

    @Test
    fun exponentialFogCarriesNoRangeAndIsWhatAFogDidBefore() {
        val environment = compiler.environmentUniforms(World().withFog(Fog(density = 0.02f)))
        assertEquals(EXPONENTIAL, environment.range)
        assertEquals(0.02f, environment.fogDensity)
        assertEquals(EXPONENTIAL, compiler.environmentUniforms(World()).range, "no fog carries no range")
    }

    @Test
    fun aLinearFogReachesTheFrameAsItsRange() {
        val environment = compiler.environmentUniforms(World().withFog(Fog(mode = FogMode.Linear, start = 20f, end = 120f)))
        assertEquals(20f to 120f, environment.range)
    }

    @Test
    fun aDisabledLinearFogCarriesNoRange() {
        val fog = Fog(enabled = false, mode = FogMode.Linear, start = 20f, end = 120f)
        val environment = compiler.environmentUniforms(World().withFog(fog))
        assertEquals(EXPONENTIAL, environment.range)
        assertEquals(0f, environment.fogDensity)
    }

    @Test
    fun aFogWrittenAtRuntimeFogsTheNextFrameWithIt() {
        val fog = Fog()
        val world = World().withFog(fog)
        assertEquals(EXPONENTIAL, compiler.environmentUniforms(world).range)
        fog.mode = FogMode.Linear
        fog.start = 5f
        fog.end = 50f
        assertEquals(5f to 50f, compiler.environmentUniforms(world).range)
        fog.mode = FogMode.Exponential
        assertEquals(EXPONENTIAL, compiler.environmentUniforms(world).range)
    }

    @Test
    fun aRangeTheShadersCannotRampIsRefused() {
        assertFailsWith<IllegalArgumentException> { Fog(mode = FogMode.Linear, start = 50f, end = 50f) }
        assertFailsWith<IllegalArgumentException> { Fog(start = -1f) }
        assertEquals(1, SceneFog(start = 10f, end = 5f).validate("fog").size)
        assertEquals(1, SceneFog(end = Float.POSITIVE_INFINITY).validate("fog").size)
        assertEquals(emptyList(), SceneFog(mode = FogMode.Linear, start = 10f, end = 50f).validate("fog"))
        val world = World().withFog(Fog(mode = FogMode.Linear).also { it.end = 0f })
        assertFailsWith<IllegalArgumentException> { compiler.environmentUniforms(world) }
    }

    @Test
    fun aDocumentsLinearFogRoundTripsAndAnOlderDocumentFogsExponentially() {
        val json = Json { ignoreUnknownKeys = true }
        val saved = SceneFog(mode = FogMode.Linear, start = 15f, end = 300f)
        val decoded = json.decodeFromString(SceneFog.serializer(), json.encodeToString(SceneFog.serializer(), saved))
        assertEquals(saved, decoded)
        val live = decoded.toComponent()
        assertEquals(FogMode.Linear to (15f to 300f), live.mode to (live.start to live.end))
        assertEquals(saved, live.toSceneComponent())

        val older = json.decodeFromString(SceneFog.serializer(), """{"enabled":true,"density":0.004}""")
        assertEquals(FogMode.Exponential, older.toComponent().mode)
    }

    private companion object {
        val EXPONENTIAL = 0f to 0f
    }
}
