/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry.generate

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat

/**
 * Scoped builder for generating procedural [MeshGeometry] instances.
 */
class MeshGenerateScope {
    private var geometry: MeshGeometry? = null

    /**
     * Builds and validates the procedural [MeshGeometry].
     *
     * @return The constructed [MeshGeometry].
     */
    internal fun build(): MeshGeometry =
        requireNotNull(geometry) { "generate { } produced no geometry -- call cube()/plane() inside the block." }

    /**
     * Generates a unit or scaled cube mesh with 24 vertices and normals per face.
     *
     * @param size The edge length of the cube.
     * @param colored If true, generates distinct face colors; otherwise uniform white.
     * @param format The vertex layout format. Defaults to [VertexFormat.PositionNormalColor].
     */
    fun cube(
        size: Float = 1f,
        colored: Boolean = false,
        format: VertexFormat = VertexFormat.PositionNormalColor,
    ) {
        geometry = buildCubeGeometry(size = size, colored = colored, format = format)
    }

    /**
     * Generates a flat ground or quad plane mesh.
     *
     * @param size The full width and depth of the plane.
     * @param colored If true, uses a subtle ground tone; otherwise uniform white.
     * @param format The vertex layout format. Defaults to [VertexFormat.PositionNormalColor].
     */
    fun plane(
        size: Float = 10f,
        colored: Boolean = false,
        format: VertexFormat = VertexFormat.PositionNormalColor,
    ) {
        geometry = buildPlaneGeometry(size = size, colored = colored, format = format)
    }

    /**
     * Generates a UV sphere mesh.
     *
     * @param radius The radius of the sphere.
     * @param rings The number of latitude subdivisions.
     * @param sectors The number of longitude subdivisions.
     * @param colored If true, generates distinctive face colors; otherwise uniform white.
     * @param format The vertex layout format. Defaults to [VertexFormat.PositionNormalColor].
     */
    fun sphere(
        radius: Float = 0.5f,
        rings: Int = 16,
        sectors: Int = 16,
        colored: Boolean = false,
        format: VertexFormat = VertexFormat.PositionNormalColor,
    ) {
        geometry = buildSphereGeometry(
            radius = radius,
            rings = rings,
            sectors = sectors,
            colored = colored,
            format = format,
        )
    }
}

/**
 * Entry point for constructing procedural [MeshGeometry].
 *
 * @param block Configuration lambda defining the procedural primitive.
 * @return The generated [MeshGeometry].
 */
fun generate(block: MeshGenerateScope.() -> Unit): MeshGeometry =
    MeshGenerateScope().apply(block).build()

private fun buildCubeGeometry(
    size: Float,
    colored: Boolean,
    format: VertexFormat,
): MeshGeometry {
    val h = size * 0.5f
    // 24 vertices (4 per face x 6 faces), 36 indices
    val vertices = if (colored) {
        floatArrayOf(
            // Back face (-Z)
            -h, -h, -h, 0f, 0f, -1f, 0f, 0f, 0f,
            h, -h, -h, 0f, 0f, -1f, 1f, 0f, 0f,
            h, h, -h, 0f, 0f, -1f, 1f, 1f, 0f,
            -h, h, -h, 0f, 0f, -1f, 0f, 1f, 0f,
            // Front face (+Z)
            -h, -h, h, 0f, 0f, 1f, 0f, 0f, 1f,
            h, -h, h, 0f, 0f, 1f, 1f, 0f, 1f,
            h, h, h, 0f, 0f, 1f, 1f, 1f, 1f,
            -h, h, h, 0f, 0f, 1f, 0f, 1f, 1f,
            // Left face (-X)
            -h, -h, -h, -1f, 0f, 0f, 0f, 0f, 0f,
            -h, h, -h, -1f, 0f, 0f, 0f, 1f, 0f,
            -h, h, h, -1f, 0f, 0f, 0f, 1f, 1f,
            -h, -h, h, -1f, 0f, 0f, 0f, 0f, 1f,
            // Right face (+X)
            h, -h, -h, 1f, 0f, 0f, 1f, 0f, 0f,
            h, -h, h, 1f, 0f, 0f, 1f, 0f, 1f,
            h, h, h, 1f, 0f, 0f, 1f, 1f, 1f,
            h, h, -h, 1f, 0f, 0f, 1f, 1f, 0f,
            // Bottom face (-Y)
            -h, -h, -h, 0f, -1f, 0f, 0f, 0f, 0f,
            -h, -h, h, 0f, -1f, 0f, 0f, 0f, 1f,
            h, -h, h, 0f, -1f, 0f, 1f, 0f, 1f,
            h, -h, -h, 0f, -1f, 0f, 1f, 0f, 0f,
            // Top face (+Y)
            -h, h, -h, 0f, 1f, 0f, 0f, 1f, 0f,
            h, h, -h, 0f, 1f, 0f, 1f, 1f, 0f,
            h, h, h, 0f, 1f, 0f, 1f, 1f, 1f,
            -h, h, h, 0f, 1f, 0f, 0f, 1f, 1f,
        )
    } else {
        floatArrayOf(
            // Back face (-Z)
            -h, -h, -h, 0f, 0f, -1f, 1f, 1f, 1f,
            h, -h, -h, 0f, 0f, -1f, 1f, 1f, 1f,
            h, h, -h, 0f, 0f, -1f, 1f, 1f, 1f,
            -h, h, -h, 0f, 0f, -1f, 1f, 1f, 1f,
            // Front face (+Z)
            -h, -h, h, 0f, 0f, 1f, 1f, 1f, 1f,
            h, -h, h, 0f, 0f, 1f, 1f, 1f, 1f,
            h, h, h, 0f, 0f, 1f, 1f, 1f, 1f,
            -h, h, h, 0f, 0f, 1f, 1f, 1f, 1f,
            // Left face (-X)
            -h, -h, -h, -1f, 0f, 0f, 1f, 1f, 1f,
            -h, h, -h, -1f, 0f, 0f, 1f, 1f, 1f,
            -h, h, h, -1f, 0f, 0f, 1f, 1f, 1f,
            -h, -h, h, -1f, 0f, 0f, 1f, 1f, 1f,
            // Right face (+X)
            h, -h, -h, 1f, 0f, 0f, 1f, 1f, 1f,
            h, -h, h, 1f, 0f, 0f, 1f, 1f, 1f,
            h, h, h, 1f, 0f, 0f, 1f, 1f, 1f,
            h, h, -h, 1f, 0f, 0f, 1f, 1f, 1f,
            // Bottom face (-Y)
            -h, -h, -h, 0f, -1f, 0f, 1f, 1f, 1f,
            -h, -h, h, 0f, -1f, 0f, 1f, 1f, 1f,
            h, -h, h, 0f, -1f, 0f, 1f, 1f, 1f,
            h, -h, -h, 0f, -1f, 0f, 1f, 1f, 1f,
            // Top face (+Y)
            -h, h, -h, 0f, 1f, 0f, 1f, 1f, 1f,
            h, h, -h, 0f, 1f, 0f, 1f, 1f, 1f,
            h, h, h, 0f, 1f, 0f, 1f, 1f, 1f,
            -h, h, h, 0f, 1f, 0f, 1f, 1f, 1f,
        )
    }
    val indices = intArrayOf(
        0, 1, 2, 2, 3, 0,
        4, 5, 6, 6, 7, 4,
        8, 9, 10, 10, 11, 8,
        12, 13, 14, 14, 15, 12,
        16, 17, 18, 18, 19, 16,
        20, 21, 22, 22, 23, 20,
    )
    return MeshGeometry(vertices = vertices, indices = indices, format = format)
}

private fun buildPlaneGeometry(
    size: Float,
    colored: Boolean,
    format: VertexFormat,
): MeshGeometry {
    val h = size * 0.5f
    val (r, g, b) = if (colored) Triple(0.5f, 0.5f, 0.55f) else Triple(1f, 1f, 1f)
    val vertices = floatArrayOf(
        -h, 0f, -h, 0f, 1f, 0f, r, g, b,
        h, 0f, -h, 0f, 1f, 0f, r, g, b,
        h, 0f, h, 0f, 1f, 0f, r, g, b,
        -h, 0f, h, 0f, 1f, 0f, r, g, b,
    )
    val indices = intArrayOf(0, 1, 2, 2, 3, 0)
    return MeshGeometry(vertices = vertices, indices = indices, format = format)
}

@Suppress("MagicNumber", "LongMethod")
private fun buildSphereGeometry(
    radius: Float,
    rings: Int,
    sectors: Int,
    colored: Boolean,
    format: VertexFormat,
): MeshGeometry {
    val vertexCount = (rings + 1) * (sectors + 1)
    val floatStride = format.strideFloats
    val vertices = FloatArray(vertexCount * floatStride)
    val (rColor, gColor, bColor) = if (colored) Triple(0.85f, 0.85f, 0.95f) else Triple(1f, 1f, 1f)

    var offset = 0
    val pi = kotlin.math.PI.toFloat()
    for (r in 0..rings) {
        val theta = (pi * r) / rings
        val sinTheta = kotlin.math.sin(theta)
        val cosTheta = kotlin.math.cos(theta)

        for (s in 0..sectors) {
            val phi = (2f * pi * s) / sectors
            val sinPhi = kotlin.math.sin(phi)
            val cosPhi = kotlin.math.cos(phi)

            val nx = sinTheta * cosPhi
            val ny = cosTheta
            val nz = sinTheta * sinPhi

            val px = radius * nx
            val py = radius * ny
            val pz = radius * nz

            vertices[offset++] = px
            vertices[offset++] = py
            vertices[offset++] = pz
            vertices[offset++] = nx
            vertices[offset++] = ny
            vertices[offset++] = nz
            vertices[offset++] = rColor
            vertices[offset++] = gColor
            vertices[offset++] = bColor
        }
    }

    val indexCount = rings * sectors * 6
    val indices = IntArray(indexCount)
    var indexOffset = 0
    for (r in 0 until rings) {
        for (s in 0 until sectors) {
            val first = r * (sectors + 1) + s
            val second = first + sectors + 1

            indices[indexOffset++] = first
            indices[indexOffset++] = second
            indices[indexOffset++] = first + 1

            indices[indexOffset++] = second
            indices[indexOffset++] = second + 1
            indices[indexOffset++] = first + 1
        }
    }

    return MeshGeometry(vertices = vertices, indices = indices, format = format)
}
