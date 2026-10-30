/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasParticles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every live option on [CanvasParticles] is either set from a [SceneCanvasParticles] field or
 * listed as code-only with a concrete reason, and every [SceneCanvasParticles] field maps to at
 * least one live option.
 */
@OptIn(ExperimentalSerializationApi::class)
class CanvasParticlesExposureTest {
    private val setFromSceneFile: Map<String, String> = mapOf(
        "maxParticles" to "maxParticles",
        "anchor" to "anchor",
        "offsetX" to "offsetX",
        "offsetY" to "offsetY",
        "placement" to "placement",
        "clipped" to "clipped",
        "localSpace" to "localSpace",
        "additive" to "additive",
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
        "alphaCurve" to "alphaCurve",
        "tint" to "tint",
        "endTint" to "endTint",
        "shape" to "shape",
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
        "burstCount" to "burstCount",
        "burstCycle" to "burstCycle",
    )

    private val codeOnly: Map<String, String> = mapOf(
        "dynamicSpawnRate" to "a per-frame function, so it cannot be data",
        "onParticleDeath" to "a callback, so it cannot be data",
    )

    /** Backing simulation pool, owner linkage, burst-pool flags, and bitmap/sheet caches. */
    private val internalPlumbing: Set<String> = setOf(
        "simulation",
        "owner",
        "pooledForBurst",
        "autoHideOwnerOnBurstComplete",
        "lastFrameStamp",
        "lastDensityRatio",
        "cachedSourceBitmap",
        "cachedPremultipliedBitmap",
        "cachedSheetCols",
        "cachedSheetRows",
        "cachedSheetRegions",
        "deathRelay",
    )

    private val runtimeFields: Set<String> = CanvasParticles::class.java.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()

    private val sceneFileFields: Set<String> = SceneCanvasParticles.serializer().descriptor.elementNames.toSet()

    @Test
    fun everyRuntimeFieldIsMappedFromSceneDataCodeOnlyOrPlumbing() {
        val accountedFor = setFromSceneFile.keys + codeOnly.keys + internalPlumbing
        assertEquals(
            emptySet(),
            runtimeFields - accountedFor,
            "CanvasParticles has fields neither mapped from SceneCanvasParticles nor listed as code-only",
        )
        assertEquals(
            emptySet(),
            accountedFor - runtimeFields,
            "CanvasParticlesExposureTest lists fields CanvasParticles no longer has",
        )
    }

    @Test
    fun everyMappedOptionNamesARealSceneFieldAndEverySceneFieldSetsSomething() {
        val targets = setFromSceneFile.values.toSet()
        assertEquals(emptySet(), targets - sceneFileFields, "mapped to SceneCanvasParticles fields that do not exist")
        assertEquals(emptySet(), sceneFileFields - targets, "SceneCanvasParticles fields that set no runtime option")
    }

    @Test
    fun everyCodeOnlyOptionDocumentsWhy() {
        assertTrue(codeOnly.values.all { it.isNotBlank() })
    }
}
