/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.terrain

import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The camera has to be pointed at the terrain, and the scene file cannot know where that is.
 *
 * A scene is authored in world coordinates and the mesh is placed by its own origin convention,
 * so the two agree only by having been written at the same time. When heightmaps moved from a
 * corner origin to a centred one, this camera kept framing (6, _, 6) -- which had been the middle
 * of the terrain and became its far corner. Nothing failed: the scene loaded, the mesh drew, and
 * the terrain sat off to one side of a frame nobody asserted anything about.
 */
class HeightfieldSceneFramingTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun theCameraLooksAtGroundThatExists() = runTest {
        val document = SceneLoader.loadFromResource("assets/examples/heightfield-terrain.scene.json")
        val camera = assertNotNull(
            document.nodes.firstNotNullOfOrNull { node -> node.components.filterIsInstance<SceneCamera>().firstOrNull() },
            "The scene has no camera to check.",
        )
        val bounds = assertNotNull(TerrainExampleAsset.geometry.bounds, "The terrain has no bounds.")

        // The middle half, not merely "on the map": the old camera framed (6, _, 6), which is
        // exactly the far CORNER vertex of the centred terrain and passes an inside-the-bounds
        // test while showing the terrain shoved into one edge of the frame.
        val insideX = kotlin.math.abs(camera.center.x - bounds.center.x) <= (bounds.max.x - bounds.min.x) * QUARTER
        val insideZ = kotlin.math.abs(camera.center.z - bounds.center.z) <= (bounds.max.z - bounds.min.z) * QUARTER
        assertTrue(
            insideX && insideZ,
            "The camera looks at (${camera.center.x}, ${camera.center.z}) while the terrain spans " +
                "${bounds.min.x}..${bounds.max.x} on X and ${bounds.min.z}..${bounds.max.z} on Z. " +
                "That is its rim or past it, so the terrain sits in one edge of the frame.",
        )
    }

    private companion object {
        /** Half of the middle half: the camera should look at terrain, not at its border. */
        const val QUARTER = 0.25f
    }
}
