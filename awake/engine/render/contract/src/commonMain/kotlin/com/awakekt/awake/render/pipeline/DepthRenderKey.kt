/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Pipeline-selection identity for a depth or shadow caster.
 *
 * The family describes vertex/storage inputs; [alphaMode] describes fragment coverage. Keeping
 * both dimensions explicit prevents backends from guessing a depth shader from a scene object or
 * accidentally treating a masked material as opaque. Blended draws intentionally have no key:
 * they do not write depth and remain in the transparent scene path.
 * @property kind Categorization of vertex attribute inputs for shadow depth generation.
 * @property alphaMode Alpha testing coverage mode for fragment discards.
 */
data class DepthRenderKey(
    /** Categorization of vertex attribute inputs for shadow depth generation. */
    val kind: DepthCasterKind,
    /** Alpha testing coverage mode for fragment discards. */
    val alphaMode: AlphaMode = AlphaMode.Opaque,
)
