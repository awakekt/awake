/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.particles.ParticleEmitter2D
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every live option on [ParticleEmitter2D] is either set from a [SceneParticleEmitter2D] field or
 * listed as code-only with a concrete reason, and every [SceneParticleEmitter2D] field maps to at
 * least one live option.
 */
@OptIn(ExperimentalSerializationApi::class)
class Particle2DExposureTest {
    private val setFromSceneFile: Map<String, String> = mapOf(
        "maxParticles" to "maxParticles",
        "texture" to "texture",
        "columns" to "columns",
        "rows" to "rows",
        "frameCount" to "frameCount",
        "frameRate" to "frameRate",
        "enabled" to "enabled",
        "spawnRate" to "spawnRate",
        "lifetime" to "lifetime",
        "maxLifetime" to "maxLifetime",
        "size" to "size",
        "endSize" to "endSize",
        "startAlpha" to "startAlpha",
        "alphaRamp" to "alphaCurve",
        "tint" to "tint",
        "endTint" to "endTint",
        "spawnShape" to "shape",
        "speed" to "speed",
        "maxSpeed" to "maxSpeed",
        "directionDegrees" to "directionDegrees",
        "spreadDegrees" to "spreadDegrees",
        "radialSpeed" to "radialSpeed",
        "convergeToOrigin" to "convergeToOrigin",
        "gravityX" to "gravity",
        "gravityY" to "gravity",
        "drag" to "drag",
        "turbulence" to "turbulence",
        "turbulenceFrequency" to "turbulenceFrequency",
        "spin" to "spin",
        "additive" to "additive",
        "localSpace" to "localSpace",
        "inheritOrientation" to "inheritOrientation",
        "sortingLayer" to "sortingLayer",
        "sortOrder" to "sortOrder",
        "burstCount" to "burstCount",
        "burstSchedule" to "burstCycle",
    )

    private val codeOnly: Map<String, String> = mapOf(
        "dynamicSpawnRate" to "a per-frame function, so it cannot be data",
        "onParticleDeath" to "a callback, so it cannot be data",
    )

    /** Backing pool, burst-pool pinning and draw scratch, not authored options. */
    private val internalPlumbing: Set<String> = setOf(
        "simulation",
        "pooledForBurst",
        "pinned",
        "pinnedX",
        "pinnedY",
        "pinnedZ",
        "slotModels",
        "slotTints",
        "localScratch",
    )

    private val runtimeFields: Set<String> = ParticleEmitter2D::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()

    private val sceneFileFields: Set<String> = SceneParticleEmitter2D.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyRuntimeFieldIsMappedFromSceneDataCodeOnlyOrPlumbing() {
        val accountedFor = setFromSceneFile.keys + codeOnly.keys + internalPlumbing
        assertEquals(
            emptySet(),
            runtimeFields - accountedFor,
            "ParticleEmitter2D has fields neither mapped from SceneParticleEmitter2D nor listed as code-only",
        )
        assertEquals(
            emptySet(),
            accountedFor - runtimeFields,
            "Particle2DExposureTest lists fields ParticleEmitter2D no longer has",
        )
    }

    @Test
    fun everyMappedOptionNamesARealSceneFieldAndEverySceneFieldSetsSomething() {
        val targets = setFromSceneFile.values.toSet()
        assertEquals(emptySet(), targets - sceneFileFields, "mapped to SceneParticleEmitter2D fields that do not exist")
        assertEquals(emptySet(), sceneFileFields - targets, "SceneParticleEmitter2D fields that set no runtime option")
    }

    @Test
    fun everyCodeOnlyOptionDocumentsWhy() {
        assertTrue(codeOnly.values.all { it.isNotBlank() })
    }
}
