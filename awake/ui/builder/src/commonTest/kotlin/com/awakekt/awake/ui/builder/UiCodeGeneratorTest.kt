/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder

import com.awakekt.awake.ui.builder.codegen.UiCodeGenerator
import com.awakekt.awake.ui.builder.model.UiLayoutDocument
import com.awakekt.awake.ui.builder.model.UiNode
import kotlin.test.Test
import kotlin.test.assertTrue

class UiCodeGeneratorTest {
    @Test
    fun generatesValidKotlinComposeCode() {
        val buttonNode = UiNode(id = "btn_submit", type = "Widget.ShadcnButton", props = mapOf("label" to "Submit"))
        val root = UiNode(id = "root", type = "Container.Column", children = listOf(buttonNode))
        val doc = UiLayoutDocument(id = "doc1", name = "TestForm", rootNode = root)

        val code = UiCodeGenerator.generateKotlin(doc)

        assertTrue(code.contains("fun TestFormLayout()"))
        assertTrue(code.contains("shadcnButton(id = \"btn_submit\") { text(\"Submit\") }"))
    }
}
