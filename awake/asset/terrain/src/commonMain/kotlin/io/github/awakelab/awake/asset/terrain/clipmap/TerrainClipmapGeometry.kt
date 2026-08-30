/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain.clipmap

import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat

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
    fun extentForLevel(level: Int): Float {
        return (ringResolution - 1) * spacingForLevel(level)
    }
}

/**
 * Generates Geometry Clipmap concentric ring meshes with zero CPU elevation bake.
 *
 * Vertices are generated on the unit/local grid $(X, Z)$ with $Y = 0$, unit normals $(0, 1, 0)$,
 * and normalized texture coordinates $(U, V)$ suitable for GPU vertex texture displacement.
 */
object TerrainClipmapGeometry {

    /** Red channel of the colour attribute: Pos(3) + Normal(3) puts it at 6. */
    private const val COLOR_OFFSET = 6

    private const val STRIDE = 11 // VertexFormat.PositionNormalColorUv: Pos(3) + Normal(3) + Color(3) + UV(2)

    /**
     * Builds the solid $(N \times N)$ Level 0 core grid centered at local origin $(0, 0)$.
     */
    fun buildCoreMesh(config: TerrainClipmapConfig): MeshGeometry {
        val n = config.ringResolution
        val spacing = config.baseSpacing
        val halfExtent = (n - 1) * spacing * 0.5f

        val vertexCount = n * n
        val vertices = FloatArray(vertexCount * STRIDE)
        var vCursor = 0

        for (z in 0 until n) {
            val localZ = z * spacing - halfExtent
            val v = z.toFloat() / (n - 1).toFloat()
            for (x in 0 until n) {
                val localX = x * spacing - halfExtent
                val u = x.toFloat() / (n - 1).toFloat()

                // Position (X, Y, Z)
                vertices[vCursor++] = localX
                vertices[vCursor++] = 0.0f
                vertices[vCursor++] = localZ
                // Normal (NX, NY, NZ)
                vertices[vCursor++] = 0.0f
                vertices[vCursor++] = 1.0f
                vertices[vCursor++] = 0.0f
                // Color (R, G, B)
                vertices[vCursor++] = 1.0f
                vertices[vCursor++] = 1.0f
                vertices[vCursor++] = 1.0f
                // UV (U, V)
                vertices[vCursor++] = u
                vertices[vCursor++] = v
            }
        }

        val quadCount = (n - 1) * (n - 1)
        val indices = IntArray(quadCount * 6)
        var iCursor = 0

        for (z in 0 until n - 1) {
            for (x in 0 until n - 1) {
                val topLeft = z * n + x
                val topRight = topLeft + 1
                val bottomLeft = topLeft + n
                val bottomRight = bottomLeft + 1

                indices[iCursor++] = topLeft
                indices[iCursor++] = bottomLeft
                indices[iCursor++] = topRight
                indices[iCursor++] = topRight
                indices[iCursor++] = bottomLeft
                indices[iCursor++] = bottomRight
            }
        }

        return MeshGeometry(vertices, indices, VertexFormat.PositionNormalColorUv)
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

        val vertexCount = n * n
        val vertices = FloatArray(vertexCount * STRIDE)
        var vCursor = 0

        for (z in 0 until n) {
            val localZ = z * spacing - halfExtent
            val v = z.toFloat() / (n - 1).toFloat()
            for (x in 0 until n) {
                val localX = x * spacing - halfExtent
                val u = x.toFloat() / (n - 1).toFloat()

                // Position (X, Y, Z)
                vertices[vCursor++] = localX
                vertices[vCursor++] = 0.0f
                vertices[vCursor++] = localZ
                // Normal (NX, NY, NZ)
                vertices[vCursor++] = 0.0f
                vertices[vCursor++] = 1.0f
                vertices[vCursor++] = 0.0f
                // Color (R, G, B)
                vertices[vCursor++] = 1.0f
                vertices[vCursor++] = 1.0f
                vertices[vCursor++] = 1.0f
                // UV (U, V)
                vertices[vCursor++] = u
                vertices[vCursor++] = v
            }
        }

        // Emit quads outside the inner hole [innerStart, innerEnd)
        val maxQuads = (n - 1) * (n - 1) - (n / 2) * (n / 2)
        val indices = IntArray(maxQuads * 6)
        var iCursor = 0

        for (z in 0 until n - 1) {
            for (x in 0 until n - 1) {
                // Check if this quad is inside the inner hole
                val isInsideHole = (x >= innerStart && x < innerEnd) && (z >= innerStart && z < innerEnd)
                if (isInsideHole) continue

                val topLeft = z * n + x
                val topRight = topLeft + 1
                val bottomLeft = topLeft + n
                val bottomRight = bottomLeft + 1

                indices[iCursor++] = topLeft
                indices[iCursor++] = bottomLeft
                indices[iCursor++] = topRight
                indices[iCursor++] = topRight
                indices[iCursor++] = bottomLeft
                indices[iCursor++] = bottomRight
            }
        }

        return MeshGeometry(vertices, indices.copyOf(iCursor), VertexFormat.PositionNormalColorUv)
    }

    /**
     * Builds the entire set of Geometry Clipmap concentric ring meshes for a given configuration.
     * Index 0 is the Level 0 core mesh, and index 1..ringCount-1 are the annular rings.
     */
    fun buildAllClipmapMeshes(config: TerrainClipmapConfig = TerrainClipmapConfig()): List<MeshGeometry> {
        return buildList {
            add(buildCoreMesh(config))
            for (level in 1 until config.ringCount) {
                add(buildRingMesh(level, config))
            }
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
