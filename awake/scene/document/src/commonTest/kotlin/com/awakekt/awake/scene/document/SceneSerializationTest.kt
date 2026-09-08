/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
@SerialName("test_item")
private data class TestItemComponent(val itemId: String, val count: Int) : SceneComponent

class SceneSerializationTest {
    @Test
    fun customComponentSerializesAndDeserializes() {
        val payload = buildJsonObject { put("hp", 75) }
        val custom = SceneCustomComponent(type = "health", payload = payload)
        val doc = SceneDocument(
            nodes = listOf(SceneNode(name = "Hero", components = listOf(custom))),
        )

        val json = SceneLoader.encode(doc)
        val restored = SceneLoader.decode(json)

        assertEquals(1, restored.nodes.size)
        val restoredCustom = restored.nodes.first().components.first() as SceneCustomComponent
        assertEquals("health", restoredCustom.type)
    }

    @Test
    fun polymorphicComponentRegisteredDynamicallySerializesAndDeserializes() {
        SceneSerializers.register(TestItemComponent::class, TestItemComponent.serializer())
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "Chest",
                    components = listOf(
                        TestItemComponent(itemId = "sword_01", count = 2),
                        ScenePrefabLink(prefabGuid = "guid-chest-99", isRoot = true),
                    ),
                ),
            ),
        )

        val json = SceneLoader.encode(doc)
        assertTrue(json.contains("\"test_item\""))
        assertTrue(json.contains("\"prefab_link\""))

        val restored = SceneLoader.decode(json)
        val components = restored.nodes.single().components
        assertEquals(2, components.size)

        val item = components[0] as TestItemComponent
        assertEquals("sword_01", item.itemId)
        assertEquals(2, item.count)

        val link = components[1] as ScenePrefabLink
        assertEquals("guid-chest-99", link.prefabGuid)
        assertEquals(true, link.isRoot)
    }

    @Test
    fun legacyPrefabLinkDiscriminatorDecodedCorrectly() {
        val legacyJson = """
            {
              "version": 1,
              "nodes": [
                {
                  "name": "Props",
                  "components": [
                    {
                      "component": "prefabLink",
                      "prefabGuid": "guid-1234",
                      "isRoot": true
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val doc = SceneLoader.decode(legacyJson)
        val link = doc.nodes.single().components.single() as ScenePrefabLink
        assertEquals("guid-1234", link.prefabGuid)
        assertEquals(true, link.isRoot)
    }
}
