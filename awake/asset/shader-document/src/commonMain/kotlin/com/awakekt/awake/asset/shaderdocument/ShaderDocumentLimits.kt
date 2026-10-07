/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/**
 * How large and how costly a [ShaderDocument] may be. A document beyond any limit is rejected before
 * it is parsed or compiled, so a published project cannot ship a shader that stalls a GPU.
 *
 * The [Default] values are provisional: they bound the cost without having been measured on a slow
 * device. A weighted count multiplies each node by the iterations of the loops around it.
 */
data class ShaderDocumentLimits(
    /** The document's size in UTF-8 bytes. */
    val maxBytes: Int = 65_536,
    /** How deep objects and arrays may nest in the JSON, checked before parsing. */
    val maxJsonNesting: Int = 128,
    /** How deep expressions may nest. */
    val maxExpressionDepth: Int = 32,
    /** Expressions and statements in the document, each counted once. */
    val maxNodes: Int = 512,
    /** Expressions and statements, each counted once per time it runs for a pixel or a vertex. */
    val maxWeightedCost: Int = 4_096,
    /** Iterations of one loop. */
    val maxLoopIterations: Int = 32,
    /** Loops inside loops: 2 allows one loop inside another. */
    val maxLoopNesting: Int = 2,
    /** Texture reads per pixel, each counted once per time it runs. */
    val maxWeightedSamples: Int = 16,
    /** Parameters. */
    val maxParameters: Int = 16,
    /** Textures, at most 4: a document binds them to four fixed slots. */
    val maxTextures: Int = 4,
    /** Locals and loop counters across the document. */
    val maxLocals: Int = 64,
    /** Quads along each side of a plane. */
    val maxPlaneSegments: Int = 128,
) {
    /** The values in use while none has been measured. */
    companion object {
        /** The provisional limits: see each property for what it bounds. */
        val Default: ShaderDocumentLimits = ShaderDocumentLimits()
    }
}
