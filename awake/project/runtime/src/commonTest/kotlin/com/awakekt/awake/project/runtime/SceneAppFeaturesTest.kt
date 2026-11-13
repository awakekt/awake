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
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.SceneSystemRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A scene app that asks for features gets the systems, in the order, that the project runtime plans for them. */
class SceneAppFeaturesTest {
    private val gameplay = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    private fun viaProject(scene: String, extra: List<SceneCapability>, role: SceneHostRole): List<PlannedSystem> {
        installProjectComponents()
        val services = SceneHostServices(gameplay, NoopRenderer()).withHostRole(role)
        return sceneSystemsFor(SceneLoader.decode(scene), services, extra).use { it.planned }
    }

    private suspend fun viaApp(scene: String, features: List<SceneCapability>, role: SceneHostRole, before: Boolean = false): List<PlannedSystem> {
        installProjectComponents()
        val game = app {
            scene("play") {
                if (before) frameSystem("explicit-first") { Idle }
                features(SceneLoader.decode(scene), features, hostRole = role)
                if (!before) frameSystem("explicit-last") { Idle }
            }
        }
        game.ready(TestRenderer())
        return game.requireService<SceneAppLifecycleRuntime>().plannedSystems
    }

    @Test
    fun coreFeaturesGiveTheSamePlanAsAProjectForEveryHostRole() = runTest {
        for (role in SceneHostRole.entries) {
            val project = viaProject(RICH, emptyList(), role)
            assertEquals(project, viaApp(RICH, CoreFeatures.all, role).filter { !it.name.startsWith("explicit") }, "Core, $role")
        }
    }

    @Test
    fun aGamesOwnFeatureIsPlannedAfterCoresInTheSameOrder() = runTest {
        for (role in SceneHostRole.entries) {
            val project = viaProject(RICH, listOf(Game), role)
            assertEquals(project, viaApp(RICH, CoreFeatures.all + Game, role).filter { !it.name.startsWith("explicit") }, "game, $role")
            val names = project.map { it.name }
            assertTrue(names.indexOf("game-a") > names.indexOf("physics").coerceAtLeast(-1) && names.last().startsWith("game") == (role == SceneHostRole.Full), "game systems come after Core's: $names")
        }
    }

    @Test
    fun explicitSystemsRunInCallOrderRelativeToPlannedOnes() = runTest {
        val after = viaApp(RICH, CoreFeatures.all, SceneHostRole.Full).map { it.name }
        assertEquals("explicit-last", after.last())
        val first = viaApp(RICH, CoreFeatures.all, SceneHostRole.Full, before = true).map { it.name }
        assertEquals("explicit-first", first.first())
    }

    @Test
    fun aFeatureAskedForAloneGivesOnlyItsSystemsAndSharedIdsAreRefused() = runTest {
        val spin = viaApp(RICH, listOf(CoreFeatures.Motion), SceneHostRole.Full).map { it.name }
        assertEquals(listOf("keyframes", "explicit-last"), spin)
        assertFailsWith<IllegalArgumentException> { viaApp(RICH, listOf(CoreFeatures.Motion, CoreFeatures.Motion), SceneHostRole.Full) }
    }

    private object Game : SceneCapability {
        override val id = "com.example.harbor-town.game"
        override val systemNames = listOf("game-a", "game-b")

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.fixed("game-a") { Idle }
            plan.frame("game-b", SceneSystemRole.Presentation) { Idle }
        }
    }

    private object Idle : System {
        override fun update(world: World, delta: Float) = Unit
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
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
