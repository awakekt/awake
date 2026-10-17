/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ai.definition.AiDefinitionException
import com.awakekt.awake.ai.definition.AiDefinitionLibrary
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.scene.ai.definition.AiDefinitionAttachSystem
import com.awakekt.awake.scene.ai.definition.BehaviorTreeAsset
import com.awakekt.awake.scene.ai.definition.SceneBehaviorTree
import com.awakekt.awake.scene.ai.definition.SceneStateMachine
import com.awakekt.awake.scene.ai.definition.StateMachineAsset
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneAiDefinitionTest {
    private val registry = SceneComponentRegistry().registerAiBehaviors()

    private val document = SceneDocument(
        nodes = listOf(
            SceneNode(name = "Keeper", components = listOf(SceneBehaviorTree("ai/keeper.tree.json", seed = 7))),
            SceneNode(name = "Lamp", components = listOf(SceneStateMachine("ai/lamp.machine.json", enabled = false))),
        ),
    )

    private val tree = """{ "root": { "kind": "ai.set", "params": { "key": "version", "value": 1 } } }"""
    private val machine = """{ "initial": "off", "states": [ { "name": "off" } ] }"""

    @Test
    fun theComponentsRoundTripThroughASceneDocument() {
        val text = SceneLoader.encode(document)
        assertTrue("\"component\": \"behavior_tree\"" in text || "\"component\":\"behavior_tree\"" in text, text)
        val world = SceneLoader.instantiate(SceneLoader.decode(text), componentRegistry = registry).world
        assertEquals(document.nodes.map { it.components }, SceneLoader.fromWorld(world, componentRegistry = registry).nodes.map { it.components })
    }

    @Test
    fun aBlankAssetIsInvalid() {
        val issues = SceneValidator.validate(SceneDocument(nodes = listOf(SceneNode(name = "x", components = listOf(SceneBehaviorTree(" "))))))
        assertTrue(issues.any { "behavior_tree.asset must not be blank" in it.message }, "$issues")
    }

    @Test
    fun entitiesGetTheirBehaviourOnceTheLibraryHasItAndAgainOnReload() {
        val world = SceneLoader.instantiate(document, componentRegistry = registry).world
        val keeper = world.named("Keeper")
        val lamp = world.named("Lamp")
        assertNotNull(world.get<PathRequest>(keeper), "the moving kinds route through it")
        val library = AiDefinitionLibrary()
        val attach = AiDefinitionAttachSystem(library)

        attach.update(world, 0f)
        assertNull(world.get<BehaviorTreeComponent>(keeper))

        library.loadTree("ai/keeper.tree.json", tree)
        library.loadStateMachine("ai/lamp.machine.json", machine)
        attach.update(world, 0f)
        val first = world.get<BehaviorTreeComponent>(keeper)!!
        assertEquals(false, world.get<AiStateMachineComponent>(lamp)!!.enabled)

        attach.update(world, 0f)
        assertTrue(first === world.get<BehaviorTreeComponent>(keeper), "nothing changed, so nothing is rebuilt")

        library.loadTree("ai/keeper.tree.json", tree.replace("\"value\": 1", "\"value\": 2"))
        attach.update(world, 0f)
        val reloaded = world.get<BehaviorTreeComponent>(keeper)!!
        assertTrue(first !== reloaded)
        BehaviorTreeSystem().update(world, 0.1f)
        assertEquals(2, reloaded.blackboard.get<Int>("version"))
        assertEquals(BehaviorStatus.SUCCESS, reloaded.activeStatus)

        library.forget("ai/keeper.tree.json")
        attach.update(world, 0f)
        assertNull(world.get<BehaviorTreeComponent>(keeper))
        assertEquals("ai/keeper.tree.json", world.get<BehaviorTreeAsset>(keeper)!!.asset)
        assertEquals("ai/lamp.machine.json", world.get<StateMachineAsset>(lamp)!!.asset)
    }

    @Test
    fun aLibraryNamesTheFileInEveryProblem() {
        val error = assertFailsWith<AiDefinitionException> {
            AiDefinitionLibrary().loadTree("ai/broken.tree.json", """{ "root": { "kind": "ai.wait" } }""")
        }
        assertEquals("ai/broken.tree.json: node 0 (ai.wait): missing parameter 'seconds'; ai.wait needs it.", error.problems.single().toString())
    }

    private fun World.named(name: String) = buildList { queryEach(Name::class) { entity, value -> if (value.value == name) add(entity) } }.single()
}
