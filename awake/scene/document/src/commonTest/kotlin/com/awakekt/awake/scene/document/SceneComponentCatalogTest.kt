/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
@SerialName("catalog_probe")
private data class CatalogProbe(
    val label: String,
    val offset: SceneVec3 = SceneVec3(),
    val tint: SceneColor = SceneColor(),
    val scale: Float = 1f,
) : SceneComponent

@Serializable
@SerialName("catalog_probe")
private data class SameIdAsTheProbe(val other: Int = 0) : SceneComponent

class SceneComponentCatalogTest {
    init {
        SceneSerializers.register(CatalogProbe::class, CatalogProbe.serializer())
    }

    private fun PropertySchema.child(name: String): PropertySchema = children.single { it.name == name }

    @Test
    fun idsListEveryRegisteredComponentSorted() {
        val ids = SceneComponentCatalog.ids()

        assertTrue(ids.containsAll(listOf("catalog_probe", "prefab_link", "custom")), ids.toString())
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun anUnknownIdHasNoDescriptorAndNoSchema() {
        assertNull(SceneComponentCatalog.descriptor("no_such_component"))
        assertNull(SceneComponentCatalog.schema("no_such_component"))
    }

    @Test
    fun aComponentComesBackWithItsDescriptorAndSchema() {
        assertEquals("catalog_probe", SceneComponentCatalog.descriptor("catalog_probe")?.serialName)
        val schema = assertNotNull(SceneComponentCatalog.schema("catalog_probe"))

        assertEquals("catalog_probe", schema.name)
        assertEquals(listOf("label", "offset", "tint", "scale"), schema.children.map { it.name })
        assertTrue(schema.child("label").required)
        assertEquals(JsonPrimitive(1.0f), schema.child("scale").default)
    }

    @Test
    fun theScenesVectorAndColourTypesAreReadAsVectorAndColour() {
        val schema = assertNotNull(SceneComponentCatalog.schema("catalog_probe"))

        assertEquals(PropertyKind.Vector3, schema.child("offset").kind)
        assertEquals(PropertyKind.Color, schema.child("tint").kind)
    }

    @Test
    fun aComponentWithRequiredPropertiesStillGetsTheDefaultsOfTheOthers() {
        val link = assertNotNull(SceneComponentCatalog.schema("prefab_link"))

        assertTrue(link.child("path").required)
        assertNull(link.child("path").default, "a required property has no default")
    }

    @Test
    fun theOpenCustomComponentHasRequiredTypeAndPayload() {
        val custom = assertNotNull(SceneComponentCatalog.schema("custom"))

        assertTrue(custom.child("type").required && custom.child("payload").required)
        assertEquals(PropertyKind.Polymorphic, custom.child("payload").kind, "its payload is open JSON, so it has no fixed shape")
    }

    @Test
    fun schemasHasOneEntryPerIdInTheSameOrder() {
        assertEquals(SceneComponentCatalog.ids(), SceneComponentCatalog.schemas().keys.toList())
    }

    @Test
    fun theNodeSchemaCarriesTheTransformThatNoComponentOwns() {
        val node = SceneComponentCatalog.nodeSchema()

        assertEquals(SceneNode.serializer().descriptor.serialName, node.name)
        assertEquals(listOf("name", "transform", "components", "children"), node.children.map { it.name })

        val transform = node.child("transform")
        assertEquals(listOf("position", "rotation", "scale"), transform.children.map { it.name })
        assertTrue(transform.children.all { it.kind == PropertyKind.Vector3 })
        assertEquals(JsonPrimitive(1.0f), (transform.child("scale").default as JsonObject)["x"], "a node starts at scale 1")
        assertEquals(listOf("x", "y", "z"), transform.child("position").children.map { it.name })
    }

    @Test
    fun theNodeSchemaNamesAreNullableAndComponentsAndChildrenAreLists() {
        val node = SceneComponentCatalog.nodeSchema()

        assertTrue(node.child("name").nullable)
        assertEquals(JsonNull, node.child("name").default)
        assertEquals(PropertyKind.List, node.child("components").kind)
        assertEquals(PropertyKind.Polymorphic, node.child("components").element?.kind)
        assertEquals(PropertyKind.List, node.child("children").kind)
        assertEquals(PropertyKind.Object, node.child("children").element?.kind)
    }

    @Test
    fun twoComponentsSharingAnIdAreAnError() {
        val failure = assertFailsWith<IllegalStateException> {
            listOf(CatalogProbe.serializer(), SameIdAsTheProbe.serializer()).byId()
        }

        assertTrue("catalog_probe" in failure.message.orEmpty(), failure.message)
    }
}
