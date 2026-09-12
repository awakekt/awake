/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

/** glTF 2.0 material alpha behavior, kept in the asset module until scene conversion. */
enum class GltfAlphaMode {
    OPAQUE,
    MASK,
    BLEND,
}
