/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.example.harbortown

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.project.runtime.SceneCapability
import com.awakekt.awake.project.runtime.SceneContent
import com.awakekt.awake.project.runtime.SceneContentKey
import com.awakekt.awake.project.runtime.SceneSystemPlan
import com.awakekt.awake.project.runtime.uses
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** A signal lamp: a node that reads a signal from the project's files and shows it. */
@Serializable
@SerialName("harbor_signal")
data class SceneHarborSignal(val signal: String = "") : SceneComponent

/** The signal lamp on an entity. */
class HarborSignal(val signal: String)

/** Binds `harbor_signal` to [HarborSignal]. */
object HarborSignalBinding : SceneComponentBinding<HarborSignal, SceneHarborSignal> {
    override val componentClass: KClass<HarborSignal> = HarborSignal::class
    override val schemaClass: KClass<SceneHarborSignal> = SceneHarborSignal::class
    override val serializer = SceneHarborSignal.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneHarborSignal, context: SceneResolutionContext) {
        world.add(entity, HarborSignal(component.signal))
    }

    override fun export(world: World, entity: Entity, component: HarborSignal): SceneHarborSignal = SceneHarborSignal(component.signal)
}

/** The project's own capability: its `harbor_signal` component, the signal files it reads, and the system that shows them. */
object HarborSignalCapability : SceneCapability {
    private val signals = SceneContentKey<Map<String, String>>("harbor signals")

    override val id = "com.example.harbor-town.signal"
    override val components = listOf(HarborSignalBinding)

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (!scene.uses(SceneHarborSignal::class)) return
        val paths = scene.nodes.flatMap { it.signals() }.distinct()
        content[signals] = paths.associateWith { path ->
            files.read(AssetPath(path)).getOrElse { throw IllegalArgumentException("Can't read the signal $path", it) }.decodeToString()
        }
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneHarborSignal::class)) return
        plan.fixed("harbor-signal") { services ->
            val read = services.content[signals].orEmpty()
            object : System {
                override fun update(world: World, delta: Float) {
                    world.queryEach(HarborSignal::class) { _, lamp -> check(lamp.signal in read || lamp.signal.isEmpty()) }
                }
            }
        }
    }
}

/** The signals this node's lamps and its children's read. */
private fun SceneNode.signals(): List<String> = components.filterIsInstance<SceneHarborSignal>().map { it.signal } + children.flatMap { it.signals() }

/** Not a capability: what a manifest that names the wrong class points at. */
class HarborTownNotes
