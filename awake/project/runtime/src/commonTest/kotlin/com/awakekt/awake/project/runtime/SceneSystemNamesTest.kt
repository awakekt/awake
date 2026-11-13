/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
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
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Systems have stable names a host can list and disable, the same in the project runtime and the scene app. */
class SceneSystemNamesTest {
    private val gameplay = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    private fun plan(scene: String, drawn: Boolean, disabled: Set<String> = emptySet()): SceneSystemSet {
        installProjectComponents()
        val services = if (drawn) SceneHostServices(gameplay, NoopRenderer()) else SceneHostServices.headless(gameplay)
        return sceneSystemsFor(SceneLoader.decode(scene), services, emptyList(), disabled)
    }

    private fun SceneSystemSet.names() = planned.map { it.name }

    @Test
    fun coreSystemNamesAreStable() {
        assertEquals(CORE_NAMES, CoreSystems.all)
        assertEquals(CoreSystems.all.size, CoreSystems.all.toSet().size, "a name is used once")
    }

    @Test
    fun theListMatchesWhatWasBuiltAndKeepsPhaseOrder() {
        plan(SCENE, drawn = true).use { set ->
            assertEquals(set.fixed.size + set.preAnimation.size + set.frame.size, set.planned.size)
            assertEquals(set.planned.map { it.phase }, set.planned.map { it.phase }.sortedBy { PHASE_ORDER.indexOf(it) })
        }
    }

    @Test
    fun aClientAHeadlessAndAServerHostPlanDifferentLists() {
        val client = plan(SCENE, drawn = true).also { it.close() }.names()
        val headless = plan(SCENE, drawn = false).also { it.close() }.names()
        val server = plan(SCENE, drawn = false, setOf(CoreSystems.CAMERA, CoreSystems.PLAYER_INPUT, CoreSystems.SPIN))
            .also { it.close() }.names()

        assertEquals(listOf("playerInput", "canvas-actions", "camera", "spin-clock", "spin", "particle-content", "particles", "animation"), client)
        assertEquals(listOf("playerInput", "camera", "spin-clock", "spin", "animation"), headless)
        assertEquals(listOf("spin-clock", "animation"), server)
    }

    @Test
    fun disablingNothingPlansWhatTheOverloadWithoutItPlans() {
        installProjectComponents()
        val services = SceneHostServices(gameplay, NoopRenderer())
        val without = sceneSystemsFor(SceneLoader.decode(SCENE), services).also { it.close() }
        assertEquals(without.planned, plan(SCENE, drawn = true).also { it.close() }.planned)
    }

    @Test
    fun anUnknownNameIsRefusedWithTheKnownOnes() {
        val failure = assertFailsWith<IllegalArgumentException> { plan(SCENE, drawn = true, setOf("camra")) }
        val message = failure.message.orEmpty()
        assertTrue("'camra'" in message, message)
        assertTrue("camera" in message && "spin" in message, "the known names are listed: $message")
    }

    @Test
    fun aCoreNameTheSceneDoesNotPlanIsKnownNotATypo() {
        plan(SCENE, drawn = true, setOf(CoreSystems.AUDIO, CoreSystems.PHYSICS)).use { assertTrue(CoreSystems.AUDIO !in it.names()) }
    }

    @Test
    fun aGamesOwnSystemsAreKnownByTheNamesItsFeatureLists() {
        installProjectComponents()
        val services = SceneHostServices.headless(gameplay)
        val beaconed = SceneLoader.decode(BEACONED)
        sceneSystemsFor(beaconed, services, listOf(Beacons)).use { assertTrue("beacons" in it.names()) }
        sceneSystemsFor(beaconed, services, listOf(Beacons), setOf("beacons")).use { assertTrue("beacons" !in it.names()) }
        // A scene that plans none of them still knows the name, so it is not taken for a typo.
        sceneSystemsFor(SceneLoader.decode(PLAIN), services, listOf(Beacons), setOf("beacons")).use { assertTrue("beacons" !in it.names()) }
    }

    @Test
    fun runProjectDisablesByNameAndListsWhatItPlanned() = runTest {
        val project = loadProject(files(SCENE))
        val game = app { scene("play") { runProject(project, setOf(CoreSystems.CAMERA, CoreSystems.SPIN)) } }
        game.ready(TestRenderer())
        game.update(DELTA, WIDTH, HEIGHT)
        val planned = game.requireService<ProjectSystemPlan>().planned.map { it.name }
        assertEquals(listOf("playerInput", "canvas-actions", "spin-clock", "particle-content", "particles", "animation"), planned)
    }

    @Test
    fun runProjectRefusesAnUnknownNameAtStart() = runTest {
        val project = loadProject(files(SCENE))
        val failure = assertFailsWith<IllegalArgumentException> {
            app { scene("play") { runProject(project, setOf("camra")) } }.ready(TestRenderer())
        }
        assertTrue("'camra'" in failure.message.orEmpty() && "camera" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun theSceneAppDisablesAndListsTheSameWay() = runTest {
        val game = app {
            scene("play") {
                frameSystem("playerInput") { noop() }
                fixedSystem("physics") { noop() }
                frameSystem("camera") { noop() }
                disableSystems("camera")
            }
        }
        game.ready(TestRenderer())
        val planned = game.requireService<SceneAppLifecycleRuntime>().plannedSystems
        assertEquals(listOf(PlannedSystem("physics", SceneSystemPhase.Fixed), PlannedSystem("playerInput", SceneSystemPhase.Frame)), planned)
        val failure = assertFailsWith<IllegalArgumentException> {
            app { scene("play") { frameSystem("camera") { noop() }; disableSystems("camra") } }.ready(TestRenderer())
        }
        assertTrue("'camra'" in failure.message.orEmpty() && "Known systems: camera" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun theSceneAppAndTheProjectRuntimeListNamesAndPhasesAlike() = runTest {
        val viaProject = plan(SCENE, drawn = true).also { it.close() }.planned
        val game = app {
            scene("play") { viaProject.forEach { system(it.name, it.phase, it.role) { noop() } } }
        }
        game.ready(TestRenderer())
        assertEquals(viaProject, game.requireService<SceneAppLifecycleRuntime>().plannedSystems)
    }

    private fun noop(): System = NoopSystem

    private object Beacons : SceneFeature {
        override val id = "com.example.harbor-town.beacons"
        override val systemNames = listOf("beacons")

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            if (scene.name == "beaconed") plan.frame("beacons") { NoopSystem }
        }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf("awake.project.json" to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        val PHASE_ORDER = listOf(SceneSystemPhase.Fixed, SceneSystemPhase.PreAnimation, SceneSystemPhase.Frame)
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val BEACONED = """{ "version": 1, "name": "beaconed", "nodes": [ { "name": "Rock", "components": [] } ] }"""
        const val PLAIN = """{ "version": 1, "name": "still", "nodes": [ { "name": "Rock", "components": [] } ] }"""
        const val SCENE = """
{ "version": 1, "name": "look", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "primary": true },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Spinner", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } } ] },
  { "name": "Spinner", "components": [ { "component": "spin_control", "speed": 2.0 } ] }
] }
"""
        val CORE_NAMES = listOf(
            "playerInput", "canvas-actions", "movement", "camera", "occluder-fade", "canvas-occlusion", "paged-terrain",
            "terrain", "terrain-collider", "mesh-collider", "physics", "character-sensor-bodies", "character",
            "navigation-agents", "ai-intents", "ai-perception", "ai-definitions", "ai-trees", "ai-machines", "ai-patrol",
            "ai-chase", "ai-flee", "path-requests", "spin-clock", "spin", "locomotion", "keyframes", "texture-clips",
            "sprite-clips", "particles-2d", "particle-content", "particles", "day-cycle", "shader-effects",
            "audio-listener", "audio", "animation",
        )
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
