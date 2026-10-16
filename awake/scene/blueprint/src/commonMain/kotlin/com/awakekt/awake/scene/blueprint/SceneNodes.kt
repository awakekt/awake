/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.blueprint.ActionNode
import com.awakekt.awake.blueprint.BlueprintContext
import com.awakekt.awake.blueprint.BlueprintNode
import com.awakekt.awake.blueprint.Effect
import com.awakekt.awake.blueprint.EventNode
import com.awakekt.awake.blueprint.NoEntity
import com.awakekt.awake.blueprint.PortTypes.BOOL
import com.awakekt.awake.blueprint.PortTypes.ENTITY
import com.awakekt.awake.blueprint.PortTypes.EXEC
import com.awakekt.awake.blueprint.PortTypes.STRING
import com.awakekt.awake.blueprint.PureNode
import com.awakekt.awake.blueprint.Step
import com.awakekt.awake.core.animation.AnimationPlayback
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import com.awakekt.awake.scene.rendering.animation.Animator

/**
 * Nodes that act on scene entities: sensor events, entities and animation.
 *
 * A node with a `target` input acts on the blueprint's own entity when nothing is wired to it. When a
 * wire gives no entity, such as a Find By Name that found no one, the node acts on nothing and warns
 * once.
 */
object SceneNodes {
    /**
     * Every scene node, bound to [scene].
     *
     * @param scene The blueprint scene execution bridge.
     * @return List of all instantiated scene blueprint nodes.
     */
    fun all(scene: BlueprintScene): List<BlueprintNode> =
        listOf(OnSensorEnter, OnSensorExit, Self, FindByName(scene), Destroy(scene), PlayAnimation(scene), LoadScene(scene))

    /** Fired on a sensor's entity when a body starts touching it; `other` is the body's entity. */
    object OnSensorEnter : EventNode {
        /** Event type identifier for sensor contact entry. */
        const val TYPE = "event.sensor.enter"
        override val spec = sensorEvent(TYPE, "On Sensor Enter")
    }

    /** Fired on a sensor's entity when a body stops touching it; `other` is the body's entity. */
    object OnSensorExit : EventNode {
        /** Event type identifier for sensor contact exit. */
        const val TYPE = "event.sensor.exit"
        override val spec = sensorEvent(TYPE, "On Sensor Exit")
    }

    /** The entity the blueprint runs on. */
    object Self : PureNode {
        override val spec = NodeSpec(
            type = "entity.self",
            displayName = "Self",
            category = "Entity",
            outputs = listOf(PortSpec(ENTITY_OUT, ENTITY)),
        )

        override fun evaluate(ctx: BlueprintContext) = ctx.setEntity(ENTITY_OUT, ctx.owner)
    }

    /**
     * The live entity with a `Name`, or no entity.
     *
     * @param scene The blueprint scene execution bridge.
     */
    class FindByName(private val scene: BlueprintScene) : PureNode {
        override val spec = NodeSpec(
            type = "entity.find-by-name",
            displayName = "Find By Name",
            category = "Entity",
            inputs = listOf(PortSpec(NAME, STRING)),
            outputs = listOf(PortSpec(ENTITY_OUT, ENTITY)),
        )

        override fun evaluate(ctx: BlueprintContext) {
            val name = ctx.string(NAME)
            val found = if (name == null) null else scene.names.find(name)
            ctx.setEntity(ENTITY_OUT, found ?: NoEntity)
        }
    }

    /**
     * Destroys the target with the nodes under it, and their physics bodies. A wired target that
     * gives no entity destroys nothing.
     *
     * @param scene The blueprint scene execution bridge.
     */
    class Destroy(private val scene: BlueprintScene) : ActionNode {
        private val then = Step.Continue(THEN)
        override val spec = NodeSpec(
            type = "entity.destroy",
            displayName = "Destroy",
            category = "Entity",
            inputs = listOf(execIn(), PortSpec(TARGET, ENTITY)),
            outputs = listOf(PortSpec(THEN, EXEC)),
        )

        override fun run(ctx: BlueprintContext): Step {
            val target = targetOrOwner(ctx)
            if (scene.world.isAlive(target)) scene.destroy(target)
            return then
        }
    }

    /**
     * Plays a clip on the target's `Animator`. A missing animator or clip, or a wired target that
     * gives no entity, is skipped.
     *
     * @param scene The blueprint scene execution bridge.
     */
    class PlayAnimation(private val scene: BlueprintScene) : ActionNode {
        private val then = Step.Continue(THEN)
        override val spec = NodeSpec(
            type = "animation.play",
            displayName = "Play Animation",
            category = "Animation",
            inputs = listOf(execIn(), PortSpec(TARGET, ENTITY), PortSpec(CLIP, STRING), PortSpec(LOOP, BOOL)),
            outputs = listOf(PortSpec(THEN, EXEC)),
        )
        override val effect = Effect.Presentation

        override fun run(ctx: BlueprintContext): Step {
            // A wired target that gives no entity has no Animator: NoEntity is never alive.
            val player = scene.world.get<Animator>(targetOrOwner(ctx))?.player ?: return then
            val clip = ctx.string(CLIP)
            if (clip != null && clip in player.clipEntries) {
                player.play(clip, if (ctx.bool(LOOP)) AnimationPlayback.Loop else AnimationPlayback.Once)
            }
            return then
        }
    }

    /**
     * Asks the game to play another of its scenes: `scene` is its path relative to the project root,
     * such as `scenes/level-2.scene.json`. The scene playing now runs on until the next one has loaded,
     * then stops for it at the start of a later step, and nothing it holds carries across. A scene
     * that can't be loaded is logged and the one playing goes on.
     *
     * Where nothing switches scenes ([BlueprintScene.loadScene] is null), such as an editor playing the
     * one scene it edits, and for an empty `scene`, it logs a warning, once, and carries on.
     *
     * @param scene The blueprint scene execution bridge.
     */
    class LoadScene(private val scene: BlueprintScene) : ActionNode {
        private val then = Step.Continue(THEN)
        private var warned = false
        override val spec = NodeSpec(
            type = TYPE,
            displayName = "Load Scene",
            category = "Scene",
            description = "Plays another of the project's scenes in place of this one, once it has loaded.",
            inputs = listOf(execIn(), PortSpec(SCENE, STRING, "The scene's path relative to the project root.")),
            outputs = listOf(PortSpec(THEN, EXEC)),
        )

        override fun run(ctx: BlueprintContext): Step {
            val path = ctx.string(SCENE)
            val load = scene.loadScene
            when {
                path.isNullOrEmpty() -> warnOnce { "Load Scene names no scene, so it loads none" }
                load == null -> warnOnce { "Load Scene can't load $path: nothing here plays another scene" }
                else -> load(path)
            }
            return then
        }

        private inline fun warnOnce(message: () -> String) {
            if (warned) return
            warned = true
            log.warn(message)
        }

        /** Identifiers for Load Scene. */
        companion object {
            /** The node type, as a graph names it. */
            const val TYPE = "scene.load"

            /** The input naming the scene to play. */
            const val SCENE = "scene"
        }
    }

    /** The sensor events' output naming the body's entity; no entity for a body none owns. */
    const val OTHER = "other"
    private const val THEN = "then"
    private const val TARGET = "target"
    private const val ENTITY_OUT = "entity"
    private const val NAME = "name"
    private const val CLIP = "clip"
    private const val LOOP = "loop"

    private fun execIn() = PortSpec("exec", EXEC, multiple = true)

    private fun sensorEvent(type: String, displayName: String) = NodeSpec(
        type = type,
        displayName = displayName,
        category = "Events",
        outputs = listOf(PortSpec(THEN, EXEC), PortSpec(OTHER, ENTITY)),
    )

    /** The wired `target`, or the owner when nothing is wired to it; see [wiredTarget]. */
    private fun targetOrOwner(ctx: BlueprintContext): Entity = if (ctx.isWired(TARGET)) wiredTarget(ctx) else ctx.owner
}

private val log = Logger("blueprint")

/**
 * The entity wired to a node's `target`. A wire that gives none, such as a Find By Name that found no
 * one, gives [NoEntity] and warns once, so the node acts on nothing rather than on its own entity.
 */
internal fun wiredTarget(ctx: BlueprintContext): Entity {
    val target = ctx.entity(TARGET_INPUT)
    if (target == NoEntity) ctx.warnOnce(NO_TARGET)
    return target
}

private const val TARGET_INPUT = "target"
private const val NO_TARGET = "its target is wired but was given no entity, as by a Find By Name that found no one, so it does nothing."
