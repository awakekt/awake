/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.RenderDebugView

/**
 * Debug ids that turn on [WorldDebugSettings] toggles from the debug flag (`--debug=bounds,grid`, or
 * `?debug=bounds,grid` on the web), so a game can look at its bounds, lights or skeletons without
 * adding the component in code.
 *
 * | Id | Turns on |
 * | --- | --- |
 * | `bounds` | [WorldDebugSettings.showBounds] |
 * | `instance-bounds` | [WorldDebugSettings.showInstanceBounds] |
 * | `occluders` | [WorldDebugSettings.showOcclusion] |
 * | `lights` | [WorldDebugSettings.showLights] |
 * | `shadow-frustum` | [WorldDebugSettings.showShadowFrustum] |
 * | `grid` | [WorldDebugSettings.showGrid] |
 * | `axes` | [WorldDebugSettings.showAxisLines] |
 * | `skeleton` | [WorldDebugSettings.showSkeleton] |
 * | `wireframe` | [WorldDebugSettings.showWireframe] |
 * | `view-<name>` | [WorldDebugSettings.renderDebugView], a [RenderDebugView] in kebab case: `view-world-normals`, `view-linear-depth`, `view-shadow-visibility`, `view-albedo` and so on |
 *
 * The camera frustum has no id: it needs a target camera. The `culling` source draws the frozen
 * culling camera's frustum instead ([CullingDebugSource]).
 */
object DebugToggleIds {
    /** [WorldDebugSettings.showBounds]. */
    const val BOUNDS: String = "bounds"

    /** [WorldDebugSettings.showInstanceBounds]. */
    const val INSTANCE_BOUNDS: String = "instance-bounds"

    /** [WorldDebugSettings.showOcclusion]. */
    const val OCCLUDERS: String = "occluders"

    /** [WorldDebugSettings.showLights]. */
    const val LIGHTS: String = "lights"

    /** [WorldDebugSettings.showShadowFrustum]. */
    const val SHADOW_FRUSTUM: String = "shadow-frustum"

    /** [WorldDebugSettings.showGrid]. */
    const val GRID: String = "grid"

    /** [WorldDebugSettings.showAxisLines]. */
    const val AXES: String = "axes"

    /** [WorldDebugSettings.showSkeleton]. */
    const val SKELETON: String = "skeleton"

    /** [WorldDebugSettings.showWireframe]. */
    const val WIREFRAME: String = "wireframe"

    /** The prefix of a render view's id: `view-` and the [RenderDebugView] name in kebab case. */
    const val VIEW_PREFIX: String = "view-"

    /** The id that shows [view]: `view-world-normals` for [RenderDebugView.WorldNormals]. */
    fun viewId(view: RenderDebugView): String = VIEW_PREFIX + kebab(view.name)

    /** Whether [ids] holds any id this object knows. */
    fun anyIn(ids: Set<String>): Boolean = ids.any { it in TOGGLES || it in VIEWS }

    /**
     * Turns on in [settings] every toggle [ids] names, and sets its render view when one is named,
     * leaving everything else as it is. Returns [settings].
     */
    fun applyTo(settings: WorldDebugSettings, ids: Set<String>): WorldDebugSettings {
        if (BOUNDS in ids) settings.showBounds = true
        if (INSTANCE_BOUNDS in ids) settings.showInstanceBounds = true
        if (OCCLUDERS in ids) settings.showOcclusion = true
        if (LIGHTS in ids) settings.showLights = true
        if (SHADOW_FRUSTUM in ids) settings.showShadowFrustum = true
        if (GRID in ids) settings.showGrid = true
        if (AXES in ids) settings.showAxisLines = true
        if (SKELETON in ids) settings.showSkeleton = true
        if (WIREFRAME in ids) settings.showWireframe = true
        ids.firstNotNullOfOrNull { VIEWS[it] }?.let { settings.renderDebugView = it }
        return settings
    }

    /**
     * Applies [ids] to [world]'s [WorldDebugSettings], adding one on a new entity when the world has
     * none and an id asks for something. Returns the settings it changed, or `null` when no id is one
     * of these.
     */
    fun applyTo(world: World, ids: Set<String>): WorldDebugSettings? {
        if (!anyIn(ids)) return null
        val existing = world.family<WorldDebugSettings>().components().firstOrNull()
        return applyTo(existing ?: WorldDebugSettings().also { world.add(world.create(), it) }, ids)
    }

    private val TOGGLES = setOf(BOUNDS, INSTANCE_BOUNDS, OCCLUDERS, LIGHTS, SHADOW_FRUSTUM, GRID, AXES, SKELETON, WIREFRAME)

    private val VIEWS: Map<String, RenderDebugView> =
        RenderDebugView.entries.filter { it != RenderDebugView.Off }.associateBy(::viewId)

    private fun kebab(name: String): String =
        buildString {
            name.forEachIndexed { i, c ->
                if (c.isUpperCase() && i > 0) append('-')
                append(c.lowercaseChar())
            }
        }
}
