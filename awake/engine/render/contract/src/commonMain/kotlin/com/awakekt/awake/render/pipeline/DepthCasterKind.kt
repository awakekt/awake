/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Structural families that may need a separate depth-caster vertex path.
 *
 * A scene draw can carry all of these payloads through the generic packet, but a depth pass still
 * has to bind the matching vertex inputs and material/storage layouts. Keeping the family name in
 * the render contract makes that requirement explicit without putting scene vocabulary into the
 * hardware interface. Backends use this as a pipeline-selection key; they must never infer a
 * caster by silently drawing an incompatible prepared mesh through the ordinary depth pipeline.
 */
enum class DepthCasterKind {
    /** A non-instanced mesh whose model transform is in the material uniform block. */
    Ordinary,

    /** A static instanced mesh whose model matrix is an instance-rate vertex input. */
    Instanced,

    /** A non-instanced skinned mesh whose joint palette is in the material uniform block. */
    Skinned,

    /** An instanced skinned mesh with a per-instance storage-buffer palette. */
    SkinnedInstanced,

    /** A billboard particle batch with matrix/color/frame instance inputs. */
    Particle,

    /** A terrain heightfield or quadtree draw with a terrain-specific depth vertex path. */
    Terrain,

    /** A 2D sprite/quad draw when the 2D domain participates in depth or shadow casting. */
    Sprite,

    /** A 2D tilemap draw when the tilemap domain participates in depth or shadow casting. */
    Tilemap,
}
