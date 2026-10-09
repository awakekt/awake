/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.input

import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActionDefinition
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.controls.movement.MovementActions
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** One action a scene names: a [SceneButtonAction] (`"type": "button"`) or a [SceneAxisAction] (`"type": "axis"`). */
@Serializable
sealed interface SceneInputAction {
    /** The name systems and canvas elements use for the action. */
    val name: String
}

/**
 * An action that is on or off, triggered by any of [keys] and pointer [buttons].
 *
 * @property name The name systems and canvas elements use for the action.
 * @property keys The keys that trigger it.
 * @property buttons The pointer buttons that trigger it.
 * @property trigger Whether it is active while held, for the frame it is pressed, or switched by each press.
 * @property startsOn Whether a [ActionTrigger.Toggle] starts on.
 */
@Serializable
@SerialName("button")
data class SceneButtonAction(
    override val name: String,
    val keys: Set<Key> = emptySet(),
    val buttons: Set<PointerButton> = emptySet(),
    val trigger: ActionTrigger = ActionTrigger.Hold,
    val startsOn: Boolean = false,
) : SceneInputAction

/** A direction on two axes: [up] and [down] keys push it along y, [right] and [left] along x. */
@Serializable
@SerialName("axis")
data class SceneAxisAction(
    override val name: String,
    val up: Set<Key> = emptySet(),
    val down: Set<Key> = emptySet(),
    val left: Set<Key> = emptySet(),
    val right: Set<Key> = emptySet(),
) : SceneInputAction

/**
 * The scene's input actions and what triggers each. An action named like one of
 * [MovementActions.defaults] replaces that default; the rest of the defaults stay. A scene uses the
 * first `input_actions` it has.
 *
 * @property actions The scene's own actions and its rebound defaults.
 */
@Serializable
@SerialName("input_actions")
data class SceneInputActions(val actions: List<SceneInputAction> = emptyList()) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        (actions.flatMap(::actionIssues) + duplicateNames() + keysBoundTwice()).map { SceneValidationIssue(path, it) }

    /** The scene's actions, with the defaults it does not rebind. */
    fun withDefaults(): List<InputActionDefinition> {
        val declared = actions.map { it.toDefinition() }
        val names = declared.mapTo(HashSet()) { it.name }
        return MovementActions.defaults.filterNot { it.name in names } + declared
    }

    private fun actionIssues(action: SceneInputAction): List<String> = buildList {
        val name = action.name
        if (!InputActions.isValidName(name)) add("input_actions: \"$name\" is not an action name: ${InputActions.NAME_RULE}")
        if (name == MovementActions.MOVE && action !is SceneAxisAction) add("input_actions.move steers the player, so its type is axis")
        if (name in BUTTON_DEFAULTS && action !is SceneButtonAction) add("input_actions.$name is a button, so its type is button")
        if (action is SceneButtonAction && action.startsOn && action.trigger != ActionTrigger.Toggle) {
            add("input_actions.$name.startsOn needs \"trigger\": \"Toggle\"")
        }
        if (Key.Unknown in action.toDefinition().boundKeys()) add("input_actions.$name: Unknown is not a key that can be pressed")
    }

    private fun duplicateNames(): List<String> = actions.groupingBy { it.name }.eachCount()
        .filterValues { it > 1 }.keys.map { "input_actions names \"$it\" more than once" }

    private fun keysBoundTwice(): List<String> {
        val owners = HashMap<Key, String>()
        return withDefaults().flatMap { action ->
            action.boundKeys().mapNotNull { key ->
                when (val other = owners.put(key, action.name)) {
                    null -> null
                    action.name -> "input_actions.${action.name} binds $key twice"
                    else -> "input_actions binds $key to both $other and ${action.name}"
                }
            }
        }
    }
}

/** The defaults a player presses, which a scene can rebind only as buttons. */
private val BUTTON_DEFAULTS = setOf(MovementActions.JUMP, MovementActions.RUN)

/** The scene's input actions: those of its first `input_actions`, or null while it has none. */
fun World.inputActions(): InputActions? = query(InputActions::class).firstOrNull()?.let { get(it, InputActions::class) }

/** Binds a scene's [SceneInputActions] to an [InputActions] holding them and the defaults they keep. */
object InputActionsBinding : SceneComponentBinding<InputActions, SceneInputActions> {
    override val componentClass: KClass<InputActions> = InputActions::class
    override val schemaClass: KClass<SceneInputActions> = SceneInputActions::class
    override val serializer = SceneInputActions.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneInputActions, context: SceneResolutionContext) {
        world.add(entity, InputActions(component.withDefaults()))
    }

    /** Leaves out the defaults, so a scene that rebinds nothing saves as it was written. */
    override fun export(world: World, entity: Entity, component: InputActions): SceneInputActions =
        SceneInputActions(component.definitions.filterNot { it in MovementActions.defaults }.map { it.toScene() })
}

private fun SceneInputAction.toDefinition(): InputActionDefinition = when (this) {
    is SceneButtonAction -> ButtonAction(name, keys, buttons, trigger, startsOn)
    is SceneAxisAction -> AxisAction(name, up, down, left, right)
}

private fun InputActionDefinition.toScene(): SceneInputAction = when (this) {
    is ButtonAction -> SceneButtonAction(name, keys, buttons, trigger, startsOn)
    is AxisAction -> SceneAxisAction(name, up, down, left, right)
}

/** Every key bound to the action, once per binding. */
private fun InputActionDefinition.boundKeys(): List<Key> = when (this) {
    is ButtonAction -> keys.toList()
    is AxisAction -> up.toList() + down + left + right
}
