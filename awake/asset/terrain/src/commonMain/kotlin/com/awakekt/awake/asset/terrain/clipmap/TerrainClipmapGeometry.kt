/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.clipmap

import com.awakekt.awake.core.geometry.InterleavedVertices
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.gridTriangleIndices

/**
 * Configuration for Geometry Clipmap terrain mesh generation.
 *
 * @property ringCount Total number of concentric nested LOD rings (Level 0 core + rings 1..ringCount-1).
 * @property ringResolution Number of grid vertices along one side of each ring (must be an even integer >= 16).
 * @property baseSpacing World-space meter spacing between adjacent grid samples at Level 0.
 */
data class TerrainClipmapConfig(
    val ringCount: Int = 6,
    val ringResolution: Int = 64,
    val baseSpacing: Float = 2.0f,
) {
    init {
        require(ringCount >= 2) { "Clipmap ringCount must be at least 2; was $ringCount." }
        require(ringResolution >= 16 && ringResolution % 2 == 0) {
            "Clipmap ringResolution must be an even integer >= 16; was $ringResolution."
        }
        require(baseSpacing > 0f && baseSpacing.isFinite()) {
            "baseSpacing must be a positive finite float; was $baseSpacing."
        }
    }

    /** World-space side extent of the inner Level 0 core grid in meters. */
    val coreExtent: Float get() = (ringResolution - 1) * baseSpacing

    /** World-space meter spacing for a specific LOD level. */
    fun spacingForLevel(level: Int): Float {
        require(level in 0 until ringCount) { "Level $level out of bounds [0, $ringCount)." }
        return baseSpacing * (1 shl level).toFloat()
    }

    /** World-space side extent of ring level [level] in meters. */
    fun extentForLevel(level: Int): Float = (ringResolution - 1) * spacingForLevel(level)
}

/**
 * Generates Geometry Clipmap concentric ring meshes with zero CPU elevation bake.
 *
 * Vertices are generated on the unit/local grid $(X, Z)$ with $Y = 0$, unit normals $(0, 1, 0)$,
 * and normalized texture coordinates $(U, V)$ suitable for GPU vertex texture displacement.
 */
object TerrainClipmapGeometry {

    private val FORMAT = VertexFormat.PositionNormalColorUv

    /** Asked of the format rather than restated: both used to be hand-maintained constants. */
    private val COLOR_OFFSET = FORMAT.floatOffsetOf(VertexSemantic.Color)
    private val STRIDE = FORMAT.strideFloats

    /**
     * A flat, level, white `n` x `n` grid centred on the local origin, with UVs across the whole.
     *
     * The core mesh and every ring build the identical vertex; they differ only in [spacing] and
     * in which cells they emit indices for. Two copies of this loop is how those two could have
     * drifted apart without any test noticing.
     */
    private fun flatGridVertices(n: Int, spacing: Float, halfExtent: Float): InterleavedVertices {
        val vertices = InterleavedVertices(FORMAT, n * n)
        val lastIndex = (n - 1).toFloat()
        for (z in 0 until n) {
            for (x in 0 until n) {
                val vertex = z * n + x
                vertices.put(
                    vertex,
                    VertexSemantic.Position,
                    x * spacing - halfExtent,
                    0f,
                    z * spacing - halfExtent,
                )
                vertices.put(vertex, VertexSemantic.Uv, x / lastIndex, z / lastIndex)
            }
        }
        // Constant across the grid, so written once per attribute rather than once per vertex.
        vertices.fill(VertexSemantic.Normal, 0f, 1f, 0f)
        vertices.fill(VertexSemantic.Color, 1f, 1f, 1f)
        return vertices
    }

    /**
     * Builds the solid $(N \times N)$ Level 0 core grid centered at local origin $(0, 0)$.
     */
    fun buildCoreMesh(config: TerrainClipmapConfig): MeshGeometry {
        val n = config.ringResolution
        val spacing = config.baseSpacing
        val halfExtent = (n - 1) * spacing * 0.5f

        val vertices = flatGridVertices(n, spacing, halfExtent)

        return vertices.build(gridTriangleIndices(n, n))
    }

    /**
     * Builds an annular concentric ring mesh for level [level] >= 1, culling the inner
     * $(N/2 \times N/2)$ hole where the higher-resolution interior ring resides.
     */
    fun buildRingMesh(level: Int, config: TerrainClipmapConfig): MeshGeometry {
        require(level in 1 until config.ringCount) {
            "Ring level must be in [1, ${config.ringCount}); was $level."
        }

        val n = config.ringResolution
        val spacing = config.spacingForLevel(level)
        val halfExtent = (n - 1) * spacing * 0.5f

        val innerStart = n / 4
        val innerEnd = innerStart + (n / 2) // Inner hole bounds in grid units

        val vertices = flatGridVertices(n, spacing, halfExtent)

        // A ring is the same grid with its middle removed, which is exactly what the shared
        // builder's cell filter is for.
        val indices = gridTriangleIndices(n, n) { x, z ->
            !((x >= innerStart && x < innerEnd) && (z >= innerStart && z < innerEnd))
        }

        return vertices.build(indices)
    }

    /**
     * Builds the entire set of Geometry Clipmap concentric ring meshes for a given configuration.
     * Index 0 is the Level 0 core mesh, and index 1..ringCount-1 are the annular rings.
     */
    fun buildAllClipmapMeshes(config: TerrainClipmapConfig = TerrainClipmapConfig()): List<MeshGeometry> = buildList {
        add(buildCoreMesh(config))
        for (level in 1 until config.ringCount) {
            add(buildRingMesh(level, config))
        }
    }

    /**
     * Every level of [buildAllClipmapMeshes] concatenated into one mesh, each vertex tagged with
     * the ring level it came from.
     *
     * One mesh rather than a list because a clipmap is drawn against a single uniform block: the
     * levels are distinct geometry, so instancing cannot cover them, and one draw per level would
     * need one block per level (see `TerrainUniformLayout`). Merging makes it one draw that reads
     * each vertex's own ring parameters out of an indexed array.
     *
     * The tag goes in the colour channel's red component. Every clipmap vertex carries a constant
     * white that no shader reads, so this costs no vertex format change -- but it does mean this
     * geometry is only meaningful to a shader that knows the convention, which is why the two are
     * described together here and in `AslTerrainShader`.
     *
     * Index buffers are rebased as each level is appended; a level's indices are local to its own
     * vertex block.
     */
    fun buildMergedClipmapMesh(config: TerrainClipmapConfig = TerrainClipmapConfig()): MeshGeometry {
        val levels = buildAllClipmapMeshes(config)
        val vertices = FloatArray(levels.sumOf { it.vertices.size })
        val indices = IntArray(levels.sumOf { it.indices.size })
        var vertexCursor = 0
        var indexCursor = 0
        var baseVertex = 0

        levels.forEachIndexed { level, mesh ->
            mesh.vertices.copyInto(vertices, vertexCursor)
            var tagCursor = vertexCursor + COLOR_OFFSET
            while (tagCursor < vertexCursor + mesh.vertices.size) {
                vertices[tagCursor] = level.toFloat()
                tagCursor += STRIDE
            }
            mesh.indices.forEach { index ->
                indices[indexCursor++] = index + baseVertex
            }
            baseVertex += mesh.vertices.size / STRIDE
            vertexCursor += mesh.vertices.size
        }
        return MeshGeometry(vertices, indices, VertexFormat.PositionNormalColorUv)
    }
}
