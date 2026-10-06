/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.schema

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertySchemasTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun PropertySchema.child(name: String): PropertySchema =
        children.single { it.name == name }

    @Test
    fun aTypesPropertiesComeBackInDeclarationOrderWithTheirKinds() {
        val schema = propertySchemaOf(Emitter.serializer(), json)

        assertEquals(
            listOf("texture", "lifetime", "alpha", "count", "mode", "tags", "named", "nested", "radians", "label", "offset", "spacing"),
            schema.children.map { it.name },
        )
        assertEquals(PropertyKind.Object, schema.kind)
        assertEquals(PropertyKind.Text, schema.child("texture").kind)
        assertEquals(PropertyKind.Float, schema.child("lifetime").kind)
        assertEquals(PropertyKind.Int, schema.child("count").kind)
        assertEquals(PropertyKind.Enum, schema.child("mode").kind)
        assertEquals(PropertyKind.List, schema.child("tags").kind)
        assertEquals(PropertyKind.Map, schema.child("named").kind)
        assertEquals(PropertyKind.Object, schema.child("nested").kind)
    }

    @Test
    fun aPropertyWithNoDefaultIsRequiredAndOneWithADefaultIsNot() {
        val schema = propertySchemaOf(Emitter.serializer(), json, semantic)

        assertTrue(schema.child("texture").required, "texture has no default")
        assertNull(schema.child("texture").default, "a required property has no default")
        assertTrue(!schema.child("lifetime").required)
        assertEquals(JsonPrimitive(1.0f), schema.child("lifetime").default)
        assertEquals(JsonPrimitive("A"), schema.child("mode").default)
    }

    @Test
    fun aTypeWithARequiredPropertyHasNoDerivedDefaultsUntilSeededWithOne() {
        assertNull(deriveDefaults(Emitter.serializer(), json), "texture is required, so nothing can be built from {}")

        val seeded = deriveDefaults(Emitter.serializer(), json, buildJsonObject { put("texture", "spark.png") })

        assertNotNull(seeded)
        assertEquals(JsonPrimitive(1.0f), seeded["lifetime"])
    }

    @Test
    fun aTypeThatRejectsTheRequiredPlaceholdersHasNoKnownDefaults() {
        val schema = propertySchemaOf(Strict.serializer(), json)

        assertTrue(schema.child("width").required)
        assertNull(schema.child("depth").default, "width = 0 fails Strict's own check, so nothing could be learned")
    }

    @Test
    fun aNullableOptionalPropertyDefaultsToJsonNullAndSaysItIsNullable() {
        val schema = propertySchemaOf(Emitter.serializer(), json, semantic)

        val count = schema.child("count")
        assertTrue(count.nullable)
        assertTrue(!count.required)
        assertEquals(JsonNull, count.default)
        assertTrue(!schema.child("lifetime").nullable)
    }

    @Test
    fun nullablePropertyRequirednessDependsOnExplicitNullsConfiguration() {
        val withExplicitNulls = Json { explicitNulls = true }
        val withoutExplicitNulls = Json { explicitNulls = false }

        val schemaExplicit = propertySchemaOf(NullableRequired.serializer(), withExplicitNulls)
        val schemaImplicit = propertySchemaOf(NullableRequired.serializer(), withoutExplicitNulls)

        val fieldExplicit = schemaExplicit.children.single()
        val fieldImplicit = schemaImplicit.children.single()

        assertTrue(fieldExplicit.nullable)
        assertTrue(fieldExplicit.required, "With explicitNulls = true, a nullable property without default is required")

        assertTrue(fieldImplicit.nullable)
        assertTrue(!fieldImplicit.required, "With explicitNulls = false, a nullable property without default is optional")
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun anOmittedNonNullDefaultIsUnknownRatherThanFabricatedAsJsonNull() {
        val schema = propertySchemaOf(OmittedDefault.serializer(), json)
        val field = schema.children.single()

        assertTrue(field.nullable)
        assertTrue(!field.required)
        assertNull(field.default, "An omitted non-null default with EncodeDefault(NEVER) must remain unknown (null), not JsonNull")
    }

    @Test
    fun enumNamesListElementsMapValuesAndNestedObjectsAreRead() {
        val schema = propertySchemaOf(Emitter.serializer(), json, semantic)

        assertEquals(listOf("A", "B", "C"), schema.child("mode").enumValues)
        assertEquals(PropertyKind.Text, schema.child("tags").element?.kind)
        assertEquals(PropertyKind.Float, schema.child("named").element?.kind)
        val nested = schema.child("nested")
        assertTrue(nested.nullable)
        assertEquals(listOf("depth", "weight"), nested.children.map { it.name })
        assertEquals(PropertyKind.Int, nested.child("depth").kind)
    }

    @Test
    fun annotationsBecomeConstraintsAndHints() {
        val schema = propertySchemaOf(Emitter.serializer(), json, semantic)

        val lifetime = schema.child("lifetime")
        assertEquals(NumberRange(min = 0.0, max = null, exclusiveMin = true), lifetime.constraints.range)
        assertEquals("How long a particle lives.", lifetime.hints.tooltip)
        assertEquals("s", lifetime.hints.unit)

        val alpha = schema.child("alpha")
        assertEquals(SliderHint(0.0, 1.0, 0.05), alpha.hints.slider)
        assertNull(alpha.constraints.range, "a slider is a hint and constrains nothing")

        assertTrue(schema.child("radians").hints.hidden)
        assertTrue(schema.child("label").hints.readOnly)
        assertEquals("texture", schema.child("texture").hints.assetKind)
        assertEquals(0.5, schema.child("spacing").hints.step)
    }

    @Test
    fun aTypeNamedInSemanticTypesIsReadAsThatKind() {
        val plain = propertySchemaOf(Emitter.serializer(), json)
        val mapped = propertySchemaOf(Emitter.serializer(), json, semantic)

        assertEquals(PropertyKind.Object, plain.child("offset").kind)
        assertEquals(PropertyKind.Vector3, mapped.child("offset").kind)
        assertEquals(listOf("x", "y", "z"), mapped.child("offset").children.map { it.name }, "its fields stay readable")
    }

    @Test
    fun aCustomSerializersDescriptorIsWhatTheWalkerReads() {
        val schema = propertySchemaOf(TintHolder.serializer(), json, mapOf(TintSerializer.descriptor.serialName to PropertyKind.Color))

        val tint = schema.child("tint")
        assertEquals(PropertyKind.Color, tint.kind)
        assertEquals(listOf("r", "g", "b", "a"), tint.children.map { it.name })
        assertEquals(JsonPrimitive(1.0f), (tint.default as JsonObject)["a"])
    }

    @Test
    fun theSameDescriptorAndOptionsGiveAnEqualSchema() {
        assertEquals(
            propertySchemaOf(Emitter.serializer(), json, semantic),
            propertySchemaOf(Emitter.serializer(), json, semantic),
        )
    }

    @Test
    fun aTypeThatContainsItselfIsCutOffAtMaxDepthInsteadOfRecursingForever() {
        val schema = Tree.serializer().descriptor.toPropertySchema(SchemaOptions(maxDepth = 3))

        var depth = 0
        var node: PropertySchema? = schema
        while (node != null && node.children.isNotEmpty()) {
            node = node.children.firstOrNull { it.name == "child" }
            depth++
        }
        assertEquals(3, depth)
    }

    @Test
    fun aNumericAnnotationOnANonNumericPropertyIsRejectedNamingTheProperty() {
        val failure = assertFailsWith<IllegalArgumentException> { Misapplied.serializer().descriptor.toPropertySchema() }

        assertTrue("label" in failure.message.orEmpty() && "PropertyRange" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aRangeWhoseMinimumExceedsItsMaximumIsRejected() {
        val failure = assertFailsWith<IllegalArgumentException> { Backwards.serializer().descriptor.toPropertySchema() }

        assertTrue("min" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun anAssetReferenceOnANumberIsRejected() {
        assertFailsWith<IllegalArgumentException> { AssetOnNumber.serializer().descriptor.toPropertySchema() }
    }

    @Test
    fun maxDepthMustBeAtLeastOne() {
        assertFailsWith<IllegalArgumentException> { SchemaOptions(maxDepth = 0) }
    }

    private val semantic = mapOf(Vec.serializer().descriptor.serialName to PropertyKind.Vector3)

    @Serializable
    private enum class Mode { A, B, C }

    @Serializable
    private data class Vec(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

    @Serializable
    private data class Nested(val depth: Int = 1, val weight: Float = 0.5f)

    @Serializable
    private data class Emitter(
        @AssetReference("texture") val texture: String,
        @PropertyRange(min = 0.0, exclusiveMin = true)
        @PropertyHint("How long a particle lives.")
        @PropertyUnit("s")
        val lifetime: Float = 1f,
        @PropertySlider(softMin = 0.0, softMax = 1.0, step = 0.05) val alpha: Float = 1f,
        val count: Int? = null,
        val mode: Mode = Mode.A,
        val tags: List<String> = emptyList(),
        val named: Map<String, Float> = emptyMap(),
        val nested: Nested? = null,
        @PropertyHidden val radians: Float = 0f,
        @PropertyReadOnly val label: String = "emitter",
        val offset: Vec = Vec(),
        @PropertyStep(0.5) val spacing: Float = 1f,
    )

    @Serializable
    private data class Strict(val width: Int, val depth: Int = 4) {
        init {
            require(width > 0) { "width must be positive" }
        }
    }

    @Serializable
    private data class NullableRequired(val label: String?)

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class OmittedDefault(
        @EncodeDefault(EncodeDefault.Mode.NEVER) val label: String? = "known",
    )

    @Serializable
    private data class Tree(val name: String = "node", val child: Tree? = null)

    @Serializable
    private data class Misapplied(@PropertyRange(min = 0.0) val label: String = "")

    @Serializable
    private data class Backwards(@PropertyRange(min = 2.0, max = 1.0) val value: Float = 0f)

    @Serializable
    private data class AssetOnNumber(@AssetReference("texture") val count: Int = 0)

    @Serializable
    private data class TintHolder(val tint: Tint = Tint())

    /** Stands in for a type with a hand-written serializer whose descriptor differs from its fields. */
    @Serializable(with = TintSerializer::class)
    private data class Tint(val r: Float = 1f, val g: Float = 1f, val b: Float = 1f, val a: Float = 1f)

    private object TintSerializer : KSerializer<Tint> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("test.Tint") {
            element<Float>("r", isOptional = true)
            element<Float>("g", isOptional = true)
            element<Float>("b", isOptional = true)
            element<Float>("a", isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: Tint) {
            val composite = encoder.beginStructure(descriptor)
            composite.encodeFloatElement(descriptor, 0, value.r)
            composite.encodeFloatElement(descriptor, 1, value.g)
            composite.encodeFloatElement(descriptor, 2, value.b)
            composite.encodeFloatElement(descriptor, 3, value.a)
            composite.endStructure(descriptor)
        }

        override fun deserialize(decoder: Decoder): Tint {
            val composite = decoder.beginStructure(descriptor)
            var tint = Tint()
            while (true) {
                when (val index = composite.decodeElementIndex(descriptor)) {
                    0 -> tint = tint.copy(r = composite.decodeFloatElement(descriptor, 0))
                    1 -> tint = tint.copy(g = composite.decodeFloatElement(descriptor, 1))
                    2 -> tint = tint.copy(b = composite.decodeFloatElement(descriptor, 2))
                    3 -> tint = tint.copy(a = composite.decodeFloatElement(descriptor, 3))
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("unexpected index $index")
                }
            }
            composite.endStructure(descriptor)
            return tint
        }
    }
}
