/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/**
 * One problem with a [ShaderDocument].
 *
 * @property path Where it is, from the document's root: `fragment.statements[2].value.args[0]`, or
 * `parameters.tint` for a parameter, or an empty string for the document as a whole.
 * @property message What is wrong, naming the parameter, texture or local involved.
 */
data class ShaderDocumentIssue(
    val path: String,
    val message: String,
) {
    override fun toString(): String = if (path.isEmpty()) message else "$path: $message"
}

/**
 * A [ShaderDocument] that cannot be used, with every problem found in it.
 *
 * @property issues The problems, in the order found.
 * @param cause The decoding error behind the first issue, when a parse failed.
 */
class ShaderDocumentException(
    val issues: List<ShaderDocumentIssue>,
    cause: Throwable? = null,
) : IllegalArgumentException(
    "Shader document rejected:\n" + issues.joinToString("\n") { "  $it" },
    cause,
)
