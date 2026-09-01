/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.scene.rendering.mesh.LodGroup
import io.github.awakelab.awake.scene.rendering.mesh.LodLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * What the band is for: an entity parked at a threshold draws ONE level, frame after frame.
 *
 * Without it, selection is a bare comparison, so a camera that breathes by a millimetre either
 * side of 100m swaps the mesh every frame -- which looks like flickering geometry, not like LOD.
 * Each test below states the distance sequence a camera would actually produce.
 */
class LodHysteresisTest {

    @Test
    fun anEntityHoveringOnAThresholdKeepsTheLevelItAlreadyDrew() {
        val group = twoLevelGroup()

        assertSame(group.levels[0], group.selectLevel(99.9f), "Inside the near level to begin with.")
        // A camera nudging back and forth across 100m, which is every frame of standing still.
        val drawn = listOf(100.1f, 99.9f, 100.2f, 99.8f, 100.05f).map { group.selectLevel(it) }

        assertEquals(
            listOf(group.levels[0]),
            drawn.distinct(),
            "Every one of those frames must draw the near level. A bare threshold alternates " +
                "here, and the swap is visible.",
        )
    }

    @Test
    fun leavingTheBandSwitchesToTheCoarserLevel() {
        val group = twoLevelGroup()
        group.selectLevel(50f)

        // 110 is exactly the band edge at 10%; 110.1 is past it.
        assertSame(group.levels[0], group.selectLevel(110f), "The band's own edge still holds.")
        assertSame(group.levels[1], group.selectLevel(110.1f))
        assertEquals(1, group.activeLevel)
    }

    @Test
    fun comingBackSwitchesOnlyOnceTheDistanceIsWellInside() {
        val group = twoLevelGroup()
        group.selectLevel(500f)

        // Mirrors the outward crossing: back across 100m is not enough, reaching 90m is.
        assertSame(group.levels[1], group.selectLevel(99f), "Still the far level just inside the threshold.")
        assertSame(group.levels[1], group.selectLevel(90.1f))
        assertSame(group.levels[0], group.selectLevel(90f), "The lower edge is where it hands back.")
    }

    @Test
    fun aTeleportLandsOnTheRightLevelRatherThanSteppingThroughThem() {
        val group = threeLevelGroup()
        group.selectLevel(5f)

        assertEquals(
            2,
            group.let { it.selectLevel(5_000f); it.activeLevel },
            "Outside its band the plain threshold decides, so one frame is enough to reach the " +
                "coarsest level -- a level per frame would take three.",
        )
    }

    @Test
    fun zeroHysteresisIsTheBareThreshold() {
        val group = twoLevelGroup(hysteresis = 0f)

        assertSame(group.levels[0], group.selectLevel(100f))
        assertSame(group.levels[1], group.selectLevel(100.1f))
        assertSame(group.levels[0], group.selectLevel(100f), "No band, so it switches straight back.")
    }

    @Test
    fun theCoarsestLevelDrawsBeyondEveryThreshold() {
        val group = twoLevelGroup()

        assertSame(
            group.levels[1],
            group.selectLevel(50_000f),
            "LOD selects detail, it does not cull -- an entity past every threshold still draws.",
        )
    }

    private fun twoLevelGroup(hysteresis: Float = 0.1f) = LodGroup(
        listOf(
            LodLevel(fakeMesh(), fakeMaterial(), maxDistance = 100f),
            LodLevel(fakeMesh(), fakeMaterial(), maxDistance = 1_000f),
        ),
        hysteresis = hysteresis,
    )

    private fun threeLevelGroup() = LodGroup(
        listOf(
            LodLevel(fakeMesh(), fakeMaterial(), maxDistance = 10f),
            LodLevel(fakeMesh(), fakeMaterial(), maxDistance = 100f),
            LodLevel(fakeMesh(), fakeMaterial(), maxDistance = 1_000f),
        ),
    )

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
