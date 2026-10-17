/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.perception

import com.awakekt.awake.ai.perception.FactionId
import com.awakekt.awake.ai.perception.FactionRelation
import com.awakekt.awake.ai.perception.FactionTable
import com.awakekt.awake.ai.perception.PerceptionSettings
import com.awakekt.awake.ai.perception.PerceptionSpace
import com.awakekt.awake.ai.perception.Relation
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/**
 * An entity that perceives: its faction and how it sees, hears and remembers.
 *
 * @property faction Its faction.
 * @property settings How it perceives.
 */
@Serializable
@SerialName("perceiver")
data class ScenePerceiver(
    val faction: String,
    val settings: PerceptionSettings = PerceptionSettings(),
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        if (faction.isBlank()) listOf(SceneValidationIssue(path, "perceiver.faction must not be blank")) else emptyList()
}

/**
 * An entity perceivers can see and hear.
 *
 * @property faction Its faction.
 * @property visible Whether it can be seen; it can always be heard.
 * @property height How far above its position a viewer aims at it.
 */
@Serializable
@SerialName("perceivable")
data class ScenePerceivable(
    val faction: String,
    val visible: Boolean = true,
    val height: Float = 1f,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (faction.isBlank()) add(SceneValidationIssue(path, "perceivable.faction must not be blank"))
        if (!height.isFinite()) add(SceneValidationIssue(path, "perceivable.height must be a number"))
    }
}

/**
 * Who regards whom how, for every perceiver in the scene. A scene has at most one; with none, every
 * faction is neutral to every other and friendly to itself.
 *
 * @property relations The rows.
 * @property otherwise The relation between factions no row names.
 * @property sameFaction The relation of a faction to itself.
 */
@Serializable
@SerialName("factions")
data class SceneFactions(
    val relations: List<SceneFactionRelation> = emptyList(),
    val otherwise: Relation = Relation.Neutral,
    val sameFaction: Relation = Relation.Friendly,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        relations.forEachIndexed { index, row ->
            if (row.from.isBlank() || row.to.isBlank()) add(SceneValidationIssue("$path.relations[$index]", "factions.relations needs from and to"))
        }
        val error = runCatching { toTable() }.exceptionOrNull()
        if (error != null) add(SceneValidationIssue(path, error.message.orEmpty()))
    }

    /** The faction table this describes. */
    fun toTable(): FactionTable =
        FactionTable(relations.map { FactionRelation(FactionId(it.from), FactionId(it.to), it.relation, it.mutual) }, otherwise, sameFaction)
}

/**
 * One row of [SceneFactions].
 *
 * @property from The faction that regards.
 * @property to The faction regarded.
 * @property relation How.
 * @property mutual Whether [to] regards [from] the same way.
 */
@Serializable
data class SceneFactionRelation(
    val from: String,
    val to: String,
    val relation: Relation,
    val mutual: Boolean = true,
)

/** The runtime side of [SceneFactions]: the scene's faction table. @property table The table. */
class Factions(val table: FactionTable)

/**
 * Where scene entities are and which way they face, for `PerceptionSystem`: an entity is where its
 * `Transform`'s position says and faces its yaw, +Z at yaw 0, as agents are turned. One with no
 * `Transform` has no place.
 */
object TransformPerceptionSpace : PerceptionSpace {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val transform = world.get<Transform>(entity) ?: return false
        into.set(transform.position)
        return true
    }

    override fun forward(world: World, entity: Entity, into: Vec3f): Boolean {
        val transform = world.get<Transform>(entity) ?: return false
        val yaw = transform.rotation.y
        into.set(sin(yaw), 0f, cos(yaw))
        return true
    }
}
