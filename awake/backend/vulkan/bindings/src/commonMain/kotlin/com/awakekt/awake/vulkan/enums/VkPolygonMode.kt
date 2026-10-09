/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How polygons are rasterized (`VkPolygonMode`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPolygonMode(val value: Int) {
    /** Polygons are filled. */
    VK_POLYGON_MODE_FILL(0),

    /** Polygon edges are drawn as lines; requires the `fillModeNonSolid` feature. */
    VK_POLYGON_MODE_LINE(1),

    /** Polygon vertices are drawn as points; requires the `fillModeNonSolid` feature. */
    VK_POLYGON_MODE_POINT(2),

    /** Polygons are filled as their bounding rectangle (NVIDIA extension). */
    VK_POLYGON_MODE_FILL_RECTANGLE_NV(1000153000),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_POLYGON_MODE_MAX_ENUM(0x7FFFFFFF),
}
