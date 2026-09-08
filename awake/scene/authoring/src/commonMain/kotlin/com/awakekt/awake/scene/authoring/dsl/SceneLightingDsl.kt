/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.dsl

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Environment
import com.awakekt.awake.scene.rendering.Light

/**
 * Attaches a Directional Light component to this entity.
 *
 * @param direction World-space direction vector the light shines toward.
 * @param color Light RGB color vector (`(1, 1, 1)` white by default).
 * @param intensity Light radiance/luminance multiplier.
 */
fun EntityScope.directionalLight(
    direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
) {
    with(
        Light(
            color = color,
            intensity = intensity,
            type = Light.Type.Directional,
            direction = direction,
        ),
    )
}

/**
 * Attaches a Point Light component to this entity.
 *
 * @param color Light RGB color vector.
 * @param intensity Light radiance/luminance multiplier.
 * @param range Maximum light falloff radius.
 */
fun EntityScope.pointLight(
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
    range: Float = 10f,
) {
    with(
        Light(
            color = color,
            intensity = intensity,
            type = Light.Type.Point,
            range = range,
        ),
    )
}

/**
 * Spawns a directional sun light entity in a [SceneBuilder].
 *
 * @param name Optional entity name ("sun" by default).
 * @param direction Direction vector the sunlight shines toward.
 * @param color Light RGB color vector.
 * @param intensity Light luminance/radiance multiplier.
 * @return The spawned sun [Entity].
 */
fun SceneBuilder.sun(
    name: String = "sun",
    direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
): Entity = entity(name) {
    directionalLight(direction, color, intensity)
}

/**
 * Spawns a directional sun light entity in a [SceneAppDsl].
 */
fun SceneAppDsl.sun(
    name: String = "sun",
    direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
) {
    entity(name) {
        directionalLight(direction, color, intensity)
    }
}

/**
 * Spawns a point light lamp entity in a [SceneBuilder].
 *
 * @param name Optional entity name ("lamp" by default).
 * @param position World-space position vector for the lamp entity.
 * @param color Light RGB color vector.
 * @param intensity Light luminance/radiance multiplier.
 * @param range Maximum light falloff radius.
 * @return The spawned lamp [Entity].
 */
fun SceneBuilder.lamp(
    name: String = "lamp",
    position: Vec3f = Vec3f(0f, 2f, 0f),
    color: Vec3f = Vec3f(1f, 0.9f, 0.7f),
    intensity: Float = 1f,
    range: Float = 10f,
): Entity = entity(name) {
    with(Transform(position = position))
    pointLight(color, intensity, range)
}

/**
 * Spawns a point light lamp entity in a [SceneAppDsl].
 */
fun SceneAppDsl.lamp(
    name: String = "lamp",
    position: Vec3f = Vec3f(0f, 2f, 0f),
    color: Vec3f = Vec3f(1f, 0.9f, 0.7f),
    intensity: Float = 1f,
    range: Float = 10f,
) {
    entity(name) {
        with(Transform(position = position))
        pointLight(color, intensity, range)
    }
}

/**
 * Attaches an [Environment] component to this entity.
 */
fun EntityScope.environment(
    environment: Environment = Environment(),
) {
    with(environment)
}

/**
 * Spawns an environment entity in a [SceneBuilder].
 *
 * @param name Optional entity name ("environment" by default).
 * @param environment Environmental parameters to assign.
 * @return The spawned environment [Entity].
 */
fun SceneBuilder.environmentEntity(
    name: String = "environment",
    environment: Environment = Environment(),
): Entity = entity(name) {
    environment(environment)
}

/**
 * Spawns an environment entity in a [SceneAppDsl].
 */
fun SceneAppDsl.environmentEntity(
    name: String = "environment",
    environment: Environment = Environment(),
) {
    entity(name) {
        environment(environment)
    }
}
