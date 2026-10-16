/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.blueprint.BlueprintCompiler
import com.awakekt.awake.blueprint.BlueprintInstance
import com.awakekt.awake.blueprint.BlueprintInterpreter
import com.awakekt.awake.blueprint.BlueprintNodes
import com.awakekt.awake.blueprint.BlueprintProgram
import com.awakekt.awake.blueprint.EventFilter
import com.awakekt.awake.blueprint.EventPayload
import com.awakekt.awake.blueprint.NoEntity
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.ui.canvas.CanvasElement

/**
 * Runs the blueprint of every entity with a [BlueprintComponent]. A fixed-phase system; register it
 * after [PhysicsSystem], whose contacts it turns into sensor events.
 *
 * Each step, in order:
 * 1. blueprints new since the last step start, and On Start fires;
 * 2. the others' waits are polled;
 * 3. On Sensor Enter and On Sensor Exit fire on sensors from the step's contacts;
 * 4. On Button Pressed fires once for each press of a canvas Button since the last step.
 *
 * A press is read from [CanvasElement.presses], which takes it from no other reader: a Button whose
 * `action` names an input action presses that action as well.
 *
 * A graph compiles once per path. A step in which nothing happens allocates nothing.
 *
 * @param graphs Loads the graph at a [BlueprintComponent.graph] path; called once per path.
 * @param physics Where sensor contacts come from and bodies are freed on destroy; `null` without physics.
 * @param runPresentation `false` on a server, to skip presentation nodes.
 */
class BlueprintSystem(
    private val graphs: (path: String) -> NodeGraph,
    private val physics: PhysicsSystem? = null,
    runPresentation: Boolean = true,
) : System {
    /** Pass this to game nodes that act on the scene. */
    val scene = BlueprintScene(physics)

    /** Core, scene and canvas nodes. Register game nodes here before the first update. */
    val nodes: BlueprintNodes = BlueprintNodes.core().apply {
        SceneNodes.all(scene).forEach(::register)
        CanvasNodes.all(scene).forEach(::register)
    }

    private val interpreter = BlueprintInterpreter(runPresentation)
    private val programs = HashMap<String, BlueprintProgram>()
    private var family: Family1<BlueprintComponent>? = null
    private var owners = LongArray(INITIAL_OWNERS)
    private var ownerCount = 0
    private var sensorOther = NoEntity
    private val sensorPayload = EventPayload { it.setEntity(SceneNodes.OTHER, sensorOther) }
    private var canvas: Family1<CanvasElement>? = null
    private val pressCounts = PressCounts()
    private var pressed = LongArray(INITIAL_PRESSES)
    private var pressedCount = 0
    private var pressedElement = NoEntity
    private var pressedName: String? = null
    private val buttonPayload = EventPayload { it.setEntity(CanvasNodes.ELEMENT, pressedElement) }

    /** A node with no name listens to its own entity's element, and a named one to elements of that name. */
    private val buttonFilter = EventFilter { event ->
        val name = event.string(CanvasNodes.NAME)
        if (name.isNullOrEmpty()) event.owner == pressedElement else name == pressedName
    }

    /**
     * Executes one fixed simulation frame of all active entity blueprints.
     *
     * @param world The ECS simulation world.
     * @param delta Elapsed time in seconds since the previous simulation step.
     */
    override fun update(world: World, delta: Float) {
        if (scene.bind(world)) {
            family = world.family(BlueprintComponent::class)
            canvas = world.family(CanvasElement::class)
            pressCounts.clear()
        }
        // Collected first: a chain may create or destroy entities, which a live query must not see.
        ownerCount = 0
        family?.forEach { entity, _ -> addOwner(entity) }
        for (i in 0 until ownerCount) {
            val owner = Entity(owners[i])
            val component = world.get<BlueprintComponent>(owner) ?: continue
            val instance = component.instance
            if (instance == null) start(owner, component) else interpreter.tick(instance, delta)
        }
        fireSensorEvents(world)
        fireButtonEvents(world)
    }

    /**
     * Recompiles [path] from [graph] and swaps it into every running instance of it. Variables whose
     * name and type still exist keep their values, waits are cancelled, and On Start does not fire again.
     *
     * @param path The graph asset path being reloaded.
     * @param graph The updated node graph definition.
     * @throws com.awakekt.awake.nodegraph.InvalidNodeGraphException before anything changes, when
     * [graph] is not a valid blueprint.
     */
    fun reload(path: String, graph: NodeGraph) {
        val program = BlueprintCompiler.compile(graph, nodes)
        programs[path] = program
        family?.forEach { _, component ->
            val instance = component.instance
            if (component.graph == path && instance != null) interpreter.reload(instance, program)
        }
    }

    private fun start(owner: Entity, component: BlueprintComponent) {
        val program = programs.getOrPut(component.graph) { BlueprintCompiler.compile(graphs(component.graph), nodes) }
        val instance = BlueprintInstance(program, owner)
        for ((name, value) in component.variables) {
            try {
                instance.setVariable(name, value)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Blueprint '${component.graph}' on $owner: ${e.message}", e)
            }
        }
        component.instance = instance
        interpreter.start(instance)
    }

    private fun fireSensorEvents(world: World) {
        val contacts = physics?.contacts ?: return
        for (i in contacts.indices) {
            val contact = contacts[i]
            val type = if (contact.phase == ContactPhase.BEGAN) SceneNodes.OnSensorEnter.TYPE else SceneNodes.OnSensorExit.TYPE
            fireSensor(world, contact.entityA, contact.entityB, type)
            fireSensor(world, contact.entityB, contact.entityA, type)
        }
    }

    private fun fireSensor(world: World, sensor: Entity?, other: Entity?, type: String) {
        if (sensor == null || world.get<PhysicsBody>(sensor)?.sensor != true) return
        val instance = world.get<BlueprintComponent>(sensor)?.instance ?: return
        sensorOther = other ?: NoEntity
        interpreter.fire(instance, type, sensorPayload)
    }

    private fun fireButtonEvents(world: World) {
        // Collected first, as the owners are: a chain may create or destroy elements.
        pressedCount = 0
        canvas?.forEach { entity, element ->
            repeat(pressCounts.take(entity, element.presses)) { addPressed(entity) }
        }
        for (p in 0 until pressedCount) {
            pressedElement = Entity(pressed[p])
            pressedName = world.get<Name>(pressedElement)?.value
            for (i in 0 until ownerCount) {
                val instance = world.get<BlueprintComponent>(Entity(owners[i]))?.instance ?: continue
                interpreter.fire(instance, CanvasNodes.OnButtonPressed.TYPE, buttonPayload, buttonFilter)
            }
        }
        pressedName = null
    }

    private fun addPressed(entity: Entity) {
        if (pressedCount == pressed.size) pressed = pressed.copyOf(pressed.size * 2)
        pressed[pressedCount++] = entity.packed
    }

    private fun addOwner(entity: Entity) {
        if (ownerCount == owners.size) owners = owners.copyOf(owners.size * 2)
        owners[ownerCount++] = entity.packed
    }

    private companion object {
        const val INITIAL_OWNERS = 16
    }
}

private const val INITIAL_PRESSES = 4
