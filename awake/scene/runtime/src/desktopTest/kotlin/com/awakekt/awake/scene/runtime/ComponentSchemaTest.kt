/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.core.schema.propertySchemaOf
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real components read into property schemas: their descriptors, defaults and required properties. */
class ComponentSchemaTest {
    private val json = SceneSerializers.createJson()
    private val semantic = mapOf(
        SceneVec3.serializer().descriptor.serialName to PropertyKind.Vector3,
        SceneColor.serializer().descriptor.serialName to PropertyKind.Color,
    )

    private fun PropertySchema.child(name: String): PropertySchema = children.single { it.name == name }

    @Test
    fun spinControlIsTwoFloatsEachWithADefault() {
        val schema = propertySchemaOf(SceneSpinControl.serializer(), json, semantic)

        assertEquals("spin_control", schema.name)
        assertEquals(listOf("radians", "speed"), schema.children.map { it.name })
        assertTrue(schema.children.all { it.kind == PropertyKind.Float && !it.required })
        assertEquals(JsonPrimitive(0.0f), schema.child("radians").default)
        assertEquals(JsonPrimitive(1.0f), schema.child("speed").default)
    }

    @Test
    fun particleEmitterNeedsOnlyItsTextureAndEveryOtherPropertyHasADefault() {
        val schema = propertySchemaOf(SceneParticleEmitter.serializer(), json, semantic)

        assertEquals("particle_emitter", schema.name)
        val required = schema.children.filter { it.required }.map { it.name }
        assertEquals(listOf("texture"), required)
        assertEquals(PropertyKind.Text, schema.child("texture").kind)
        assertNull(schema.child("texture").default, "a required property has no default, whatever seeded the others")
        assertEquals(JsonPrimitive(64), schema.child("maxParticles").default)
        assertEquals(JsonPrimitive(10.0f), schema.child("spawnRate").default)
    }

    @Test
    fun particleEmitterShowsItsNullableNestedAndSemanticProperties() {
        val schema = propertySchemaOf(SceneParticleEmitter.serializer(), json, semantic)

        val endScale = schema.child("endScale")
        assertEquals(PropertyKind.Float, endScale.kind)
        assertTrue(endScale.nullable)
        assertEquals(JsonNull, endScale.default)

        assertEquals(PropertyKind.Vector3, schema.child("velocity").kind)
        assertEquals(0.0f, schema.child("velocity").default!!.jsonObject.getValue("x").toString().toFloat())
        assertEquals(PropertyKind.Color, schema.child("color").kind)

        val spin = schema.child("spin")
        assertTrue(spin.nullable)
        assertEquals(PropertyKind.Object, spin.kind)
        assertTrue(spin.children.map { it.name }.containsAll(listOf("minDegreesPerSecond", "maxDegreesPerSecond", "randomStartAngle")))

        assertEquals(PropertyKind.Enum, schema.child("facing").kind)
        assertTrue("Flat" in schema.child("facing").enumValues, schema.child("facing").enumValues.toString())
    }
}
