/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRecord
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.NodeGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Load Scene asks the host to play another scene, and where none can, says so once and carries on. */
class LoadSceneNodeTest {
    private val world = World()
    private val graphs = HashMap<String, NodeGraph>()
    private val system = BlueprintSystem(graphs = { graphs.getValue(it) })

    init {
        graphs[EXIT] = graph {
            node("start", "event.start")
            node("load", SceneNodes.LoadScene.TYPE, SceneNodes.LoadScene.SCENE to LEVEL)
            node("left", "var.set.float", "name" to "left", "value" to 1f)
            wire("start.then", "load.exec")
            wire("load.then", "left.exec")
        }
    }

    private fun blueprint(path: String): Entity = world.create().also { world.add(it, BlueprintComponent(path)) }

    private fun left(entity: Entity): Any? = world.get<BlueprintComponent>(entity)?.instance?.variable("left")

    @Test
    fun loadSceneAsksTheHostForTheSceneItNamesAndCarriesOn() {
        val asked = mutableListOf<String>()
        system.scene.loadScene = { path -> asked += path }
        val exit = blueprint(EXIT)

        system.update(world, STEP)

        assertEquals(listOf(LEVEL), asked)
        assertEquals(1f, left(exit), "the chain goes on after it")
    }

    @Test
    fun whereNothingSwitchesScenesItWarnsOnceAndCarriesOn() {
        val warnings = Warnings()
        val exits = List(2) { blueprint(EXIT) }
        Log.install(warnings)
        try {
            repeat(2) { system.update(world, STEP) }
        } finally {
            Log.remove(warnings)
        }

        val warning = warnings.records.single()
        assertTrue(LEVEL in warning.message, warning.message)
        exits.forEach { assertEquals(1f, left(it), "the chain goes on without a switch") }
    }

    @Test
    fun aLoadSceneNamingNoSceneAsksForNone() {
        val asked = mutableListOf<String>()
        system.scene.loadScene = { path -> asked += path }
        graphs["nowhere"] = graph {
            node("start", "event.start")
            node("load", SceneNodes.LoadScene.TYPE)
            wire("start.then", "load.exec")
        }
        blueprint("nowhere")

        system.update(world, STEP)

        assertEquals(emptyList(), asked)
    }

    private class Warnings : LogSink {
        val records = mutableListOf<LogRecord>()

        override fun emit(record: LogRecord) {
            if (record.level == LogLevel.Warn) records += record
        }
    }

    private companion object {
        const val EXIT = "exit"
        const val LEVEL = "scenes/level-2.scene.json"
    }
}
