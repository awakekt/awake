/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneCustomComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneSchemaVersionException
import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.scene.document.SceneValidator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The "Scene documents" guide includes its loading, saving and validation samples from here, and
 * its scene document from `snippets/scene/harbor-town.scene.json`.
 */
class SceneDocumentsDocsSampleTest {

    private val json = File(DOCS_SNIPPETS, "scene/harbor-town.scene.json").readText()

    @Test
    fun theHarborTownDocumentLoadsIntoAWorld() {
        // --8<-- [start:load]
        DefaultSceneComponentResolvers.install()
        val document = SceneLoader.decode(json)
        val scene = SceneLoader.instantiate(document)

        val lighthouse = scene.roots.first { it.name == "lighthouse" }
        val beacon = lighthouse.children.single().entity
        val spin = scene.world.get<SpinControl>(beacon)
        // --8<-- [end:load]

        assertEquals("Harbor Town", document.name)
        assertEquals(0.5f, spin?.speed)
        assertEquals(lighthouse.entity, scene.world.get<Transform>(beacon)?.parent)
        assertEquals(6f, scene.world.get<Transform>(beacon)?.position?.y)
        assertEquals(3, scene.world.query(Transform::class).size, "every node gets a Transform")
    }

    @Test
    fun aLoadedWorldSavesBackToTheSameDocument() {
        DefaultSceneComponentResolvers.install()
        val scene = SceneLoader.decode(json).instantiate()

        // --8<-- [start:export]
        val saved: SceneDocument = SceneLoader.fromWorld(scene.world, name = "Harbor Town")
        val text = SceneLoader.encode(saved)
        // --8<-- [end:export]

        assertEquals(SceneLoader.decode(json), SceneLoader.decode(text))
    }

    @Test
    fun validationReportsEveryProblemWithItsNodePath() {
        DefaultSceneComponentResolvers.install()
        // --8<-- [start:validate]
        val document = SceneLoader.decode(
            """
            {
              "nodes": [
                { "name": "dock" },
                { "name": "dock", "components": [ { "component": "spin_control", "speed": -1.0 } ] }
              ]
            }
            """,
        )
        val issues = SceneValidator.validate(document)
        issues.forEach { println("${it.path}: ${it.message}") }
        // --8<-- [end:validate]

        assertEquals(
            listOf(
                "dock: duplicate node name 'dock' already used at dock",
                "dock: spinControl.speed must not be negative",
            ),
            issues.map { "${it.path}: ${it.message}" },
        )
        assertFailsWith<SceneValidationException> { SceneLoader.instantiate(document) }
    }

    @Test
    fun aNewerSchemaVersionIsRefused() {
        assertFailsWith<SceneSchemaVersionException> { SceneLoader.decode("""{ "version": 2 }""") }
    }

    @Test
    fun anUnregisteredComponentIdDoesNotDecode() {
        DefaultSceneComponentResolvers.install()
        assertFails { SceneLoader.decode("""{ "nodes": [ { "components": [ { "component": "not_registered" } ] } ] }""") }
    }

    @Test
    fun aCustomComponentIsKeptInTheDocumentButNotOnTheEntityOrOnExport() {
        DefaultSceneComponentResolvers.install()
        val document = SceneLoader.decode(
            """
            { "nodes": [ { "name": "sky", "components": [
                { "component": "custom", "type": "weather", "payload": { "rain": 0.3 } }
            ] } ] }
            """,
        )
        val custom = document.nodes.single().components.single() as SceneCustomComponent
        assertEquals("weather", custom.type)

        val scene = document.instantiate()
        val entity = scene.roots.single().entity
        assertEquals(listOf("Name", "Transform"), scene.world.componentTypes(entity).mapNotNull { it.simpleName }.sorted())
        assertTrue(SceneLoader.fromWorld(scene.world).nodes.single().components.isEmpty())
    }

    @Test
    fun theSceneManagerReplacesTheCurrentScene() {
        val other = SceneDocument(name = "empty")
        // --8<-- [start:switch]
        val world = World()
        val scenes = SceneManager(world)
        scenes.switchTo(SceneLoader.decode(json)) // Harbor Town's entities are now in world
        scenes.switchTo(other) // Harbor Town is destroyed first, then other loads
        scenes.close() // destroys whatever is current
        // --8<-- [end:switch]
        assertEquals(0, world.query(Transform::class).size)

        scenes.switchTo(SceneLoader.decode(json))
        assertEquals(3, world.query(Transform::class).size)
        scenes.switchTo(other)
        assertEquals(0, world.query(Transform::class).size)
    }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
