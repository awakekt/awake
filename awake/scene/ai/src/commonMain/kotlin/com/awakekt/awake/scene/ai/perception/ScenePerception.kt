/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.perception

import com.awakekt.awake.ai.perception.FactionId
import com.awakekt.awake.ai.perception.FactionRelation
import com.awakekt.awake.ai.perception.FactionTable
import com.awakekt.awake.ai.perception.HearingSettings
import com.awakekt.awake.ai.perception.PerceptionKeys
import com.awakekt.awake.ai.perception.PerceptionSettings
import com.awakekt.awake.ai.perception.PerceptionSpace
import com.awakekt.awake.ai.perception.Relation
import com.awakekt.awake.ai.perception.SightSettings
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.schema.PropertyUnit
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
    val settings: ScenePerceptionSettings = ScenePerceptionSettings(),
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (faction.isBlank()) add(SceneValidationIssue(path, "perceiver.faction must not be blank"))
        runCatching { settings.toSettings() }.exceptionOrNull()?.let { add(SceneValidationIssue(path, "perceiver.settings: ${it.message}")) }
    }
}

private val DEFAULT_SIGHT = SightSettings()
private val DEFAULT_HEARING = HearingSettings()
private val DEFAULT_SETTINGS = PerceptionSettings()

/**
 * [SightSettings] as scene data. It reads whatever a document says, and [ScenePerceiver.validate]
 * reports a value [SightSettings] refuses, so a bad document is reported rather than unreadable.
 *
 * @property focusRange How far it sees straight ahead, in metres.
 * @property focusAngle The full width of the focus cone, in degrees.
 * @property peripheralRange How far it sees to the side, in metres.
 * @property peripheralAngle The full width of the peripheral cone, in degrees.
 * @property eyeHeight How far above its position it looks from.
 * @property lineOfSight Whether sight asks what blocks it.
 */
@Serializable
data class SceneSight(
    @PropertyRange(min = 0.0) @PropertyUnit("m") val focusRange: Float = DEFAULT_SIGHT.focusRange,
    @PropertyRange(min = 0.0, max = 360.0) @PropertyUnit("deg") val focusAngle: Float = DEFAULT_SIGHT.focusAngle,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val peripheralRange: Float = DEFAULT_SIGHT.peripheralRange,
    @PropertyRange(min = 0.0, max = 360.0) @PropertyUnit("deg") val peripheralAngle: Float = DEFAULT_SIGHT.peripheralAngle,
    @PropertyUnit("m") val eyeHeight: Float = DEFAULT_SIGHT.eyeHeight,
    val lineOfSight: Boolean = DEFAULT_SIGHT.lineOfSight,
)

/**
 * [HearingSettings] as scene data, checked as [SceneSight] is.
 *
 * @property range How far a sound of loudness 1 is heard, in metres.
 * @property muffledBy How much of its reach a blocked sound keeps, from 0 to 1.
 */
@Serializable
data class SceneHearing(
    @PropertyRange(min = 0.0) @PropertyUnit("m") val range: Float = DEFAULT_HEARING.range,
    @PropertyRange(min = 0.0, max = 1.0) val muffledBy: Float = DEFAULT_HEARING.muffledBy,
)

/**
 * [PerceptionSettings] as scene data, checked as [SceneSight] is.
 *
 * @property sight What it sees.
 * @property hearing What it hears.
 * @property forgetAfter Seconds after it last sensed something that it forgets it.
 * @property updateInterval Seconds between its sight updates; 0 updates every frame.
 * @property keys The blackboard keys it writes.
 */
@Serializable
data class ScenePerceptionSettings(
    val sight: SceneSight = SceneSight(),
    val hearing: SceneHearing = SceneHearing(),
    @PropertyRange(min = 0.0) @PropertyUnit("s") val forgetAfter: Float = DEFAULT_SETTINGS.forgetAfter,
    @PropertyRange(min = 0.0) @PropertyUnit("s") val updateInterval: Float = DEFAULT_SETTINGS.updateInterval,
    val keys: PerceptionKeys = DEFAULT_SETTINGS.keys,
) {
    /**
     * The settings this describes.
     *
     * @throws IllegalArgumentException naming the value [PerceptionSettings] refuses.
     */
    fun toSettings(): PerceptionSettings =
        PerceptionSettings(
            sight = SightSettings(sight.focusRange, sight.focusAngle, sight.peripheralRange, sight.peripheralAngle, sight.eyeHeight, sight.lineOfSight),
            hearing = HearingSettings(hearing.range, hearing.muffledBy),
            forgetAfter = forgetAfter,
            updateInterval = updateInterval,
            keys = keys,
        )

    /** Mapping from the capability's settings. */
    companion object {
        /** [settings] as scene data. */
        fun of(settings: PerceptionSettings): ScenePerceptionSettings =
            ScenePerceptionSettings(
                sight = settings.sight.let {
                    SceneSight(it.focusRange, it.focusAngle, it.peripheralRange, it.peripheralAngle, it.eyeHeight, it.lineOfSight)
                },
                hearing = SceneHearing(settings.hearing.range, settings.hearing.muffledBy),
                forgetAfter = settings.forgetAfter,
                updateInterval = settings.updateInterval,
                keys = settings.keys,
            )
    }
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
