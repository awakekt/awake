/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A game's own capability, as a community package would ship one: a `beacon` component, the content
 * it reads from the project, and the system that runs it. It goes through the same decision as Core's.
 */
class SceneCapabilityTest {

    @Test
    fun aProjectRunsAGamesCapabilityWithItsComponentContentAndSystem() = runTest {
        // --8<-- [start:load]
        val project = loadProject(files(BEACON_SCENE), capabilities = listOf(BeaconCapability))
        val game = app { scene("play") { runProject(project) } }
        // --8<-- [end:load]
        game.ready(TestRenderer())
        val world = game.requireService<SceneAppLifecycleRuntime>().world
        val before = world.beacon().pulses

        repeat(FRAMES) { game.update(DELTA, WIDTH, HEIGHT) }

        assertEquals(PULSE * FRAMES, world.beacon().pulses - before, "the system ran each frame with the pulse the capability loaded")
    }

    /** A host that runs a scene in its own world gets the same systems, Core's first and the game's after. */
    @Test
    fun aHostRunsTheSameCapabilityAfterCoresAndClosesItsSystem() = runTest {
        val scene = decode(BEACON_SCENE)
        val content = loadSceneContent(scene, files(BEACON_SCENE), listOf(BeaconCapability))
        val systems = sceneSystemsFor(scene, services(content), listOf(BeaconCapability))
        val world = World().also { scene.instantiate(world = it) }

        assertIs<AnimationSystem>(systems.frame[systems.frame.size - 2], "Core's systems come first")
        val beacons = assertIs<BeaconSystem>(systems.frame.last())
        systems.frame.forEach { it.update(world, DELTA) }
        systems.close()

        assertEquals(PULSE, world.beacon().pulses)
        assertTrue(beacons.closed, "a system that holds something is closed when the scene stops")
    }

    @Test
    fun aSceneNamingAComponentNoCapabilityRegistersSaysWhichAndHowToFixIt() = runTest {
        val error = assertFailsWith<IllegalArgumentException> { loadProject(files(HOOK_SCENE)) }

        assertTrue("'grappling_hook'" in error.message.orEmpty(), error.message)
        assertTrue("scenes/main.scene.json" in error.message.orEmpty(), error.message)
        assertTrue("capability" in error.message.orEmpty(), error.message)
    }

    /** Naming where the capability is published does not load it: it is linked when the game is built. */
    @Test
    fun aRequiredPluginWithAnArtifactButNoLinkedCapabilityRefusesTheLoad() = runTest {
        val files = files(BEACON_SCENE, manifest = MANIFEST_REQUIRING_PUBLISHED_BEACON)

        val error = assertFailsWith<IllegalArgumentException> { loadProject(files) }
        val project = loadProject(files, capabilities = listOf(BeaconCapability))

        val message = error.message.orEmpty()
        assertTrue(BeaconCapability.id in message, message)
        assertTrue("com.example.beacon.BeaconCapability from com.example:beacon-capability:1.0.0" in message, message)
        assertEquals(BEACON_SCENE_NAME, project.scene.name)
    }

    @Test
    fun aRequiredPluginLoadsOnlyWithItsCapability() = runTest {
        val files = files(BEACON_SCENE, manifest = MANIFEST_REQUIRING_BEACON)

        val error = assertFailsWith<IllegalArgumentException> { loadProject(files) }
        val project = loadProject(files, capabilities = listOf(BeaconCapability))

        assertTrue(BeaconCapability.id in error.message.orEmpty(), error.message)
        assertEquals(BEACON_SCENE_NAME, project.scene.name)
    }

    @Test
    fun twoCapabilitiesWithOneIdOrTwoSystemsWithOneNameAreRefused() {
        val scene = decode(BEACON_SCENE)

        assertFailsWith<IllegalArgumentException> { sceneSystemsFor(scene, services(), listOf(BeaconCapability, BeaconCapability)) }
        val clash = assertFailsWith<IllegalArgumentException> { sceneSystemsFor(scene, services(), listOf(ClashingCapability)) }
        assertTrue("'animation'" in clash.message.orEmpty(), clash.message)
    }

    private fun decode(scene: String): SceneDocument {
        installProjectComponents(listOf(BeaconCapability))
        return SceneLoader.decode(scene)
    }

    private fun services(content: SceneContent = SceneContent.Empty) = SceneHostServices(
        input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
        renderer = NoopRenderer(),
        content = content,
    )

    private fun files(scene: String, manifest: String = MANIFEST) = AssetSource { path ->
        runCatching {
            mapOf(MANIFEST_PATH to manifest, "scenes/main.scene.json" to scene, PULSE_PATH to "$PULSE").getValue(path.value).encodeToByteArray()
        }
    }

    private fun World.beacon(): Beacon {
        var found: Beacon? = null
        queryEach(Beacon::class) { _, beacon -> found = beacon }
        return checkNotNull(found) { "no beacon was attached" }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 4
        const val PULSE = 3
        const val PULSE_PATH = "beacons/pulse.txt"
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val MANIFEST_REQUIRING_BEACON = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0",
            "entryScene":"scenes/main.scene.json",
            "plugins":[{"id":"com.example.harbor-town.beacon","path":"plugins/beacon.awakeplugin","version":"1.0.0","required":true}]}"""
        const val MANIFEST_REQUIRING_PUBLISHED_BEACON = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0",
            "entryScene":"scenes/main.scene.json",
            "plugins":[{"id":"com.example.harbor-town.beacon","path":"plugins/beacon.awakeplugin","version":"1.0.0","required":true,
              "artifact":{"group":"com.example","name":"beacon-capability","version":"1.0.0"},
              "capabilityClass":"com.example.beacon.BeaconCapability"}]}"""
        const val BEACON_SCENE_NAME = "harbor"
        const val BEACON_SCENE = """{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Lighthouse", "components": [ { "component": "lighthouse_beacon", "label": "north" } ] }
] }"""
        const val HOOK_SCENE = """{ "version": 1, "name": "cliff", "nodes": [
  { "name": "Player", "components": [ { "component": "grappling_hook", "range": 12.0 } ] }
] }"""
    }
}

/** A lighthouse beacon in a scene document. */
@Serializable
@SerialName("lighthouse_beacon")
private data class SceneBeacon(val label: String = "") : SceneComponent

/** The beacon on an entity: how many times it has pulsed. */
private class Beacon(val label: String) {
    var pulses = 0
}

private object BeaconBinding : SceneComponentBinding<Beacon, SceneBeacon> {
    override val componentClass: KClass<Beacon> = Beacon::class
    override val schemaClass: KClass<SceneBeacon> = SceneBeacon::class
    override val serializer = SceneBeacon.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneBeacon, context: SceneResolutionContext) {
        world.add(entity, Beacon(component.label))
    }

    override fun export(world: World, entity: Entity, component: Beacon): SceneBeacon = SceneBeacon(component.label)
}

/** Pulses every beacon by the step the project's files give, and records that it was closed. */
private class BeaconSystem(private val step: Int) : System, AutoCloseable {
    var closed = false

    override fun update(world: World, delta: Float) {
        world.queryEach(Beacon::class) { _, beacon -> beacon.pulses += step }
    }

    override fun close() {
        closed = true
    }
}

// --8<-- [start:capability]
private object BeaconCapability : SceneCapability {
    override val id = "com.example.harbor-town.beacon"
    override val components = listOf(BeaconBinding)

    val Pulse = SceneContentKey<Int>("beacon pulse")

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneBeacon::class)) content[Pulse] = files.read(AssetPath("beacons/pulse.txt")).getOrThrow().decodeToString().toInt()
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneBeacon::class)) plan.frame("beacons") { BeaconSystem(checkNotNull(it.content[Pulse])) }
    }
}
// --8<-- [end:capability]

/** A capability that names its system as one of Core's does. */
private object ClashingCapability : SceneCapability {
    override val id = "com.example.harbor-town.clash"

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        plan.frame("animation") { AnimationSystem() }
    }
}
