/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A project opened with its own code the way an editor opens it: every scene, its prefabs, systems and saves. */
class ProjectOpenCheckTest {
    @Test
    fun aProjectWhoseScenesOpenWithItsCodeHasNoIssues() = runTest {
        val issues = checkProjectOpens(files(), SCENES, listOf(SignalCapability()), ::createJoltPhysicsWorld)

        assertEquals(emptyList(), issues)
    }

    @Test
    fun withoutItsCodeAScenesAndAPrefabsComponentsAreUnknownAtTheirNodes() = runTest {
        val issues = checkProjectOpens(files(), SCENES, physicsWorld = ::createJoltPhysicsWorld)

        assertEquals(
            listOf(
                ProjectOpenIssue(ProjectOpenIssueKind.UnknownComponent, "test_open_signal $NOT_REGISTERED", LEVEL, "Signal"),
                ProjectOpenIssue(ProjectOpenIssueKind.UnknownComponent, "test_open_signal $NOT_REGISTERED", LEVEL, "Spawner/Beacon"),
            ),
            issues.sortedBy { it.node },
        )
    }

    @Test
    fun aComponentThatReadsBackDifferentAfterASaveIsARoundTripIssue() = runTest {
        val texts = TEXTS + (LEVEL to LEVEL_SCENE.replace(SIGNAL_NODE, """{ "name": "Gauge", "components": [ { "component": "test_lossy", "level": 3 } ] }"""))

        val issues = checkProjectOpens(files(texts), SCENES, listOf(SignalCapability(), LossyCapability), ::createJoltPhysicsWorld)

        val issue = issues.single()
        assertEquals(ProjectOpenIssueKind.RoundTrip, issue.kind)
        assertEquals(LEVEL to "Gauge", issue.file to issue.node)
        assertTrue("SceneLossy(level=3) became SceneLossy(level=0)" in issue.message, issue.message)
    }

    @Test
    fun aCapabilityThatFailsToLoadOrStepIsReportedForItsScene() = runTest {
        val loads = checkProjectOpens(files(), SCENES, listOf(SignalCapability(failLoad = true)), ::createJoltPhysicsWorld)
        val steps = checkProjectOpens(files(), SCENES, listOf(SignalCapability(failStep = true)), ::createJoltPhysicsWorld)

        assertEquals(listOf(ProjectOpenIssueKind.SceneLoad to LEVEL), loads.map { it.kind to it.file })
        assertTrue("the signal won't load" in loads.single().message, loads.single().message)
        assertEquals(listOf(ProjectOpenIssueKind.SceneSystems to LEVEL), steps.map { it.kind to it.file })
    }

    @Test
    fun aMissingSceneAndAProjectThatDoesNotLoadAreReported() = runTest {
        val missing = checkProjectOpens(files(), SCENES + "scenes/missing.scene.json", listOf(SignalCapability()), ::createJoltPhysicsWorld)
        val noPhysics = checkProjectOpens(files(), SCENES, listOf(SignalCapability()))

        assertEquals(listOf(ProjectOpenIssueKind.SceneLoad to "scenes/missing.scene.json"), missing.map { it.kind to it.file })
        assertEquals(ProjectOpenIssueKind.ProjectLoad to PROJECT_MANIFEST, noPhysics.first().kind to noPhysics.first().file)
        assertTrue("physicsWorld" in noPhysics.first().message, noPhysics.first().message)
    }

    private fun files(texts: Map<String, String> = TEXTS) = AssetSource { path -> runCatching { texts.getValue(path.value).encodeToByteArray() } }

    private companion object {
        const val MENU = "scenes/menu.scene.json"
        const val LEVEL = "scenes/level.scene.json"
        val SCENES = listOf(LEVEL, MENU)
        const val NOT_REGISTERED = "is a component none of the project's capabilities registers"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""
        const val SIGNAL_NODE = """{ "name": "Signal", "components": [ { "component": "test_open_signal", "signal": "data/level.signal" } ] }"""

        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "Floor", "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 5.0, "y": 0.1, "z": 5.0 } } } ] }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } },
    "components": [ { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 0.5, "z": 0.5 } }, "motion": "DYNAMIC" } ] },
  { "name": "Spawner", "components": [ { "component": "prefab_link", "path": "prefabs/beacon.prefab.json" } ] },
  $SIGNAL_NODE
] }
"""

        const val BEACON_PREFAB = """
{ "guid": "6f1c2a40-beac-4e00-9000-000000000001", "name": "Beacon",
  "root": { "name": "Beacon", "components": [ { "component": "test_open_signal", "signal": "data/level.signal" } ] } }
"""

        val TEXTS = mapOf(
            PROJECT_MANIFEST to MANIFEST,
            MENU to MENU_SCENE,
            LEVEL to LEVEL_SCENE,
            "prefabs/beacon.prefab.json" to BEACON_PREFAB,
            "data/level.signal" to "ping",
        )

        val SIGNAL = SceneContentKey<String>("test open signal")
    }

    /** A game's capability: a component, content it reads from the project, and a system. */
    private class SignalCapability(private val failLoad: Boolean = false, private val failStep: Boolean = false) : SceneCapability {
        override val id = "com.example.harbor-town.open-signal"
        override val components = listOf(OpenSignalBinding)

        override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
            if (!scene.uses(SceneOpenSignal::class)) return
            require(!failLoad) { "the signal won't load" }
            content[SIGNAL] = files.read(AssetPath("data/level.signal")).getOrThrow().decodeToString()
        }

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            if (!scene.uses(SceneOpenSignal::class)) return
            plan.fixed("open-signal") { services ->
                checkNotNull(services.content[SIGNAL])
                object : System {
                    override fun update(world: World, delta: Float) {
                        check(!failStep) { "the signal fails its step" }
                    }
                }
            }
        }
    }

    /** A capability whose one component saves less than it read. */
    private object LossyCapability : SceneCapability {
        override val id = "com.example.harbor-town.lossy"
        override val components = listOf(LossyBinding)

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = Unit
    }
}

@Serializable
@SerialName("test_open_signal")
private data class SceneOpenSignal(val signal: String = "") : SceneComponent

private class OpenSignal(val signal: String)

private object OpenSignalBinding : SceneComponentBinding<OpenSignal, SceneOpenSignal> {
    override val componentClass: KClass<OpenSignal> = OpenSignal::class
    override val schemaClass: KClass<SceneOpenSignal> = SceneOpenSignal::class
    override val serializer = SceneOpenSignal.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneOpenSignal, context: SceneResolutionContext) {
        world.add(entity, OpenSignal(component.signal))
    }

    override fun export(world: World, entity: Entity, component: OpenSignal): SceneOpenSignal = SceneOpenSignal(component.signal)
}

/** A component whose serializer reads its level and always writes 0. */
@Serializable(with = LossySerializer::class)
private data class SceneLossy(val level: Int = 0) : SceneComponent

private object LossySerializer : KSerializer<SceneLossy> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("test_lossy") { element<Int>("level", isOptional = true) }

    override fun serialize(encoder: Encoder, value: SceneLossy) = encoder.encodeStructure(descriptor) { encodeIntElement(descriptor, 0, 0) }

    override fun deserialize(decoder: Decoder): SceneLossy = decoder.decodeStructure(descriptor) {
        var level = 0
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> level = decodeIntElement(descriptor, 0)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index $index")
            }
        }
        SceneLossy(level)
    }
}

private class Lossy(val level: Int)

private object LossyBinding : SceneComponentBinding<Lossy, SceneLossy> {
    override val componentClass: KClass<Lossy> = Lossy::class
    override val schemaClass: KClass<SceneLossy> = SceneLossy::class
    override val serializer = LossySerializer

    override fun attachTyped(world: World, entity: Entity, component: SceneLossy, context: SceneResolutionContext) {
        world.add(entity, Lossy(component.level))
    }

    override fun export(world: World, entity: Entity, component: Lossy): SceneLossy = SceneLossy(component.level)
}
