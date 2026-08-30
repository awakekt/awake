/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes.debug

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.geometry.VertexAttribute
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.geometry.VertexSemantic

/**
 * World-space debug lines -- frustum wireframes, bounds boxes, light gizmos.
 *
 * Scene-pass geometry, not UI: these depth-test against real meshes and are recorded by the
 * opaque feature, not the overlay pass. This layout and its writers lived in the `ui` package
 * for a while because that is where the vertex writers happened to be, which made the package
 * name inaccurate in both directions.
 */
object DebugLineLayout {
    /** Position (vec3) + colour (vec4) = 7 floats, 28 bytes. */
    val Format = VertexFormat(
        listOf(
            VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec3, location = 0),
            VertexAttribute(VertexSemantic.Color, GpuDataShape.Vec4, location = 1),
        ),
    )

    /** Derived from [Format], never hand-counted. */
    val FLOATS_PER_VERTEX: Int = Format.strideBytes / Float.SIZE_BYTES

    /** Two endpoints per segment -- `LINE_LIST` topology, so no index buffer. */
    const val VERTICES_PER_LINE = 2

    /**
     * Where a backend's per-slot line buffer starts. A starting size, not a limit: see
     * [grownVertexCapacity].
     *
     * Enough for the fixed-size producers that are always on -- a frustum is 12 lines, a bounds
     * box 12 -- so the common case never reallocates.
     */
    const val INITIAL_LINES = 64

    /**
     * Growth stops here and throws instead.
     *
     * A buffer that grows silently forever turns a producer bug into an out-of-memory a long way
     * from its cause. This keeps the loud failure -- the one that caught a navigation grid asking
     * for 74 lines against a fixed 64 -- at a level no honest debug visualisation reaches.
     */
    const val MAX_LINES_CEILING = 65_536

    /**
     * The capacity a buffer holding [currentVertices] must grow to in order to fit
     * [neededVertices], doubling until it fits.
     *
     * Lives here rather than in each backend for the reason [FLOATS_PER_VERTEX] does: Vulkan and
     * WebGPU each own a `LineMesh`, and a growth rule written twice drifts exactly the way the
     * rounded-quad stride did. Allocation stays per-backend; the arithmetic deciding *how much*
     * does not.
     */
    fun grownVertexCapacity(currentVertices: Int, neededVertices: Int): Int {
        val neededLines = (neededVertices + VERTICES_PER_LINE - 1) / VERTICES_PER_LINE
        require(neededLines <= MAX_LINES_CEILING) {
            "Debug line count ($neededLines) exceeds the ceiling of $MAX_LINES_CEILING lines. " +
                "This is a runaway producer, not a buffer to grow."
        }
        var capacity = maxOf(currentVertices, VERTICES_PER_LINE)
        while (capacity < neededVertices) capacity *= 2
        return capacity
    }
}
