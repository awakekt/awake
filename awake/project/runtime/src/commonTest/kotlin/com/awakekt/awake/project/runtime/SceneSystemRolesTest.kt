/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.PlannedSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneHostRole
import com.awakekt.awake.scene.runtime.SceneSystemRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A system has a role and a host has a role; a host plans only the systems its role runs. */
class SceneSystemRolesTest {
    private val gameplay = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    private fun planned(host: SceneHostServices, scene: String = RICH, extra: List<SceneCapability> = emptyList()): List<PlannedSystem> {
        installProjectComponents()
        return sceneSystemsFor(SceneLoader.decode(scene), host, extra).use { it.planned }
    }

    private val client get() = SceneHostServices(gameplay, NoopRenderer())
    private val headless get() = SceneHostServices.headless(gameplay)
    private val server get() = headless.withHostRole(SceneHostRole.SimulationOnly)

    @Test
    fun aClientRunsEveryRoleAndAServerHostRunsTheSimulation() {
        val all = planned(client)
        val simulated = planned(server)

        assertEquals(all.filter { it.role == SceneSystemRole.Simulation }, simulated.filter { it.role == SceneSystemRole.Simulation })
        assertTrue(simulated.none { it.role == SceneSystemRole.Presentation }, "no presentation system on a server: $simulated")
        assertTrue(all.any { it.role == SceneSystemRole.Presentation }, "the positive control: a client has some")
    }

    @Test
    fun theHostRoleIsFullUnlessSetSoAHeadlessHostPlansWhatItAlwaysDid() {
        assertEquals(SceneHostRole.Full, client.hostRole)
        assertEquals(SceneHostRole.Full, headless.hostRole)
        assertTrue(planned(headless).any { it.name == "playerInput" }, "a headless host still plans the presentation systems it always did")
        assertEquals(SceneHostRole.SimulationOnly, server.hostRole)
        // The role alone decides presentation systems; what also needs a renderer is left out of both lists.
        val drawn = setOf("canvas-actions", "particle-content", "particles")
        assertEquals(planned(server), planned(client.withHostRole(SceneHostRole.SimulationOnly)).filter { it.name !in drawn })
        assertEquals(planned(client).filter { it.name !in drawn }, planned(headless))
    }

    @Test
    fun coreMarksItsPresentationSystemsAndLeavesTheRestSimulation() {
        val roles = planned(client).associate { it.name to it.role }
        assertEquals(
            setOf("playerInput", "canvas-actions", "particle-content", "particles", "day-cycle"),
            roles.filterValues { it == SceneSystemRole.Presentation }.keys,
        )
        // The camera places the basis movement reads, so a host that simulates keeps it.
        assertEquals(SceneSystemRole.Simulation, roles.getValue("camera"))
        assertEquals(SceneSystemRole.Simulation, roles.getValue("movement"))
        assertEquals(SceneSystemRole.Simulation, roles.getValue("animation"))
        // It writes the transform colliders, triggers and the network follow; only its alpha track is visual.
        assertEquals(SceneSystemRole.Simulation, roles.getValue("keyframes"))
    }

    @Test
    fun aGamesUnmarkedSystemRunsOnEveryHostAndAMarkedOneOnlyWhereItsRoleRuns() {
        val extra = listOf(Marked)
        assertEquals(listOf("unmarked", "shown"), planned(client, PLAIN, extra).map { it.name }.filter { it in MARKED })
        assertEquals(listOf("unmarked"), planned(server, PLAIN, extra).map { it.name }.filter { it in MARKED })
        assertEquals(SceneSystemRole.Simulation, planned(server, PLAIN, extra).first { it.name == "unmarked" }.role)
    }

    @Test
    fun disablingASystemAHostDoesNotRunIsNotAnError() {
        installProjectComponents()
        sceneSystemsFor(SceneLoader.decode(RICH), server, emptyList(), setOf(CoreSystems.PLAYER_INPUT)).use {
            assertTrue(it.planned.none { system -> system.name == "playerInput" })
        }
    }

    @Test
    fun theSceneAppFiltersByTheSameRolesAndListsTheSamePlan() = runTest {
        val extra = listOf(Marked)
        for (host in SceneHostRole.entries) {
            val services = if (host == SceneHostRole.Full) client else server
            val viaProject = planned(services, RICH, extra)
            val game = app {
                scene("play") {
                    hostRole(host)
                    val everything = planned(client, RICH, extra)
                    everything.forEach { system(it.name, it.phase, it.role) { NoopSystem } }
                }
            }
            game.ready(TestRenderer())
            assertEquals(viaProject, game.requireService<SceneAppLifecycleRuntime>().plannedSystems, "host $host")
        }
    }

    private object Marked : SceneCapability {
        override val id = "com.example.harbor-town.roles"
        override val systemNames = MARKED

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.frame("unmarked") { NoopSystem }
            plan.frame("shown", SceneSystemRole.Presentation) { NoopSystem }
        }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private object NoopSystem : System {
        override fun update(world: World, delta: Float) = Unit
    }

    private companion object {
        val MARKED = listOf("unmarked", "shown")
        const val PLAIN = """{ "version": 1, "name": "still", "nodes": [ { "name": "Rock", "components": [] } ] }"""
        const val RICH = """
{ "version": 1, "name": "rich", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Player", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } } ] },
  { "name": "Player", "components": [ { "component": "movement_control", "speed": 6.0 },
    { "component": "keyframe_animation", "duration": 1.0,
      "position": [ { "time": 0.0, "value": { "x": 0.0, "y": 0.0, "z": 0.0 } },
                    { "time": 1.0, "value": { "x": 4.0, "y": 0.0, "z": 0.0 } } ] } ] },
  { "name": "Sun", "components": [
    { "component": "light", "type": "Directional" },
    { "component": "day_cycle", "dayLengthSeconds": 4.0, "time": 0.25 } ] }
] }
"""
    }
}
