/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

/** A diagnostic a particular showcase deliberately exposes, or an engine-wide control. */
enum class ShowcaseDebugOption(val isGlobal: Boolean = false) {
    NavGrid,
    Corridor,
    Bounds(isGlobal = true),
    InstanceBounds(isGlobal = true),
    ShadowCascades(isGlobal = true),
    CascadedShadows(isGlobal = true),
    Shadows(isGlobal = true),
    Occlusion(isGlobal = true),
    Lights(isGlobal = true),
    Colliders,
    TerrainProbes,
    Wireframe(isGlobal = true),
}
