/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.dsl

import com.awakekt.awake.render.passes.DEFAULT_SHADOW_DISTANCE
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.light.AmbientLight
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.sky.Skybox

/**
 * Attaches a Directional Light component to this entity.
 *
 * @param direction World-space direction the light comes from, as on [Light.direction].
 * @param color Light RGB color vector (`(1, 1, 1)` white by default).
 * @param intensity Light radiance/luminance multiplier.
 * @param shadowsEnabled Whether this light casts shadows.
 * @param shadowDistance How far from the camera, in world units, shadows reach.
 */
fun EntityScope.directionalLight(
    direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
    shadowsEnabled: Boolean = true,
    shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
) {
    with(
        Light(
            color = color,
            intensity = intensity,
            type = Light.Type.Directional,
            direction = direction,
            shadowsEnabled = shadowsEnabled,
            shadowDistance = shadowDistance,
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
 * @param direction Direction the sunlight comes from.
 * @param color Light RGB color vector.
 * @param intensity Light luminance/radiance multiplier.
 * @param shadowsEnabled Whether the sun casts shadows.
 * @param shadowDistance How far from the camera, in world units, shadows reach.
 * @return The spawned sun [Entity].
 */
fun SceneBuilder.sun(
    name: String = "sun",
    direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
    shadowsEnabled: Boolean = true,
    shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
): Entity = entity(name) {
    directionalLight(direction, color, intensity, shadowsEnabled, shadowDistance)
}

/**
 * Spawns a directional sun light entity in a [SceneAppDsl].
 */
fun SceneAppDsl.sun(
    name: String = "sun",
    direction: Vec3f = Vec3f(0.4f, 0.8f, 0.4f),
    color: Vec3f = Vec3f(1f, 1f, 1f),
    intensity: Float = 1f,
    shadowsEnabled: Boolean = true,
    shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
) {
    entity(name) {
        directionalLight(direction, color, intensity, shadowsEnabled, shadowDistance)
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

/** Attaches a [Skybox] component to this entity. */
fun EntityScope.skybox(
    skybox: Skybox = Skybox(),
) {
    with(skybox)
}

/** Spawns a dedicated skybox entity in a [SceneBuilder]. */
fun SceneBuilder.skyboxEntity(
    name: String = "skybox",
    skybox: Skybox = Skybox(),
): Entity = entity(name) {
    skybox(skybox)
}

/** Spawns a dedicated skybox entity in a [SceneAppDsl]. */
fun SceneAppDsl.skyboxEntity(
    name: String = "skybox",
    skybox: Skybox = Skybox(),
) {
    entity(name) {
        skybox(skybox)
    }
}

/** Attaches a [Fog] component to this entity. */
fun EntityScope.fog(
    fog: Fog = Fog(),
) {
    with(fog)
}

/** Spawns a dedicated fog entity in a [SceneBuilder]. */
fun SceneBuilder.fogEntity(
    name: String = "fog",
    fog: Fog = Fog(),
): Entity = entity(name) {
    fog(fog)
}

/** Spawns a dedicated fog entity in a [SceneAppDsl]. */
fun SceneAppDsl.fogEntity(
    name: String = "fog",
    fog: Fog = Fog(),
) {
    entity(name) {
        fog(fog)
    }
}

/** Attaches an [AmbientLight] component to this entity. */
fun EntityScope.ambientLight(
    ambientLight: AmbientLight = AmbientLight(),
) {
    with(ambientLight)
}

/** Spawns a dedicated ambient light entity in a [SceneBuilder]. */
fun SceneBuilder.ambientLightEntity(
    name: String = "ambient_light",
    ambientLight: AmbientLight = AmbientLight(),
): Entity = entity(name) {
    ambientLight(ambientLight)
}

/** Spawns a dedicated ambient light entity in a [SceneAppDsl]. */
fun SceneAppDsl.ambientLightEntity(
    name: String = "ambient_light",
    ambientLight: AmbientLight = AmbientLight(),
) {
    entity(name) {
        ambientLight(ambientLight)
    }
}
