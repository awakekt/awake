/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ai.definition.AiDefinitionJson
import com.awakekt.awake.ai.definition.AiRegistry
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Perception configured entirely as data: the faction table, the sentry's perception settings
 * (including its own blackboard key names) and its tree are JSON. The test code only places
 * entities, as a scene would.
 */
class DataOnlyPerceptionTest {
    private val factionsJson = """
        {
          "relations": [
            { "from": "rangers", "to": "raiders", "relation": "hostile" },
            { "from": "rangers", "to": "traders", "relation": "friendly" }
          ],
          "otherwise": "neutral"
        }
    """.trimIndent()

    private val sentryJson = """
        {
          "sight": { "focusRange": 30, "focusAngle": 40, "peripheralRange": 8, "peripheralAngle": 160, "lineOfSight": false },
          "hearing": { "range": 12, "muffledBy": 0 },
          "forgetAfter": 5,
          "updateInterval": 0.5,
          "keys": { "target": "foe", "targetPosition": "foe.at", "seesHostile": "foe.seen", "hostilesInSight": null }
        }
    """.trimIndent()

    private val treeJson = """
        {
          "root": { "kind": "ai.selector", "children": [
            { "kind": "ai.sequence", "children": [
              { "kind": "ai.compare", "params": { "key": "foe.seen", "op": "==", "value": true } },
              { "kind": "ai.add", "params": { "key": "warnings", "amount": 1 } }
            ] },
            { "kind": "ai.sequence", "children": [
              { "kind": "ai.perception.sees", "params": { "relation": "friendly" } },
              { "kind": "ai.set", "params": { "key": "greeted", "value": true } }
            ] }
          ] }
        }
    """.trimIndent()

    @Test
    fun aSentryConfiguredAsDataWarnsOfRaidersAndGreetsTraders() {
        val factions = Json.decodeFromString(FactionTable.serializer(), factionsJson)
        val settings = Json.decodeFromString(PerceptionSettings.serializer(), sentryJson)
        assertEquals(Relation.Hostile, factions.relation(FactionId("raiders"), FactionId("rangers")))

        val scene = PerceptionScene()
        val sentry = scene.spawn(0f, 0f, faceX = 0f, faceZ = 1f)
        val trader = scene.spawn(1f, 5f)
        val raider = scene.spawn(0f, 100f)
        scene.world.add(sentry, Perceiver(FactionId("rangers"), settings))
        scene.world.add(sentry, AiRegistry().compileTree(AiDefinitionJson.Default.decodeTree(treeJson)).newComponent())
        scene.world.add(trader, Perceivable(FactionId("traders")))
        scene.world.add(raider, Perceivable(FactionId("raiders")))
        val perception = PerceptionSystem(scene, factions)
        val behaviour = BehaviorTreeSystem()
        val board = scene.world.get<BehaviorTreeComponent>(sentry)!!.blackboard
        fun step() {
            perception.update(scene.world, 0.5f)
            behaviour.update(scene.world, 0.5f)
        }

        step()
        assertEquals(true, board.get<Boolean>("greeted"))
        assertEquals(false, board.get<Boolean>("foe.seen"))
        scene.place(raider, 2f, 25f)
        repeat(3) { step() }
        assertEquals(raider, board.get("foe"))
        assertEquals(3, board.get<Int>("warnings"))
        assertEquals(false, board.contains("perception.hostiles"))
        assertEquals(settings, Json.decodeFromString(PerceptionSettings.serializer(), Json.encodeToString(PerceptionSettings.serializer(), settings)))
    }
}
