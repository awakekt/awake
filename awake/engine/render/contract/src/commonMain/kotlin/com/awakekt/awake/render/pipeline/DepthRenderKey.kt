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
 */
data class DepthRenderKey(
    val kind: DepthCasterKind,
    val alphaMode: AlphaMode = AlphaMode.Opaque,
)
