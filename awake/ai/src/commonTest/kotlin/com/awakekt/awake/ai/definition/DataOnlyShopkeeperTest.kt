/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A shopkeeper authored entirely as data: a JSON state machine that uses only Core's kinds, loaded
 * into a registry with nothing registered by the game. The game's only code sets the hour and a
 * waiting customer on the blackboard and reads the events.
 */
class DataOnlyShopkeeperTest {
    private val shopkeeper = """
        {
          "formatVersion": 1,
          "id": "corner-shop",
          "initial": "closed",
          "blackboard": { "stock": 2, "hour": 8 },
          "states": [
            { "name": "closed" },
            {
              "name": "open",
              "enter": [ { "kind": "ai.emit", "params": { "event": "shop.opened" } } ],
              "tree": {
                "kind": "ai.sequence",
                "children": [
                  { "kind": "ai.has", "params": { "key": "customer" } },
                  { "kind": "ai.compare", "params": { "key": "stock", "op": ">", "value": 0 } },
                  { "kind": "ai.add", "params": { "key": "stock", "amount": -1 } },
                  { "kind": "ai.remove", "params": { "key": "customer" } },
                  { "kind": "ai.emit", "params": { "event": "shop.sold" } }
                ]
              }
            },
            {
              "name": "restocking",
              "enter": [ { "kind": "ai.emit", "params": { "event": "shop.restocking" } } ],
              "tree": {
                "kind": "ai.sequence",
                "children": [
                  { "kind": "ai.wait", "params": { "seconds": 2 } },
                  { "kind": "ai.set", "params": { "key": "stock", "value": 2 } }
                ]
              },
              "loopTree": false
            }
          ],
          "transitions": [
            {
              "from": "closed", "to": "open",
              "condition": { "kind": "ai.sequence", "children": [
                { "kind": "ai.compare", "params": { "key": "hour", "op": ">=", "value": 9 } },
                { "kind": "ai.compare", "params": { "key": "hour", "op": "<", "value": 17 } }
              ] }
            },
            {
              "from": "open", "to": "closed",
              "condition": { "kind": "ai.inverter", "children": [ { "kind": "ai.compare", "params": { "key": "hour", "op": "<", "value": 17 } } ] }
            },
            {
              "from": "open", "to": "restocking",
              "condition": { "kind": "ai.compare", "params": { "key": "stock", "op": "<=", "value": 0 } }
            },
            { "from": "restocking", "to": "open", "onTree": "success" }
          ]
        }
    """.trimIndent()

    @Test
    fun theShopOpensSellsRestocksAndCloses() {
        val compiled = AiRegistry().compileStateMachine(AiDefinitionJson.Default.decodeStateMachine(shopkeeper))
        val machine = compiled.newStateMachine()
        val h = Harness(compiled.newBlackboard())
        fun step(updates: Int = 1, customer: Boolean = false) = repeat(updates) {
            if (customer) h.blackboard["customer"] = true
            h.update(machine)
        }

        step(2)
        assertEquals("closed", machine.currentState.name)
        h.blackboard["hour"] = 9
        step()
        assertEquals("open", machine.currentState.name)
        step(2, customer = true) // Two sales empty the shelf.
        step() // Out of stock.
        assertEquals("restocking", machine.currentState.name)
        step(8, customer = true) // Two seconds restocking; the customer waits.
        step()
        assertEquals("open", machine.currentState.name)
        step() // The waiting customer is served.
        h.blackboard["hour"] = 17
        step()
        assertEquals("closed", machine.currentState.name)

        assertEquals(1, h.blackboard.get<Int>("stock"))
        assertEquals(
            listOf("shop.opened", "shop.sold", "shop.sold", "shop.restocking", "shop.opened", "shop.sold"),
            h.events.drain().map { it.event.value },
        )
    }

    @Test
    fun theDataSurvivesARoundTrip() {
        val codec = AiDefinitionJson.Default
        val definition = codec.decodeStateMachine(shopkeeper)
        assertEquals(definition, codec.decodeStateMachine(codec.encode(definition)))
        assertEquals(emptyList(), AiRegistry().validate(definition))
    }
}
