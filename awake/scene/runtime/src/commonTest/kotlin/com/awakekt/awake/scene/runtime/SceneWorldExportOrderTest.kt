/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import kotlin.test.Test
import kotlin.test.assertEquals

/** `SceneLoader.fromWorld` writes siblings in the order a host gives it, and otherwise as the world stores them. */
class SceneWorldExportOrderTest {
    private val world = World()
    private val gate = node("Gate")
    private val market = node("Market")
    private val pier = node("Pier")
    private val stall = node("Stall", parent = market)
    private val lamp = node("Lamp", parent = market)
    private val crate = node("Crate", parent = market)

    init {
        node("Awning", parent = stall)
    }

    /** The control: with no order, roots and children are written as their `Transform`s were added. */
    @Test
    fun withNoOrderSiblingsAreWrittenAsTheWorldStoresThem() {
        assertEquals(listOf("Gate", "Market[Stall[Awning], Lamp, Crate]", "Pier"), outline(SceneLoader.fromWorld(world)))
    }

    @Test
    fun aSiblingOrderReordersTheRootsAndEachNodesChildren() {
        val ranks = listOf(pier, gate, market, crate, lamp, stall)
        val order = compareBy<Entity> { ranks.indexOf(it) }

        val document = SceneLoader.fromWorld(world, siblingOrder = order)

        assertEquals(listOf("Pier", "Gate", "Market[Crate, Lamp, Stall[Awning]]"), outline(document))
    }

    @Test
    fun siblingsTheOrderCallsEqualKeepTheWorldsOrder() {
        // Only the pier and the crate are moved to the front; every other pair compares equal.
        val first = setOf(pier, crate)
        val order = compareBy<Entity> { if (it in first) 0 else 1 }

        val document = SceneLoader.fromWorld(world, siblingOrder = order)

        assertEquals(listOf("Pier", "Gate", "Market[Crate, Stall[Awning], Lamp]"), outline(document))
    }

    @Test
    fun extraComponentsMeetsTheNodesInTheOrderTheDocumentListsThem() {
        val asked = ArrayList<String>()
        val ranks = listOf(pier, market, gate, lamp, crate, stall)

        SceneLoader.fromWorld(
            world,
            extraComponents = { entity -> emptyList<Nothing>().also { asked += nameOf(entity) } },
            siblingOrder = compareBy { ranks.indexOf(it) },
        )

        assertEquals(listOf("Pier", "Market", "Lamp", "Crate", "Stall", "Awning", "Gate"), asked)
    }

    private fun node(name: String, parent: Entity? = null): Entity = world.create().also {
        world.add(it, Name(name))
        world.add(it, Transform(parent = parent))
    }

    private fun nameOf(entity: Entity): String = world.get<Name>(entity)?.value.orEmpty()

    private fun outline(document: SceneDocument): List<String> = document.nodes.map(::outline)

    private fun outline(node: SceneNode): String =
        if (node.children.isEmpty()) node.name.orEmpty() else "${node.name}[${node.children.joinToString(", ", transform = ::outline)}]"
}
