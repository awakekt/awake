/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.fog.FogBinding
import com.awakekt.awake.scene.rendering.light.AmbientLight
import com.awakekt.awake.scene.rendering.light.AmbientLightBinding
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.LightBinding
import com.awakekt.awake.scene.rendering.tonemapping.ToneMapping
import com.awakekt.awake.scene.rendering.tonemapping.ToneMappingBinding
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The saved form each field-copy scene3d binding writes, byte for byte, recorded from the hand-written
 * bindings before they were generated. Every float is a dyadic fraction, so it prints the same on
 * every target.
 */
class Scene3dBindingGoldenTest {
    @Test
    fun fogSavesAndLoadsAsTheHandWrittenBindingDid() {
        val json = """{"enabled":false,"density":0.125,"color":{"r":0.5,"g":0.25,"b":0.75,"a":0.5}}"""
        assertExports(json, FogBinding, Fog(enabled = false, density = 0.125f, color = Color(0.5f, 0.25f, 0.75f, 0.5f)))
        assertEquals(Fog(false, 0.125f, Color(0.5f, 0.25f, 0.75f, 0.5f)), assertRoundTrips(json, FogBinding))
    }

    @Test
    fun legacyFogChannelsStillOverrideTheColorOnLoad() {
        val legacy = """{"enabled":true,"density":0.125,"colorR":0.5,"colorG":0.25,"colorB":0.75}"""
        val live = load(legacy, FogBinding)
        assertEquals(Color(0.5f, 0.25f, 0.75f, 1f), live.color)
    }

    @Test
    fun ambientLightSavesAndLoadsAsTheHandWrittenBindingDid() {
        val json = """{"intensity":0.75,"color":{"r":0.5,"g":0.25,"b":0.75,"a":0.5}}"""
        assertExports(json, AmbientLightBinding, AmbientLight(0.75f, Color(0.5f, 0.25f, 0.75f, 0.5f)))
        assertEquals(AmbientLight(0.75f, Color(0.5f, 0.25f, 0.75f, 0.5f)), assertRoundTrips(json, AmbientLightBinding))
    }

    @Test
    fun legacyAmbientChannelsStillOverrideTheColorOnLoad() {
        val live = load("""{"intensity":0.75,"colorR":0.5,"colorG":0.25,"colorB":0.75}""", AmbientLightBinding)
        assertEquals(Color(0.5f, 0.25f, 0.75f, 1f), live.color)
    }

    @Test
    fun toneMappingSavesAndLoadsAsTheHandWrittenBindingDid() {
        val json = """{"exposure":1.5}"""
        assertExports(json, ToneMappingBinding, ToneMapping(exposure = 1.5f))
        assertEquals(ToneMapping(1.5f), assertRoundTrips(json, ToneMappingBinding))
    }

    @Test
    fun lightSavesAndLoadsAsTheHandWrittenBindingDid() {
        val live = Light(
            color = Vec3f(0.5f, 0.25f, 0.75f),
            intensity = 1.5f,
            type = Light.Type.Directional,
            direction = Vec3f(0.5f, 0.25f, 0.125f),
            range = 2.5f,
            shadowsEnabled = false,
            shadowDistance = 12.5f,
            ambient = 0.5f,
        )
        val json = """{"color":{"r":0.5,"g":0.25,"b":0.75},"intensity":1.5,"type":"Directional",""" +
            """"direction":{"x":0.5,"y":0.25,"z":0.125},"range":2.5,"shadowsEnabled":false,"shadowDistance":12.5,"ambient":0.5}"""
        assertExports(json, LightBinding, live)
        assertEquals(live, assertRoundTrips(json, LightBinding))
    }

    @Test
    fun aPointLightWithNoAmbientSavesWithoutIt() {
        val live = Light(type = Light.Type.Point, intensity = 0.5f, range = 4.5f, shadowDistance = 0.5f)
        val exported = export(LightBinding, live)
        assertEquals(false, "ambient" in exported)
        assertEquals(true, "\"type\":\"Point\"" in exported)
        assertEquals(live, load(exported, LightBinding))
    }
}

private val json = SceneSerializers.createJson()

private class NoLinks(override val world: World) : SceneResolutionContext {
    override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

    override fun recordRequest(request: Any) = Unit
}

private fun <C : Any, S : SceneComponent> export(binding: SceneComponentBinding<C, S>, live: C): String {
    val world = World()
    val entity = world.create()
    world.add(entity, binding.componentClass, live)
    return json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!)
}

private fun <C : Any, S : SceneComponent> load(saved: String, binding: SceneComponentBinding<C, S>): C {
    val world = World()
    val entity = world.create()
    binding.attach(world, entity, json.decodeFromString(binding.serializer!!, saved), NoLinks(world))
    return world.get(entity, binding.componentClass)!!
}

private fun <C : Any, S : SceneComponent> assertExports(expected: String, binding: SceneComponentBinding<C, S>, live: C) =
    assertEquals(expected, export(binding, live))

/** Asserts [saved] loads through [binding] and saves back as exactly [saved]; returns the live component. */
private fun <C : Any, S : SceneComponent> assertRoundTrips(saved: String, binding: SceneComponentBinding<C, S>): C {
    val live = load(saved, binding)
    assertEquals(saved, export(binding, live))
    return live
}
