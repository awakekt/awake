/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.awakelab.awake.ui.builder

import io.github.awakelab.awake.ui.builder.model.UiLayoutDocument
import io.github.awakelab.awake.ui.builder.model.UiNode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UiLayoutDocumentTest {
    @Test
    fun documentSerializationRoundTrip() {
        val originalDoc = UiLayoutDocument.createEmpty("TestLayout")
        val jsonString = Json.encodeToString(originalDoc)
        val deserializedDoc = Json.decodeFromString<UiLayoutDocument>(jsonString)

        assertEquals(originalDoc.id, deserializedDoc.id)
        assertEquals("TestLayout", deserializedDoc.name)
        assertEquals("Container.Column", deserializedDoc.rootNode.type)
    }

    @Test
    fun nodeInsertionAndDeletion() {
        var doc = UiLayoutDocument.createEmpty("TestDoc")
        val buttonNode = UiNode(id = "btn_1", type = "Widget.ShadcnButton", props = mapOf("label" to "Click"))

        val newRoot = doc.rootNode.insertNode("root", 0, buttonNode)
        doc = doc.copy(rootNode = newRoot)

        assertNotNull(doc.rootNode.findNode("btn_1"))
        assertEquals(1, doc.rootNode.children.size)

        val removedRoot = doc.rootNode.removeNode("btn_1")
        assertEquals(0, removedRoot.children.size)
    }
}
