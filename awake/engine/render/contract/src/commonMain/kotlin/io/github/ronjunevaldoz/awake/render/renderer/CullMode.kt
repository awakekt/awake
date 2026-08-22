// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.renderer

/** Which side of a mesh's triangles the rasterizer discards.
 *
 * [None] draws both sides -- required for genuinely double-sided geometry (leaves, flat panes),
 * but a solid mesh pays double fragment cost and can z-fight its own back faces when viewed
 * from underneath. [Back] is the normal choice for solid, correctly-wound meshes. [Front] is
 * for intentionally inside-out geometry; unused today.
 *
 * Requires consistent triangle winding -- an incorrectly wound mesh will render inside-out. */
enum class CullMode {
    None,
    Back,
    Front,
}
