/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A component no serializer knows is kept as its JSON by an editor's `Json`, and refused by the default. */
class SceneUnknownComponentTest {
    private val keeping = SceneSerializers.createJson(keepUnknownComponents = true)

    @Test
    fun anEditorsJsonKeepsAnUnknownComponentBesideTheKnownOnes() {
        val document = SceneLoader.decode(SCENE, keeping)

        val components = document.nodes.single().components
        assertEquals(3, components.size)
        assertIs<SceneCustomComponent>(components[0])
        val hook = assertIs<SceneUnknownComponent>(components[1])
        assertEquals("grappling_hook", hook.type)
        assertEquals(listOf("range", "anchors", "label"), hook.fields.keys.toList())
        assertEquals(SceneUnknownComponent("rope", JsonObject(emptyMap())), components[2])
    }

    @Test
    fun aKeptComponentIsWrittenBackAsItWasRead() {
        val encoded = SceneLoader.encode(SceneLoader.decode(SCENE, keeping), keeping)

        assertEquals(componentsOf(SCENE), componentsOf(encoded))
        assertTrue(HOOK_JSON in Json.encodeToString(JsonObject.serializer(), componentsOf(encoded)[1]), "the same JSON, key for key")
    }

    @Test
    fun theDefaultJsonWritesAKeptComponentToo() {
        val document = SceneLoader.decode(SCENE, keeping)

        assertEquals(componentsOf(SCENE), componentsOf(SceneLoader.encode(document)))
    }

    @Test
    fun theDiscriminatorCanComeAfterTheFields() {
        val late = """{ "version": 1, "nodes": [ { "components": [ { "range": 3, "component": "grappling_hook" } ] } ] }"""

        val hook = assertIs<SceneUnknownComponent>(SceneLoader.decode(late, keeping).nodes.single().components.single())

        assertEquals("grappling_hook", hook.type)
        assertEquals(setOf("range"), hook.fields.keys)
    }

    @Test
    fun theDefaultJsonStillRefusesAnUnknownComponent() {
        assertFailsWith<SerializationException> { SceneLoader.decode(SCENE) }
    }

    @Test
    fun validationNamesAKeptComponentWithoutFailingTheScene() {
        val document = SceneLoader.decode(SCENE, keeping)

        assertEquals(emptyList(), SceneValidator.validate(document))
        assertEquals(
            listOf("Harbor", "Harbor").zip(listOf("grappling_hook", "rope")).map { (path, type) ->
                SceneValidationIssue(path, "$type is a component nothing installed provides; it is kept as data and does not run")
            },
            SceneValidator.unknownComponentIssues(document),
        )
    }

    private fun componentsOf(scene: String) =
        Json.parseToJsonElement(scene).jsonObject.getValue("nodes").jsonArray.single().jsonObject.getValue("components").jsonArray
            .map { it.jsonObject }

    private companion object {
        const val HOOK_JSON = """{"component":"grappling_hook","range":12.5,"anchors":[{"x":1,"y":2}],"label":"Dock line"}"""

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Harbor", "components": [
    { "component": "custom", "type": "marker", "payload": { "id": 1 } },
    $HOOK_JSON,
    { "component": "rope" }
  ] }
] }
"""
    }
}
