/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.lod

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LodSelectorTest {
    /** Full detail above 25% of the screen, then 12%, 5%, and the last level at any size. */
    private val coverage = floatArrayOf(0.25f, 0.12f, 0.05f, 0f)

    private fun level(cover: Float, current: Int = LodSelector.NONE, hysteresis: Float = 0.1f) =
        LodSelector.coverageLevel(cover, coverage, current, hysteresis)

    @Test
    fun coveragePicksTheFirstLevelItIsLargeEnoughFor() {
        assertEquals(0, level(0.5f))
        assertEquals(0, level(0.25f))
        assertEquals(1, level(0.2f))
        assertEquals(2, level(0.06f))
        assertEquals(3, level(0.01f))
        assertEquals(3, level(0f), "picking a level never culls")
    }

    @Test
    fun hysteresisHoldsTheDrawnLevelNearAThresholdAndATeleportJumpsStraightThere() {
        // Just below 25%: a fresh pick goes to level 1, but level 0 already drawing holds within 10%.
        assertEquals(1, level(0.24f))
        assertEquals(0, level(0.24f, current = 0))
        assertEquals(1, level(0.2f, current = 0), "past the band the threshold decides")
        // Coming back, level 1 holds until the coverage is 10% past the threshold.
        assertEquals(1, level(0.26f, current = 1))
        assertEquals(0, level(0.3f, current = 1))
        assertEquals(3, level(0.001f, current = 0), "a teleport skips the levels between")
        assertEquals(1, level(0.24f, current = 0, hysteresis = 0f), "no band: the bare threshold")
    }

    @Test
    fun aCameraJitteringAtAThresholdSwitchesOnceNotEveryFrame() {
        var current = LodSelector.NONE
        var switches = 0
        repeat(200) { frame ->
            val jitter = if (frame % 2 == 0) 0.004f else -0.004f
            val next = level(0.25f + jitter, current)
            if (current != LodSelector.NONE && next != current) switches++
            current = next
        }
        assertEquals(0, switches)
    }

    @Test
    fun distanceThresholdsWorkTheSameWay() {
        val maxDistance = floatArrayOf(10f, 40f, 100f)
        fun pick(distance: Float, current: Int = LodSelector.NONE) =
            LodSelector.thresholdLevel(distance, maxDistance.size, current, 0.1f) { maxDistance[it] }
        assertEquals(0, pick(5f))
        assertEquals(1, pick(10.5f))
        assertEquals(0, pick(10.5f, current = 0))
        assertEquals(2, pick(1_000f))
    }

    @Test
    fun screenCoverageIsProjectedDiameterOverViewportHeight() {
        val fov = (PI / 2).toFloat()
        // A unit sphere 10 away with a 90° view: diameter 2 over a view 20 high.
        assertEquals(0.1f, LodSelector.screenCoverage(1f, 10f, fov), 1e-5f)
        assertEquals(Float.POSITIVE_INFINITY, LodSelector.screenCoverage(1f, 0.5f, fov))
    }

    @Test
    fun aPolicyBiasesClampsOrForcesTheLevel() {
        val quality = LodQuality(bias = 1)
        assertEquals(1, LodSelector.applyPolicy(0, 4, LodPolicy.Default, quality), "the global bias")
        assertEquals(0, LodSelector.applyPolicy(0, 4, LodPolicy(bias = -1), quality), "an entity's bias on top")
        assertEquals(3, LodSelector.applyPolicy(3, 4, LodPolicy(bias = 2)), "never past the last level")
        assertEquals(1, LodSelector.applyPolicy(3, 4, LodPolicy(maxLevel = 1)), "a party member never drops below level 1")
        assertEquals(1, LodSelector.applyPolicy(0, 4, LodPolicy(minLevel = 1)))
        assertEquals(0, LodSelector.applyPolicy(3, 4, LodPolicy(forcedLevel = 0), quality), "the player stays at full detail")
        assertEquals(3, LodSelector.applyPolicy(0, 4, LodPolicy(forcedLevel = 9)), "a forced level past the last is the last")
        assertEquals(1, LodSelector.applyPolicy(0, 2, LodPolicy(minLevel = 3)), "a range past the levels is the last level")
    }

    @Test
    fun shadowsCastCoarserAndPartsFollowTheirLeader() {
        assertEquals(2, LodSelector.shadowLevel(1, 4, LodPolicy(shadowLevelOffset = 1)))
        assertEquals(3, LodSelector.shadowLevel(3, 4, LodPolicy(shadowLevelOffset = 2)))
        assertEquals(2, LodSelector.followLevel(2, 0, 4, LodPolicy.Default))
        assertEquals(1, LodSelector.followLevel(2, -1, 4, LodPolicy.Default), "a head one level finer than its body")
        assertEquals(1, LodSelector.followLevel(3, 0, 4, LodPolicy(maxLevel = 1)))
        assertEquals(0, LodSelector.followLevel(3, 0, 4, LodPolicy(forcedLevel = 0)))
    }

    @Test
    fun invalidPoliciesAreRefused() {
        assertFailsWith<IllegalArgumentException> { LodPolicy(minLevel = 2, maxLevel = 1) }
        assertFailsWith<IllegalArgumentException> { LodPolicy(shadowLevelOffset = -1) }
        assertFailsWith<IllegalArgumentException> { LodQuality(coverageScale = 0f) }
        assertTrue(LodPolicy.Default.forcedLevel == null)
    }
}
