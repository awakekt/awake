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
import com.awakekt.awake.blueprint.Step
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import com.awakekt.awake.scene.canvas.CanvasElement

/**
 * Nodes for game UI: a menu or a HUD reacting to its buttons and changing what it shows, on the
 * scene's `canvas_element`s.
 *
 * An element is named the way the other scene nodes name an entity: the `target` wired to the node,
 * else the element whose `Name` is the node's `name`, else the blueprint's own entity's element.
 */
object CanvasNodes {
    /**
     * Every canvas node, bound to [scene].
     *
     * @param scene The blueprint scene execution bridge.
     * @return List of all instantiated canvas blueprint nodes.
     */
    fun all(scene: BlueprintScene): List<BlueprintNode> = listOf(OnButtonPressed, SetText(scene), SetVisible(scene))

    /**
     * Fired once for each press of a Button: the one whose `Name` is the node's `name`, or, with no
     * name, the blueprint's own entity's. `element` is the pressed element's entity.
     *
     * It takes no press from anything else reading the Button, such as the input action its `action`
     * names: both react to the same press.
     */
    object OnButtonPressed : EventNode {
        /** Event type identifier for a Button's press. */
        const val TYPE = "event.button.pressed"
        override val spec = NodeSpec(
            type = TYPE,
            displayName = "On Button Pressed",
            category = "Events",
            description = "Fires when the Button named here is pressed, or this blueprint's own Button when no name is given.",
            inputs = listOf(PortSpec(NAME, STRING, "The pressed element's name; empty is this blueprint's own element.")),
            outputs = listOf(PortSpec(THEN, EXEC), PortSpec(ELEMENT, ENTITY, "The pressed element's entity.")),
        )
    }

    /**
     * Sets a canvas element's text: a Text's words, a Button's label, a TextField's content. A
     * missing element is skipped.
     *
     * @param scene The blueprint scene execution bridge.
     */
    class SetText(private val scene: BlueprintScene) : ActionNode {
        private val then = Step.Continue(THEN)
        override val spec = NodeSpec(
            type = "ui.set-text",
            displayName = "Set Text",
            category = "UI",
            description = "Sets the text of a canvas element: the target, else the one named here, else this blueprint's own.",
            inputs = listOf(execIn(), targetIn(), nameIn(), PortSpec(TEXT, STRING, "The text to show.")),
            outputs = listOf(PortSpec(THEN, EXEC)),
        )
        override val effect = Effect.Presentation

        override fun run(ctx: BlueprintContext): Step {
            scene.canvasElement(ctx)?.text = ctx.string(TEXT).orEmpty()
            return then
        }
    }

    /**
     * Shows or hides a canvas element, and the elements inside it. A hidden Button takes no presses.
     * A missing element is skipped.
     *
     * @param scene The blueprint scene execution bridge.
     */
    class SetVisible(private val scene: BlueprintScene) : ActionNode {
        private val then = Step.Continue(THEN)
        override val spec = NodeSpec(
            type = "ui.set-visible",
            displayName = "Set Visible",
            category = "UI",
            description = "Shows or hides a canvas element: the target, else the one named here, else this blueprint's own.",
            inputs = listOf(execIn(), targetIn(), nameIn(), PortSpec(VISIBLE, BOOL, "Whether the element is shown.")),
            outputs = listOf(PortSpec(THEN, EXEC)),
        )
        override val effect = Effect.Presentation

        override fun run(ctx: BlueprintContext): Step {
            scene.canvasElement(ctx)?.visible = ctx.bool(VISIBLE)
            return then
        }
    }

    /** The name input of [OnButtonPressed], [SetText] and [SetVisible]. */
    internal const val NAME = "name"

    /** The output of [OnButtonPressed] naming the pressed element's entity. */
    internal const val ELEMENT = "element"
}

private const val THEN = "then"
private const val TARGET = "target"
private const val TEXT = "text"
private const val VISIBLE = "visible"

private fun execIn() = PortSpec("exec", EXEC, multiple = true)

private fun targetIn() = PortSpec(TARGET, ENTITY, "The element's entity; leave it unwired to name the element instead.")

private fun nameIn() = PortSpec(CanvasNodes.NAME, STRING, "The element's name, when no target is wired; empty is this blueprint's own element.")

/** The element a canvas node acts on: the wired `target`, else the one named `name`, else the owner's. */
private fun BlueprintScene.canvasElement(ctx: BlueprintContext): CanvasElement? {
    val target = ctx.entity(TARGET)
    val entity = if (target != NoEntity) {
        target
    } else {
        val name = ctx.string(CanvasNodes.NAME)
        if (name.isNullOrEmpty()) ctx.owner else names.find(name) ?: return null
    }
    return if (world.isAlive(entity)) world.get<CanvasElement>(entity) else null
}
