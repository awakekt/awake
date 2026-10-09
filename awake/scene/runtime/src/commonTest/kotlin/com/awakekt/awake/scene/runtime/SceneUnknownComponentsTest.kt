/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Tags
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneUnknownComponents
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.TagBinding
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneUnknownComponent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An editor without a game's code opens its scene: the components it can't run are kept as data on
 * their entities, the rest instantiates, and saving the world writes them back unchanged.
 */
class SceneUnknownComponentsTest {
    private val registry = SceneComponentRegistry.scoped(bindings = listOf(TagBinding))

    @Test
    fun theRestOfTheSceneInstantiatesAndTheUnknownComponentIsKeptOnItsEntity() {
        val world = World()
        SceneLoader.instantiate(SceneLoader.decode(SCENE, registry.sceneJson(keepUnknownComponents = true)), world, registry)

        val dock = world.named("Dock")
        assertEquals(setOf("pier"), world.get<Tags>(dock)?.names, "a component the registry knows still attaches")
        assertEquals(listOf("grappling_hook"), assertNotNull(world.get<SceneUnknownComponents>(dock)).components.map { it.type })
        assertNull(world.get<SceneUnknownComponents>(world.named("Crate")), "a node with nothing unknown keeps nothing")
    }

    @Test
    fun savingTheWorldWritesTheUnknownComponentBackAsItWasRead() {
        val json = registry.sceneJson(keepUnknownComponents = true)
        val world = World()
        SceneLoader.instantiate(SceneLoader.decode(SCENE, json), world, registry)

        val saved = SceneLoader.encode(SceneLoader.fromWorld(world, name = "harbor", componentRegistry = registry), json)

        val dock = Json.parseToJsonElement(saved).jsonObject.getValue("nodes").jsonArray
            .map { it.jsonObject }.single { it["name"].toString() == "\"Dock\"" }
        val hook = dock.getValue("components").jsonArray.map { it.jsonObject }.single { it["component"].toString() == "\"grappling_hook\"" }
        assertEquals(Json.parseToJsonElement(HOOK_JSON), hook)
        assertEquals(
            SceneUnknownComponent("grappling_hook", JsonObject(Json.parseToJsonElement(HOOK_JSON).jsonObject - "component")),
            SceneLoader.decode(saved, json).nodes.single { it.name == "Dock" }.components.filterIsInstance<SceneUnknownComponent>().single(),
        )
    }

    @Test
    fun theRegistrysOwnSceneJsonStillRefusesIt() {
        assertFailsWith<SerializationException> { SceneLoader.decode(SCENE, registry.sceneJson()) }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private companion object {
        const val HOOK_JSON = """{"component":"grappling_hook","range":12.5,"anchors":[{"x":1,"y":2}]}"""

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Dock", "components": [ { "component": "tag", "tags": ["pier"] }, $HOOK_JSON ] },
  { "name": "Crate", "components": [ { "component": "tag", "tags": ["cargo"] } ] }
] }
"""
    }
}
