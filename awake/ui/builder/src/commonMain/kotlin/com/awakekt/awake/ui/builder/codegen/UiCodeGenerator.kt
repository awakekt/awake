/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.codegen

import com.awakekt.awake.ui.builder.model.UiLayoutDocument
import com.awakekt.awake.ui.builder.model.UiNode

/**
 * Transpiles [UiLayoutDocument] trees into idiomatic Awake Compose Kotlin code.
 */
object UiCodeGenerator {
    fun generateKotlin(document: UiLayoutDocument): String = buildString {
        appendLine("package com.awakekt.awake.ui.generated")
        appendLine()
        appendLine("import com.awakekt.awake.compose.runtime.Composer")
        appendLine("import com.awakekt.awake.ui.shadcn.components.*")
        appendLine()
        appendLine("context(Composer)")
        appendLine("fun ${document.name}Layout() {")
        appendNode(document.rootNode, indent = "    ")
        appendLine("}")
    }

    private fun StringBuilder.appendNode(node: UiNode, indent: String) {
        when (node.type) {
            "Container.Column" -> {
                appendLine("${indent}shadcnCard(id = \"${node.id}\") {")
                node.children.forEach { child -> appendNode(child, "$indent    ") }
                appendLine("$indent}")
            }
            "Container.Row" -> {
                appendLine("${indent}shadcnButtonGroup(id = \"${node.id}\") {")
                node.children.forEach { child -> appendNode(child, "$indent    ") }
                appendLine("$indent}")
            }
            "Container.Card" -> {
                appendLine("${indent}shadcnCard(id = \"${node.id}\") {")
                node.children.forEach { child -> appendNode(child, "$indent    ") }
                appendLine("$indent}")
            }
            "Widget.ShadcnButton" -> {
                val label = node.props["label"] ?: "Button"
                appendLine("${indent}shadcnButton(id = \"${node.id}\") { text(\"$label\") }")
            }
            "Widget.ShadcnBadge" -> {
                val label = node.props["label"] ?: "Badge"
                appendLine("${indent}shadcnBadge(id = \"${node.id}\") { text(\"$label\") }")
            }
            "Widget.ShadcnInput" -> {
                val placeholder = node.props["placeholder"] ?: ""
                appendLine("${indent}shadcnInput(id = \"${node.id}\", placeholder = \"$placeholder\")")
            }
            "Widget.ShadcnSwitch" -> {
                val label = node.props["label"] ?: ""
                appendLine("${indent}shadcnSwitch(id = \"${node.id}\", label = \"$label\")")
            }
            "Widget.ShadcnCheckbox" -> {
                val label = node.props["label"] ?: ""
                appendLine("${indent}shadcnCheckbox(id = \"${node.id}\", label = \"$label\")")
            }
            "Widget.ShadcnText" -> {
                val text = node.props["text"] ?: ""
                appendLine("${indent}shadcnText(id = \"${node.id}\", text = \"$text\")")
            }
            "Widget.ShadcnSeparator" -> {
                appendLine("${indent}shadcnSeparator(id = \"${node.id}\")")
            }
            else -> {
                appendLine("$indent// Unknown node type: ${node.type}")
            }
        }
    }
}
