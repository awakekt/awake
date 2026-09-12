/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

// Domain ECS Components
typealias Camera = com.awakekt.awake.scene.rendering.camera.Camera
typealias Light = com.awakekt.awake.scene.rendering.light.Light
typealias AmbientLight = com.awakekt.awake.scene.rendering.light.AmbientLight
typealias Fog = com.awakekt.awake.scene.rendering.fog.Fog
typealias Skybox = com.awakekt.awake.scene.rendering.sky.Skybox

// Default Sky & Fog Colors
val DefaultHorizonColor get() = com.awakekt.awake.scene.rendering.sky.DefaultHorizonColor
val DefaultZenithColor get() = com.awakekt.awake.scene.rendering.sky.DefaultZenithColor
val DefaultFogColor get() = com.awakekt.awake.scene.rendering.fog.DefaultFogColor

// Scene Compilers & Collectors
internal typealias SceneLightingCompiler = com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
internal typealias SceneCullingCompiler = com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
internal typealias FrameCulling = com.awakekt.awake.scene.rendering.spatial.FrameCulling
internal typealias SceneDrawCollector = com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
internal typealias SceneParticleCompiler = com.awakekt.awake.scene.rendering.particles.SceneParticleCompiler
internal typealias SceneGeometryFeature3D = com.awakekt.awake.scene.rendering.mesh.SceneGeometryFeature3D
