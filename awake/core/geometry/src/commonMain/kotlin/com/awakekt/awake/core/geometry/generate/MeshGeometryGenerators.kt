/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry.generate

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.MeshGeometryBuilder
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f

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
    val corners = arrayOf(
        Vec3f(-h, -h, -h),
        Vec3f(h, -h, -h),
        Vec3f(h, h, -h),
        Vec3f(-h, h, -h),
        Vec3f(-h, -h, h),
        Vec3f(h, -h, h),
        Vec3f(h, h, h),
        Vec3f(-h, h, h),
    )
    val faces = arrayOf(
        intArrayOf(0, 1, 2, 3) to Vec3f(0f, 0f, -1f),
        intArrayOf(4, 5, 6, 7) to Vec3f(0f, 0f, 1f),
        intArrayOf(0, 3, 7, 4) to Vec3f(-1f, 0f, 0f),
        intArrayOf(1, 5, 6, 2) to Vec3f(1f, 0f, 0f),
        intArrayOf(0, 4, 5, 1) to Vec3f(0f, -1f, 0f),
        intArrayOf(3, 2, 6, 7) to Vec3f(0f, 1f, 0f),
    )
    val mesh = MeshGeometryBuilder(format, vertexCount = 24)
    faces.forEachIndexed { faceIndex, (cornersForFace, normal) ->
        cornersForFace.forEachIndexed { cornerIndex, corner ->
            val vertex = faceIndex * 4 + cornerIndex
            val position = corners[corner]
            val color = if (colored) {
                Vec3f(position.x / size + 0.5f, position.y / size + 0.5f, position.z / size + 0.5f)
            } else {
                Vec3f(1f, 1f, 1f)
            }
            mesh.vertex(vertex, position, normal, color)
        }
        val base = faceIndex * 4
        if (faceIndex == 1) {
            // The +Z face uses the opposite diagonal to preserve its outward winding.
            mesh.triangle(base, base + 1, base + 2)
            mesh.triangle(base + 2, base + 3, base)
        } else {
            mesh.quad(base, base + 2, base + 1, base + 3)
        }
    }
    return mesh.build()
}

private fun buildPlaneGeometry(
    size: Float,
    colored: Boolean,
    format: VertexFormat,
): MeshGeometry {
    val h = size * 0.5f
    val (r, g, b) = if (colored) Triple(0.5f, 0.5f, 0.55f) else Triple(1f, 1f, 1f)
    val mesh = MeshGeometryBuilder(format, vertexCount = 4)
    val positions = arrayOf(
        Vec3f(-h, 0f, -h),
        Vec3f(h, 0f, -h),
        Vec3f(h, 0f, h),
        Vec3f(-h, 0f, h),
    )
    positions.forEachIndexed { index, position ->
        mesh.vertex(index, position, Vec3f.UP, Vec3f(r, g, b))
    }
    // Counter-clockwise when viewed from above (+Y), matching the declared vertex normals and
    // the back-face culling convention used by both render backends.
    mesh.quad(0, 2, 1, 3)
    return mesh.build()
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
    val mesh = MeshGeometryBuilder(format, vertexCount)
    val (rColor, gColor, bColor) = if (colored) Triple(0.85f, 0.85f, 0.95f) else Triple(1f, 1f, 1f)

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

            mesh.vertex(
                r * (sectors + 1) + s,
                Vec3f(px, py, pz),
                Vec3f(nx, ny, nz),
                Vec3f(rColor, gColor, bColor),
            )
        }
    }

    for (r in 0 until rings) {
        for (s in 0 until sectors) {
            val first = r * (sectors + 1) + s
            val second = first + sectors + 1
            mesh.triangle(first, second, first + 1)
            mesh.triangle(second, second + 1, first + 1)
        }
    }

    return mesh.build()
}
