/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How vertices are assembled into primitives (`VkPrimitiveTopology`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPrimitiveTopology(val value: Int) {
    /** Each vertex is a point. */
    VK_PRIMITIVE_TOPOLOGY_POINT_LIST(0),

    /** Each pair of vertices is a separate line. */
    VK_PRIMITIVE_TOPOLOGY_LINE_LIST(1),

    /** Each vertex after the first extends a connected line. */
    VK_PRIMITIVE_TOPOLOGY_LINE_STRIP(2),

    /** Each triple of vertices is a separate triangle. */
    VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST(3),

    /** Each vertex after the second forms a triangle with the previous two. */
    VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP(4),

    /** Each vertex after the second forms a triangle with the first vertex and the previous one. */
    VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN(5),

    /** Lines with adjacency information for a geometry shader, four vertices per line. */
    VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY(6),

    /** A line strip with adjacency information for a geometry shader. */
    VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY(7),

    /** Triangles with adjacency information for a geometry shader, six vertices per triangle. */
    VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST_WITH_ADJACENCY(8),

    /** A triangle strip with adjacency information for a geometry shader. */
    VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP_WITH_ADJACENCY(9),

    /** Patches for the tessellation stages; the patch size is set by the tessellation state. */
    VK_PRIMITIVE_TOPOLOGY_PATCH_LIST(10),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_PRIMITIVE_TOPOLOGY_MAX_ENUM(0x7FFFFFFF),
}
