/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.ecs.Entity
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The type of an event a behaviour emits for game code, such as `"door.opened"`. What types exist,
 * and what they mean, is the game's data.
 *
 * @property value The id. Not blank.
 */
@Serializable
@JvmInline
value class AiEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "An AI event id is not blank." }
    }

    override fun toString(): String = value
}

/**
 * Receives the events behaviours emit, such as the `ai.emit` node. A host passes one to
 * [com.awakekt.awake.ai.btree.BehaviorTreeSystem] or
 * [com.awakekt.awake.ai.fsm.AiStateMachineSystem], or sets [AiContext.events].
 */
fun interface AiEventSink {
    /**
     * Called when [entity]'s behaviour emits [event] with [payload]. Runs inside the tick, so it
     * should record the event rather than change the world.
     */
    fun emit(entity: Entity, event: AiEventId, payload: Map<String, BlackboardValue>)

    /** The sink that drops every event. */
    companion object {
        /** Drops every event. */
        val None: AiEventSink = AiEventSink { _, _, _ -> }
    }
}

/**
 * One emitted event, as [AiEventQueue] keeps it.
 *
 * @property entity The entity whose behaviour emitted it.
 * @property event Its type.
 * @property payload The values it carries.
 */
data class AiEvent(
    val entity: Entity,
    val event: AiEventId,
    val payload: Map<String, BlackboardValue> = emptyMap(),
)

/** An [AiEventSink] that keeps events, in emission order, until [drain] takes them. */
class AiEventQueue : AiEventSink {
    private val pending = mutableListOf<AiEvent>()

    /** The events emitted since the last [drain]. */
    val events: List<AiEvent> get() = pending

    override fun emit(entity: Entity, event: AiEventId, payload: Map<String, BlackboardValue>) {
        pending += AiEvent(entity, event, payload)
    }

    /** Returns the kept events and forgets them. */
    fun drain(): List<AiEvent> {
        val drained = pending.toList()
        pending.clear()
        return drained
    }
}
