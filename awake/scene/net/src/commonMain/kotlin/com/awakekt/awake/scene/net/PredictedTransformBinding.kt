/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.ReconcileConfig
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneValidationException
import kotlin.reflect.KClass

/** Loads only authored settings; history, acknowledgements and game rules start empty. */
@RegisteredBinding
object PredictedTransformBinding : SceneComponentBinding<PredictedTransform, ScenePredictedEntity> {
    override val componentClass: KClass<PredictedTransform> = PredictedTransform::class
    override val schemaClass: KClass<ScenePredictedEntity> = ScenePredictedEntity::class
    override val serializer = ScenePredictedEntity.serializer()

    override fun attachTyped(world: World, entity: Entity, component: ScenePredictedEntity, context: SceneResolutionContext) {
        val issues = component.validate("predicted_entity")
        if (issues.isNotEmpty()) throw SceneValidationException(issues)
        require(world.get<Transform>(entity)?.parent == null) { "predicted_entity requires a scene root; parented transforms are not supported" }
        world.add(entity, component.toPrediction())
    }

    override fun export(world: World, entity: Entity, component: PredictedTransform): ScenePredictedEntity = component.toScene()
}

internal fun ScenePredictedEntity.toPrediction(): PredictedTransform = PredictedTransform(
    config = ReconcileConfig(
        correctMetres = correctMetres,
        farMetres = farMetres,
        trustedGuessedTicks = trustedGuessedTicks,
        serverGuessWindowSeconds = serverGuessWindowSeconds,
        capEaseSeconds = capEaseSeconds,
        predictionCapSeconds = predictionCapSeconds ?: (serverGuessWindowSeconds + capEaseSeconds),
        planar = planar,
    ),
    historyCapacity = historyCapacity,
    glideHalfLifeSeconds = glideHalfLifeSeconds,
    blendShare = blendShare,
)

internal fun PredictedTransform.toScene(): ScenePredictedEntity = ScenePredictedEntity(
    correctMetres = config.correctMetres,
    farMetres = config.farMetres,
    trustedGuessedTicks = config.trustedGuessedTicks,
    serverGuessWindowSeconds = config.serverGuessWindowSeconds,
    capEaseSeconds = config.capEaseSeconds,
    predictionCapSeconds = config.predictionCapSeconds.takeUnless { it == config.serverGuessWindowSeconds + config.capEaseSeconds },
    planar = config.planar,
    historyCapacity = historyCapacity,
    glideHalfLifeSeconds = glideHalfLifeSeconds,
    blendShare = blendShare,
)

/** Opts a scene registry into loading and saving `predicted_entity`. */
fun SceneComponentRegistry.registerNet(): SceneComponentRegistry = register(NetSceneComponents)
