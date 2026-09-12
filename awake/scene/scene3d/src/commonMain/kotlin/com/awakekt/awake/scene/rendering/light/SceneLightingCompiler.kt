/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
import com.awakekt.awake.ecs.singleOrNull
import com.awakekt.awake.render.passes.directionalShadowBox
import com.awakekt.awake.render.passes.pointShadowMatrices
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MAX_POINT_LIGHTS
import com.awakekt.awake.render.passes.uniforms.POINT_SHADOW_FACE_COUNT
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.PointShadowLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.debug.debugSettingsOrNull
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.sky.Skybox
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compiles authored lighting and environment state into backend-neutral render inputs.
 *
 * This is deliberately separate from [RenderSystem3D]: the ECS draw extractor owns visibility
 * and draw ordering, while this collaborator owns light selection, environment fallback values,
 * and directional shadow fitting. No backend or game-authored type crosses this seam.
 */
internal class SceneLightingCompiler(
    private val clipSpace: ClipSpace,
) {
    private val pointLights = ArrayList<PointLight>()
    private val pointShadows = ArrayList<PointShadowLight>()
    private val scratchDirectionalDir = com.awakekt.awake.core.math.Vec3()

    /**
     * The scene's lighting: the first [Light.Type.Directional] entity plus every
     * [Light.Type.Point] one, converted to the backend-neutral [SceneLight].
     *
     * [DEFAULT_SCENE_LIGHT] when no directional light exists -- the direction and colour every
     * lit shader hardcoded before this component did, so an un-lit scene renders as it always
     * did. Point lights still apply on top of that default; a scene can be lit entirely by them.
     *
     * A point light's position comes from its entity's `Transform`, so a point light without one
     * is skipped rather than silently placed at the origin.
     */
    fun sceneLight(world: World, camera: Camera): SceneLight {
        var directional: Light? = null
        var directionalHasTransformRotation = false
        // Reused, not rebuilt: this runs every frame, and `skills/awake-core-math` rules out
        // allocating inside System.update.
        //
        // The list is reused; the PointLight instances still are not -- one per point light per
        // frame. Pooling them would mean making PointLight mutable, and it is a public value type
        // the render contract hands to backends. A handful of small objects is the accepted cost;
        // the ArrayList, two mapped Lists and 2N Pairs this replaces were not.
        pointLights.clear()
        pointShadows.clear()
        val pointShadowsEnabled = world.debugSettingsOrNull()?.shadowsEnabledOverride != false
        world.family<Light>().forEach { entity, light ->
            when (light.type) {
                Light.Type.Directional -> if (directional == null) {
                    directional = light
                    val transform = world.get<Transform>(entity)
                    if (transform != null && (transform.rotation.x != 0f || transform.rotation.y != 0f || transform.rotation.z != 0f)) {
                        val rot = transform.rotation
                        val cp = cos(rot.x)
                        val fwdX = sin(rot.y) * cp
                        val fwdY = sin(rot.x)
                        val fwdZ = -cos(rot.y) * cp
                        // Light direction is the direction light shines FROM (-forward)
                        scratchDirectionalDir.set(-fwdX, -fwdY, -fwdZ)
                        directionalHasTransformRotation = true
                    }
                }

                Light.Type.Point -> {
                    val transform = world.get<Transform>(entity) ?: return@forEach
                    val shadowBaseLayer = if (
                        pointShadowsEnabled &&
                        pointShadows.size < MAX_POINT_LIGHTS &&
                        light.shadowsEnabled &&
                        light.range > POINT_SHADOW_NEAR
                    ) {
                        MAX_SHADOW_CASCADES + pointShadows.size * POINT_SHADOW_FACE_COUNT
                    } else {
                        -1
                    }
                    pointLights += PointLight(
                        position = transform.position,
                        color = light.color * light.intensity,
                        range = light.range,
                        shadowBaseLayer = shadowBaseLayer,
                    )
                    if (shadowBaseLayer >= 0) {
                        pointShadows += PointShadowLight(
                            baseLayer = shadowBaseLayer,
                            viewProjections = pointShadowMatrices(
                                position = transform.position,
                                range = light.range,
                                clipSpace = clipSpace,
                                nearPlane = POINT_SHADOW_NEAR,
                            ).viewProjections,
                        )
                    }
                }
            }
        }
        val sun = directional
        val base = if (sun == null) {
            // A point-light-only scene must not inherit the fallback sun: that fallback also
            // carries the directional shadow path, which otherwise blankets the floor with a
            // shadow unrelated to any authored light.
            if (pointLights.isNotEmpty()) {
                DEFAULT_SCENE_LIGHT.copy(
                    color = Vec3f.ZERO,
                    points = pointLights,
                    pointShadows = pointShadows,
                )
            } else {
                DEFAULT_SCENE_LIGHT.copy(points = pointLights, pointShadows = pointShadows)
            }
        } else {
            val dir = if (directionalHasTransformRotation) scratchDirectionalDir else sun.direction
            SceneLight(
                direction = dir,
                color = sun.color * sun.intensity,
                points = pointLights,
                pointShadows = pointShadows,
            )
        }
        // More importantly, do not route a point-only scene through the directional shadow fit:
        // that would manufacture a cascade for the fallback sun and make the entire floor sample
        // it as shadowed even though the scene has no directional caster.
        if (sun == null && pointLights.isNotEmpty()) return base
        // The volume a directional light covers is a content decision, so it is made here rather
        // than inside a backend -- see docs/reference/render-extensibility.md. A backend renders
        // depth from whatever matrix it is handed and never builds one.
        //
        // Allocates per frame, but only while shadows are on, and the caller that used to build
        // one fixed box allocated the same kind of thing.
        if (sun?.shadowsEnabled != true || world.debugSettingsOrNull()?.shadowsEnabledOverride == false) return base
        return shadowedLight(world, camera, base)
    }

    /** [base] plus whichever shadow fit is in force -- cascades, or the single box. */
    fun shadowedLight(world: World, camera: Camera, base: SceneLight): SceneLight {
        // Fitted to THIS camera's frustum, in slices. The fixed box `directionalShadowBox` still
        // builds covers a volume at the origin, which is right for a demo scene sitting there and
        // wrong for anything that walks away from it -- the shadows simply stop.
        // The REAL viewport aspect, not CONSERVATIVE_ASPECT. That constant is deliberately wider
        // than any real viewport so this system's own cull test never drops something visible --
        // harmless there, expensive here: a frustum three times too wide gives cascade boxes
        // roughly 1.4x too large in every direction, and a cascade's texel size IS its box size
        // over the map's fixed 2048. That was half of why shadow edges looked like stairs.
        val aspect = DEFAULT_SHADOW_FIT_ASPECT
        // The single fixed box, when a viewer asks for it: `SceneLight.shadowCascades()` turns a
        // lone viewProjection into a one-cascade set, so a backend still has exactly one path.
        val boxOnly = world.debugSettingsOrNull()?.cascadedShadows == false
        val cascades = if (boxOnly) {
            null
        } else {
            shadowCascadeUniforms(base, camera.lens, aspect, clipSpace)
        }
        if (cascades == null) {
            return base.copy(
                viewProjection = directionalShadowBox(
                    base.direction,
                    clipSpace,
                ).viewProjection,
            )
        }
        // viewProjection stays the near cascade: the debug visualiser draws its wireframe, and a
        // backend that reads it alone still gets the cascade covering what is nearest.
        return base.copy(viewProjection = cascades.viewProjections.first(), cascades = cascades)
    }

    fun environmentUniforms(world: World): EnvironmentUniforms {
        val skybox = world.singleOrNull<Skybox>() ?: world.firstOrNull<Skybox>()
        val fog = world.singleOrNull<Fog>() ?: world.firstOrNull<Fog>()

        val debug = world.debugSettingsOrNull()

        val showSky = skybox?.enabled ?: EnvironmentUniforms.Default.showSky
        val horizon = skybox?.horizonColor ?: EnvironmentUniforms.Default.horizonColor
        val zenith = skybox?.zenithColor ?: EnvironmentUniforms.Default.zenithColor

        val fogDensity = debug?.fogDensityOverride ?: if (fog != null) {
            if (fog.enabled) fog.density else 0f
        } else {
            EnvironmentUniforms.Default.fogDensity
        }
        val fogColor = debug?.fogColorOverride ?: fog?.color ?: EnvironmentUniforms.Default.fogColor

        var castsShadows = false
        world.family<Light>().forEachComponent { light ->
            if (light.shadowsEnabled) castsShadows = true
        }
        val shadows = debug?.shadowsEnabledOverride
            ?: castsShadows

        return EnvironmentUniforms(
            showSky = showSky,
            horizonColor = horizon,
            zenithColor = zenith,
            fogDensity = fogDensity,
            fogColor = fogColor,
            shadowsEnabled = shadows,
        )
    }
}

private const val DEFAULT_SHADOW_FIT_ASPECT = 16f / 9f
private const val POINT_SHADOW_NEAR = 0.05f
