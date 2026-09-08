/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.terrain

import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the goal post is planted in the terrain rather than buried under it or floating over it.
 *
 * The post is authored in the scene document at a fixed height and the ground it stands on is
 * generated, so the two agree only by having been worked out together -- the same trap
 * [HeightfieldSceneFramingTest] exists for. And the failure is quiet either way: a buried post
 * leaves the trigger invisible while it still works, and a floating one looks like a bug in
 * something else entirely.
 *
 * The terrain here is a dome, which is why the marker is a narrow post and not a pad: across a pad
 * wide enough to push a box onto, the ground under this spot rises by more than half a metre.
 */
class GoalZonePlacementTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun theGoalPostStandsOnTheGroundItMarks() = runTest {
        val document = SceneLoader.loadFromResource("assets/examples/heightfield-terrain.scene.json")
        val post = assertNotNull(
            document.nodes.firstOrNull { it.name == "goal-zone" },
            "The scene has no goal-zone node for the pickup trigger.",
        )
        val centre = post.transform.position
        val halfHeight = post.transform.scale.y * HALF
        val bottom = centre.y - halfHeight
        val top = centre.y + halfHeight

        val ground = groundHeightsAround(centre.x, centre.z)

        assertTrue(
            bottom < ground.min(),
            "The post's base sits at $bottom, above ground that reaches ${ground.min()} -- it floats.",
        )
        assertTrue(
            top > ground.max() + VISIBLE_MARGIN,
            "The post's top sits at $top, barely over ground reaching ${ground.max()} -- it is buried.",
        )
    }

    /**
     * World-space terrain heights at the samples either side of a point.
     *
     * Read from [TerrainExampleAsset] rather than restated, so this measures the ground that is
     * actually drawn and collided against; a hardcoded height would agree with the post forever
     * and with the terrain only until someone edits the heightmap.
     */
    private fun groundHeightsAround(x: Float, z: Float): List<Float> {
        val shape = TerrainExampleAsset.collisionShape
        val scale = TerrainExampleAsset.scale
        val centre = (TerrainExampleAsset.SAMPLE_COUNT - 1) * HALF
        // Centred origin: sample i sits at (i - centre) * scale, so this inverts that and takes the
        // samples on both sides of the point rather than rounding to one.
        val i = x / scale.x + centre
        val j = z / scale.z + centre
        return listOf(
            i.toInt() to j.toInt(),
            i.toInt() + 1 to j.toInt(),
            i.toInt() to j.toInt() + 1,
            i.toInt() + 1 to j.toInt() + 1,
        ).map { (sampleX, sampleZ) -> shape.heightAt(sampleX, sampleZ) * scale.y }
    }

    @Test
    fun theWaterSurfaceStandsClearOfTheGroundBeneathIt() = runTest {
        val document = SceneLoader.loadFromResource("assets/examples/heightfield-terrain.scene.json")
        val pool = assertNotNull(
            document.nodes.firstOrNull { it.name == "water" },
            "The scene has no water node for the buoyancy demonstration.",
        )
        val centre = pool.transform.position
        val surface = centre.y + pool.transform.scale.y * HALF

        val ground = groundHeightsAround(centre.x, centre.z)

        // A pool whose surface sits at the height of its own bed demonstrates nothing: a floating
        // box and a box resting on the ground come to rest at the same place, so the feature and
        // its absence look identical. The whole point is that they must not.
        assertTrue(
            surface > ground.max() + VISIBLE_MARGIN,
            "the water surface at $surface is level with ground reaching ${ground.max()}, " +
                "so floating and resting are indistinguishable",
        )
    }

    private companion object {
        const val HALF = 0.5f

        /** A post level with the ground is a post nobody can see, so clearing it is not enough. */
        const val VISIBLE_MARGIN = 0.5f
    }
}
