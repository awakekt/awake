/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

/** glTF 2.0 material alpha behavior, kept in the asset module until scene conversion. */
enum class GltfAlphaMode {
    /** The alpha value is ignored and the rendered output is fully opaque. */
    OPAQUE,

    /** The rendered output is either fully opaque or fully transparent depending on alpha cutoff. */
    MASK,

    /** The rendered output is combined with the background using alpha blending equations. */
    BLEND,
}
