/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private data class HealthComponent(var hp: Int)

private class HealthComponentResolver : SceneComponentResolver {
    override fun canResolve(component: SceneComponent): Boolean =
        component is SceneCustomComponent && component.type == "health"

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val custom = component as SceneCustomComponent
        val hp = custom.payload.let {
            if (it is JsonObject && it.containsKey("hp")) {
                it["hp"].toString().toIntOrNull() ?: 100
            } else {
                100
            }
        }
        world.add(entity, HealthComponent(hp))
    }
}

class SceneComponentResolverTest {
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
    fun registeredResolverAttachesCustomComponentDuringInstantiation() {
        val payload = buildJsonObject { put("hp", 85) }
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "Warrior",
                    components = listOf(SceneCustomComponent(type = "health", payload = payload)),
                ),
            ),
        )

        val world = World()
        val registry = SceneComponentRegistry(listOf(HealthComponentResolver()))
        val scene = SceneLoader.instantiate(doc, world = world, componentRegistry = registry)

        assertNotNull(scene)
        var heroHp: Int? = null
        world.queryEach<HealthComponent> { _, health ->
            heroHp = health.hp
        }
        assertEquals(85, heroHp)
    }

    @Test
    fun unregisteredCustomComponentDoesNotCrashInstantiation() {
        val payload = buildJsonObject { put("unknownKey", "value") }
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "Ghost",
                    components = listOf(SceneCustomComponent(type = "unregistered_type", payload = payload)),
                ),
            ),
        )

        val world = World()
        val scene = SceneLoader.instantiate(doc, world = world)
        assertNotNull(scene)
        assertEquals(1, scene.roots.size)
    }

    @Test
    fun customBindingExportsAndInstantiatesRoundTrip() {
        val healthBinding = object : SceneComponentBinding<HealthComponent, SceneCustomComponent> {
            override val componentClass = HealthComponent::class

            override fun canResolve(component: SceneComponent): Boolean =
                component is SceneCustomComponent && component.type == "health"

            override fun attach(
                world: World,
                entity: Entity,
                component: SceneComponent,
                context: SceneResolutionContext,
            ) {
                val custom = component as SceneCustomComponent
                val hp = (custom.payload as? JsonObject)?.get("hp")?.toString()?.toIntOrNull() ?: 100
                world.add(entity, HealthComponent(hp))
            }

            override fun export(world: World, entity: Entity, component: HealthComponent): SceneCustomComponent =
                SceneCustomComponent(
                    type = "health",
                    payload = buildJsonObject { put("hp", component.hp) },
                )

            override fun exportFrom(world: World, entity: Entity): SceneCustomComponent? =
                world.get(entity, componentClass)?.let { export(world, entity, it) }
        }

        val registry = SceneComponentRegistry(bindings = listOf(healthBinding))

        val world = World()
        val hero = world.create()
        world.add(hero, com.awakekt.awake.scene.core.Name("Hero"))
        world.add(hero, com.awakekt.awake.scene.core.transform.Transform())
        world.add(hero, HealthComponent(hp = 95))

        val doc = SceneLoader.fromWorld(world, componentRegistry = registry)
        val exportedComponent = doc.nodes.single().components.single() as SceneCustomComponent
        assertEquals("health", exportedComponent.type)
        assertEquals(95, (exportedComponent.payload as JsonObject)["hp"]?.toString()?.toInt())

        val restoredWorld = World()
        SceneLoader.instantiate(doc, world = restoredWorld, componentRegistry = registry)
        var restoredHp: Int? = null
        restoredWorld.queryEach<HealthComponent> { _, health ->
            restoredHp = health.hp
        }
        assertEquals(95, restoredHp)
    }

    @Test
    fun legacyCamelCaseDiscriminatorsAndPropertiesAreDecodedCorrectly() {
        val legacyJson = """
            {
              "version": 1,
              "nodes": [
                {
                  "name": "Props",
                  "components": [
                    {
                      "component": "meshRenderer",
                      "mesh": "cube.glb",
                      "material": "mat.json",
                      "cullMode": "Back"
                    },
                    {
                      "component": "spinControl",
                      "radians": 1.5,
                      "speed": 2.0
                    },
                    {
                      "component": "pbrMaterial",
                      "metallic": 0.8,
                      "roughness": 0.2
                    },
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
        val components = doc.nodes.single().components
        assertEquals(4, components.size)

        val mesh = components[0] as SceneMeshRenderer
        assertEquals("cube.glb", mesh.mesh)
        assertEquals(SceneMeshRenderer.CullMode.Back, mesh.cullMode)

        val spin = components[1] as SceneSpinControl
        assertEquals(1.5f, spin.radians)
        assertEquals(2.0f, spin.speed)

        val pbr = components[2] as ScenePbrMaterial
        assertEquals(0.8f, pbr.metallic)
        assertEquals(0.2f, pbr.roughness)

        val prefab = components[3] as ScenePrefabLink
        assertEquals("guid-1234", prefab.prefabGuid)
        assertEquals(true, prefab.isRoot)
    }

    @Test
    fun encodeOutputsSnakeCaseDiscriminators() {
        val doc = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "TestNode",
                    components = listOf(
                        SceneMeshRenderer(mesh = "m.glb", material = "m.mat"),
                        SceneSpinControl(radians = 0f, speed = 1f),
                        ScenePbrMaterial(metallic = 0.5f, roughness = 0.5f),
                        ScenePrefabLink(prefabGuid = "guid-abc"),
                    ),
                ),
            ),
        )

        val json = SceneLoader.encode(doc)
        kotlin.test.assertTrue(json.contains("\"mesh_renderer\""))
        kotlin.test.assertTrue(json.contains("\"spin_control\""))
        kotlin.test.assertTrue(json.contains("\"pbr_material\""))
        kotlin.test.assertTrue(json.contains("\"prefab_link\""))
    }
}
