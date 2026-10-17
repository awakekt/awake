/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.btree.behaviorTree
import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.nodegraph.NodeGraphJson
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorHooksTest {
    private val registry = AiRegistry()

    @Test
    fun aTreeReportsWhatEachNodeReturnedOnTheLatestTick() {
        val tree = treeOf(
            node(
                "ai.selector",
                "{}",
                node("ai.has", """{"key":"door"}""", id = "door?"),
                node("ai.sequence", "{}", node("ai.wait", """{"seconds":1}"""), node("ai.status", """{"status":"success"}""")),
            ),
        )
        val h = Harness()
        h.tick(tree)
        assertEquals(BehaviorStatus.FAILURE, tree.statusOf(tree.pathOf("door?")!!))
        assertEquals(listOf("0", "0.1", "0.1.0"), tree.runningPaths())
        assertNull(tree.statusOf("0.1.1"))
        assertEquals(NodeKindId("ai.wait"), tree.nodes.single { it.path == "0.1.0" }.kind)

        h.blackboard["door"] = true
        h.tick(tree)
        // The selector resumes its running child, so the condition is not asked this tick.
        assertNull(tree.statusOf("0.0"))
        assertEquals(2L, tree.tickCount)
    }

    @Test
    fun aKotlinTreeIsWalkedThroughItsChildren() {
        val tree = BehaviorTree.of(behaviorTree { sequence { condition { true }; action { BehaviorStatus.RUNNING } } })
        assertEquals(listOf("0", "0.0", "0.1"), tree.nodes.map { it.path })
        Harness().tick(tree)
        assertEquals(BehaviorStatus.RUNNING, tree.statusOf("0"))
    }

    @Test
    fun aMachineExposesTheTreeOfItsCurrentState() {
        val machine = registry.compileStateMachine(
            StateMachineDefinition(initial = "work", states = listOf(StateDefinition("work", tree = node("ai.wait", """{"seconds":1}""")))),
        ).newStateMachine()
        Harness().update(machine)
        assertEquals(BehaviorStatus.RUNNING, machine.currentTree()!!.statusOf("0"))
    }

    @Test
    fun parameterSchemasDescribeEditorForms() {
        val chance = registry.parameterSchema(NodeKindId("ai.chance"))
        val probability = chance.children.single { it.name == "probability" }
        assertEquals(PropertyKind.Float, probability.kind)
        assertTrue(probability.required)
        assertEquals(1.0, probability.constraints.range!!.max)
        assertEquals(JsonPrimitive(0L), chance.children.single { it.name == "seed" }.default)

        val repeat = registry.parameterSchema(NodeKindId("ai.repeat")).children.single()
        assertEquals(JsonPrimitive(-1), repeat.default)
        assertEquals(PropertyKind.Enum, registry.parameterSchema(NodeKindId("ai.parallel")).children.single().kind)
        assertEquals(emptyList(), registry.parameterSchema(StateKindId.Plain).children)
    }

    @Test
    fun aTreeEditsAsANodeGraphAndComesBackEqual() {
        val root = node(
            "ai.selector",
            "{}",
            node("ai.compare", """{"key":"heat","op":">=","value":30}""", id = "hot"),
            NodeDefinition(NodeKindId("ai.wait"), params = params("""{"seconds":2}"""), layout = NodeLayout(40f, 80f)),
        )
        val graph = root.toNodeGraph()
        val nodeRegistry = registry.behaviorTreeNodeRegistry()
        assertEquals(emptyList(), nodeRegistry.validate(graph))
        assertEquals(listOf("0", "hot", "0.1"), graph.nodes.map { it.id })
        assertEquals(root, NodeGraphJson.load(NodeGraphJson.encode(graph), nodeRegistry).toNodeDefinition())

        val waitSpec = nodeRegistry["ai.wait"]!!
        assertEquals(listOf("seconds"), waitSpec.config.map { it.name })
        assertEquals(emptyList(), waitSpec.outputs)
        assertEquals("Composite", nodeRegistry["ai.sequence"]!!.category)
        assertEquals(registry.nodeKinds.size, nodeRegistry.catalogue().nodes.size)
    }
}
