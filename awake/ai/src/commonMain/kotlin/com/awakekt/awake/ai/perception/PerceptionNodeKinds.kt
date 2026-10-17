/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.btree.ConditionNode
import com.awakekt.awake.ai.definition.NodeCategory
import com.awakekt.awake.ai.definition.NodeKind
import com.awakekt.awake.ai.definition.NodeKindId
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.schema.PropertyUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which relations a perception condition counts. */
@Serializable
enum class RelationFilter {
    /** Hostile only. */
    @SerialName("hostile")
    Hostile,

    /** Neutral only. */
    @SerialName("neutral")
    Neutral,

    /** Friendly only. */
    @SerialName("friendly")
    Friendly,

    /** Any relation. */
    @SerialName("any")
    Any,
    ;

    /** The relation this filters for, or `null` for [Any]. */
    val relation: Relation?
        get() = when (this) {
            Hostile -> Relation.Hostile
            Neutral -> Relation.Neutral
            Friendly -> Relation.Friendly
            Any -> null
        }
}

/**
 * Parameters of `ai.perception.sees`.
 *
 * @property relation What to look for.
 * @property focusOnly Whether only something in the focus cone counts.
 */
@Serializable
data class SeesParams(
    val relation: RelationFilter = RelationFilter.Hostile,
    val focusOnly: Boolean = false,
)

/**
 * Parameters of `ai.perception.remembers`.
 *
 * @property relation What to look for.
 */
@Serializable
data class RemembersParams(val relation: RelationFilter = RelationFilter.Hostile)

/**
 * Parameters of `ai.perception.heard`.
 *
 * @property withinSeconds How recent the noise must be.
 */
@Serializable
data class HeardParams(
    @PropertyRange(min = 0.0) @PropertyUnit("s") val withinSeconds: Float = 1f,
) {
    init {
        require(withinSeconds.isFinite() && withinSeconds >= 0f) { "withinSeconds is $withinSeconds; it must be 0 or more." }
    }
}

/**
 * Behaviour conditions over an agent's [Perceiver] memory. Every [com.awakekt.awake.ai.definition.AiRegistry]
 * with built-ins has them. Each fails on an entity with no [Perceiver].
 */
object PerceptionNodeKinds {
    /** `ai.perception.sees`: something of a relation is in sight. */
    val Sees: NodeKind<SeesParams> = NodeKind(
        NodeKindId("ai.perception.sees"),
        SeesParams.serializer(),
        NodeCategory.Condition,
        displayName = "Sees",
        description = "Succeeds when the agent sees something of relation now; focusOnly counts its focus cone only.",
    ) { params, _ ->
        ConditionNode { context ->
            memoryOf(context)?.entities?.any {
                it.inSight && (params.relation.relation == null || it.relation == params.relation.relation) &&
                    (!params.focusOnly || it.sense == Sense.Focus)
            } == true
        }
    }

    /** `ai.perception.remembers`: something of a relation is remembered. */
    val Remembers: NodeKind<RemembersParams> = NodeKind(
        NodeKindId("ai.perception.remembers"),
        RemembersParams.serializer(),
        NodeCategory.Condition,
        displayName = "Remembers",
        description = "Succeeds when the agent remembers something of relation, in sight or not.",
    ) { params, _ -> ConditionNode { context -> memoryOf(context)?.remembersAny(params.relation.relation) == true } }

    /** `ai.perception.heard`: a noise was heard recently. */
    val Heard: NodeKind<HeardParams> = NodeKind(
        NodeKindId("ai.perception.heard"),
        HeardParams.serializer(),
        NodeCategory.Condition,
        displayName = "Heard Noise",
        description = "Succeeds when the agent heard a noise in the last withinSeconds.",
    ) { params, _ ->
        ConditionNode { context ->
            val memory = memoryOf(context)
            val noise = memory?.noise
            noise != null && memory.now - noise.heardAt <= params.withinSeconds
        }
    }

    /** Every perception kind. */
    val nodeKinds: List<NodeKind<*>> = listOf(Sees, Remembers, Heard)

    private fun memoryOf(context: AiContext): PerceptionMemory? = context.world.get<Perceiver>(context.entity)?.memory
}
