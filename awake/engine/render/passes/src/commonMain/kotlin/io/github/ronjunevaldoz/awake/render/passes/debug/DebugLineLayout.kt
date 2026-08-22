// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes.debug

import io.github.ronjunevaldoz.awake.core.geometry.GpuDataShape
import io.github.ronjunevaldoz.awake.core.geometry.VertexAttribute
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.core.geometry.VertexSemantic

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
}
