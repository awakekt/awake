/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.enums

enum class VkPolygonMode(val value: Int) {
    VK_POLYGON_MODE_FILL(0),
    VK_POLYGON_MODE_LINE(1),
    VK_POLYGON_MODE_POINT(2),
    VK_POLYGON_MODE_FILL_RECTANGLE_NV(1000153000),
    VK_POLYGON_MODE_MAX_ENUM(0x7FFFFFFF),
}
