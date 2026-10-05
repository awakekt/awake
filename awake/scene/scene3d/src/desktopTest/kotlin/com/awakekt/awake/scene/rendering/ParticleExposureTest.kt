/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.scene.rendering.particles.ParticleGround
import com.awakekt.awake.scene.rendering.particles.ParticleLifecycle
import com.awakekt.awake.scene.rendering.particles.ParticleMotion
import com.awakekt.awake.scene.rendering.particles.ParticleVisual
import com.awakekt.awake.scene.rendering.particles.SceneParticleEmitter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every option of the particle library is either something a scene file can set or something that can
 * only be set from code, and this says which.
 *
 * A particle option used to be added to the runtime and left out of the scene file until someone
 * noticed, because nothing connected the two. This is the connection: a new option in [ParticleMotion],
 * [ParticleVisual], [ParticleGround] or [ParticleLifecycle], or a new field on [SceneParticleEmitter],
 * fails here until it is listed, so leaving one out is a decision written down rather than a slip.
 * `ParticleEmitterMapping.kt` is where a listed option is actually carried across, and
 * `SceneParticleEmitterTest` checks that it is.
 */
@OptIn(ExperimentalSerializationApi::class)
class ParticleExposureTest {
    /** Each runtime option, and the scene file field that sets it. */
    private val setFromSceneFile: Map<KClass<*>, Map<String, String>> = mapOf(
        ParticleMotion::class to mapOf(
            "baseVelocity" to "velocity",
            "velocityJitter" to "velocityJitter",
            "coneHalfAngleDegrees" to "coneHalfAngleDegrees",
            "spawnRadius" to "spawnRadius",
            "convergeToOrigin" to "convergeToOrigin",
            "turbulence" to "turbulence",
            "turbulenceFrequency" to "turbulenceFrequency",
            "radialSpeed" to "radialSpeed",
            "acceleration" to "acceleration",
            "inheritOrientation" to "inheritOrientation",
        ),
        ParticleVisual::class to mapOf(
            "startColor" to "color",
            "endColor" to "endColor",
            "frameCount" to "frameCount",
            "frameRate" to "frameRate",
            "stretchWithVelocity" to "stretchWithVelocity",
            "stretchFactor" to "stretchFactor",
            "endScale" to "endScale",
            "additive" to "additive",
            "facing" to "facing",
            "alphaCurve" to "alphaCurve",
        ),
        ParticleGround::class to mapOf(
            "groundY" to "ground",
            "colliders" to "ground",
            "restitution" to "ground",
            "friction" to "ground",
        ),
        ParticleLifecycle::class to mapOf(
            "burstCount" to "burstCount",
            "burstCycle" to "burstCycle",
        ),
    )

    /** Options that cannot be written as data, with why. Each is set from code through the live emitter. */
    private val codeOnly: Map<KClass<*>, Map<String, String>> = mapOf(
        ParticleGround::class to mapOf("groundHeightProvider" to "a function of position, so it cannot be data"),
        ParticleLifecycle::class to mapOf("onParticleDeath" to "a callback, so it cannot be data"),
    )

    /** The emitter's own settings, which a scene file sets at the top level and which have no group. */
    private val emitterSettings = setOf("texture", "maxParticles", "spawnRate", "lifetime", "startAlpha", "scale")

    private fun runtimeOptions(group: KClass<*>): Set<String> = group.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()

    private val sceneFileFields: Set<String> = SceneParticleEmitter.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyRuntimeOptionIsEitherSetFromASceneFileOrListedAsCodeOnly() {
        for (group in setFromSceneFile.keys) {
            val accountedFor = setFromSceneFile.getValue(group).keys + codeOnly[group].orEmpty().keys
            val actual = runtimeOptions(group)

            assertEquals(
                emptySet(),
                actual - accountedFor,
                "${group.simpleName} has options no scene file field is mapped to and that are not listed as code-only",
            )
            assertEquals(emptySet(), accountedFor - actual, "${group.simpleName} lists options that it no longer has")
        }
    }

    @Test
    fun everyMappedOptionNamesARealSceneFileField() {
        val targets = setFromSceneFile.values.flatMap { it.values }.toSet()

        assertEquals(emptySet(), targets - sceneFileFields, "mapped to scene file fields that do not exist")
    }

    @Test
    fun everySceneFileFieldSetsSomething() {
        val used = setFromSceneFile.values.flatMap { it.values }.toSet() + emitterSettings

        assertEquals(emptySet(), sceneFileFields - used, "scene file fields that set no runtime option")
        assertEquals(emptySet(), emitterSettings - sceneFileFields, "emitter settings the scene file no longer has")
    }

    @Test
    fun theCodeOnlyListSaysWhyForEachEntry() {
        assertTrue(codeOnly.values.flatMap { it.values }.all { it.isNotBlank() })
    }
}
