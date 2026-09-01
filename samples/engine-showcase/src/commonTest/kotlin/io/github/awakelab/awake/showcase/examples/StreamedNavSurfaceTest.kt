/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An agent stands on the ground that is drawn, not on the formula it was drawn from.
 *
 * The streamed terrain mesh is [StreamedNavTerrain.heightAt] sampled every metre and interpolated
 * across each quad. Settling an agent with the continuous function instead puts it on a different
 * surface -- one that curves between the facets and steps a full pillar height at a pillar edge.
 * The camera in this example is bolted to that agent with no smoothing, so the difference is not
 * a subtle sinking: it is the whole frame shaking as it walks.
 */
class StreamedNavSurfaceTest {

    @Test
    fun theSettledHeightMatchesTheDrawnFacet() {
        var worst = 0f
        var worstAt = 0f to 0f
        walk { x, z ->
            val drawn = facetHeight(x, z)
            val settled = StreamedNavTerrain.surfaceAt(x, z, SAMPLE_SIZE)
            if (abs(drawn - settled) > worst) {
                worst = abs(drawn - settled)
                worstAt = x to z
            }
        }

        assertTrue(
            worst < MATCH_TOLERANCE,
            "Settled ${worst}m off the drawn surface at $worstAt. The agent is floating over or " +
                "sunk into ground the player can see, and the camera rides the agent.",
        )
    }

    @Test
    fun walkingDoesNotStepTheAgentVertically() {
        var biggest = 0f
        var biggestAt = 0f
        var previous = StreamedNavTerrain.surfaceAt(START_X, LANE_Z, SAMPLE_SIZE)
        var x = START_X
        while (x < START_X + WALK_LENGTH) {
            x += STEP
            val height = StreamedNavTerrain.surfaceAt(x, LANE_Z, SAMPLE_SIZE)
            if (abs(height - previous) > biggest) {
                biggest = abs(height - previous)
                biggestAt = x
            }
            previous = height
        }

        assertTrue(
            biggest < STEP_TOLERANCE,
            "The ground moved ${biggest}m under the agent in one ${STEP}m step, at x=$biggestAt. " +
                "A step that size in a frame is the camera jumping, and it is what sampling the " +
                "raw height function produced at every pillar edge.",
        )
    }

    /** The mesh's own surface: bilinear over the lattice its vertices sit on. */
    private fun facetHeight(x: Float, z: Float): Float {
        val x0 = kotlin.math.floor(x / SAMPLE_SIZE) * SAMPLE_SIZE
        val z0 = kotlin.math.floor(z / SAMPLE_SIZE) * SAMPLE_SIZE
        val fx = (x - x0) / SAMPLE_SIZE
        val fz = (z - z0) / SAMPLE_SIZE
        val near = StreamedNavTerrain.heightAt(x0, z0) +
            (StreamedNavTerrain.heightAt(x0 + SAMPLE_SIZE, z0) - StreamedNavTerrain.heightAt(x0, z0)) * fx
        val far = StreamedNavTerrain.heightAt(x0, z0 + SAMPLE_SIZE) +
            (
                StreamedNavTerrain.heightAt(x0 + SAMPLE_SIZE, z0 + SAMPLE_SIZE) -
                    StreamedNavTerrain.heightAt(x0, z0 + SAMPLE_SIZE)
                ) * fx
        return near + (far - near) * fz
    }

    private fun walk(visit: (Float, Float) -> Unit) {
        var z = -WALK_LENGTH / 2f
        while (z < WALK_LENGTH / 2f) {
            var x = -WALK_LENGTH / 2f
            while (x < WALK_LENGTH / 2f) {
                visit(x, z)
                x += STEP
            }
            z += STEP
        }
    }

    private companion object {
        const val SAMPLE_SIZE = 1f
        const val STEP = 0.05f
        const val WALK_LENGTH = 20f
        const val START_X = -10f

        /** Down the open lane, where the demonstration's agents actually walk. */
        const val LANE_Z = 0f
        const val MATCH_TOLERANCE = 0.001f

        /** A 5cm step may raise the ground by the steepest facet it can cross, and no more. */
        const val STEP_TOLERANCE = 0.6f
    }
}
