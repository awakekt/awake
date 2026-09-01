/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.terrain

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.asset.terrain.toPositionNormalColorMesh
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.HeightFieldShape

/**
 * One sample-owned heightfield used by the engine showcase's terrain example. [collisionShape] and [geometry]
 * deliberately read the same row-major values, so the visual surface and a future static Jolt
 * body use the same corner-origin coordinates instead of two independently authored terrains.
 *
 * The showcase currently has no `PhysicsWorld` lifecycle, so this object does not attach an inert
 * [io.github.awakelab.awake.scene.physics.PhysicsBody]. A game that installs a
 * physics world can pass [collisionShape] to it directly at the same entity position.
 */
internal object TerrainExampleAsset {
    const val SAMPLE_COUNT = 9

    private val HEIGHT_SAMPLES = FloatArray(SAMPLE_COUNT * SAMPLE_COUNT) { index ->
        val x = index % SAMPLE_COUNT
        val z = index / SAMPLE_COUNT
        val dx = x - (SAMPLE_COUNT - 1) * 0.5f
        val dz = z - (SAMPLE_COUNT - 1) * 0.5f
        1.8f - (dx * dx + dz * dz) * 0.11f + if ((x + z) % 3 == 0) 0.25f else 0f
    }

    val heightmap = Heightmap(
        samples = HEIGHT_SAMPLES,
        width = SAMPLE_COUNT,
        depth = SAMPLE_COUNT,
        scale = Vec3f(1.5f, 0.65f, 1.5f),
    )
    val scale: Vec3f get() = heightmap.scale
    val collisionShape = HeightFieldShape(heightmap.copySamples(), SAMPLE_COUNT, heightmap.scale)
    val geometry: MeshGeometry = heightmap.toPositionNormalColorMesh { _, _, height ->
        val tone = ((height + 1f) / 3f).coerceIn(0f, 1f)
        Color(
            r = 0.12f + tone * 0.18f,
            g = 0.28f + tone * 0.42f,
            b = 0.08f + tone * 0.12f,
        )
    }
}
